package net.neuralcheck.service.stress.v2

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLES32
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * V2 master plan §3.5 — GPU stress generator.
 *
 * Three workloads, all on a 2048×2048 offscreen FBO (fixed regardless
 * of physical screen — verification depends on deterministic input
 * size):
 *
 *   1. Fragment-shader stress: 8× texture samples + 30× sin/cos ALU
 *      chain per pixel. Heavier than V1's trivial sin/cos shader,
 *      hits TMUs + ALU lanes simultaneously. glFenceSync every 30
 *      frames instead of glFinish per frame so the GPU can pipeline
 *      its real throughput rather than serialising one frame at a
 *      time.
 *
 *   2. Compute shader GEMM 1024×1024 FP32 (one-shot verification):
 *      run on the GPU, compare trace(C) against a CPU-computed
 *      scalar reference within 1e-4 ratio (Q2.4 — wider tolerance
 *      than CPU NEON because mad/fma fusion is implementation-defined
 *      across GPU drivers). Catches dead compute lanes / bad shader
 *      cores.
 *
 *   3. VRAM bandwidth ping-pong: render a 4096×4096 RGBA32F texture
 *      back and forth between two FBOs. Bytes transferred per frame
 *      = 64 MiB write + 64 MiB read; running this for ~5 seconds
 *      multiplies up to a useful bandwidth measurement.
 *
 * V2 is Kotlin-only — keeps OpenGL ES 3.1 / compute shaders in their
 * idiomatic Java home, matches V1's GpuLoadGenerator pattern. NDK
 * doesn't help here because EGL + driver work is dominated by the
 * GPU, not CPU.
 */
/**
 * Phase 1.6.C — [useRaymarchShader] picks between the legacy
 * 8-texture / 30-ALU shader (false, the V5 default) and the heavy
 * raymarching scene defined in [RaymarchingGpuLoad] (true). Swap is
 * purely at fragment-program-build time; everything else (FBO size,
 * fence cadence, frame counter, EGL pipeline) is identical, so
 * gpu_fps still tracks faithfully.
 */
class GpuLoadGeneratorV2(
    private val useRaymarchShader: Boolean = false,
    /**
     * Phase 1.8 Task 4 — calibratable FBO side length in pixels.
     *
     * Default equals the legacy [FBO_DIM] constant (2048), so existing
     * call sites that don't supply this argument keep producing the
     * same 2048×2048 offscreen framebuffer they did before. Per
     * V5 §3.5 item 1 the production target is 2048; the calibration
     * path (Task 5) will narrow this on devices whose probe came back
     * below the GPU floor and widen it on devices whose probe came
     * back above the ceiling, both clamped to [512, 4096] inside
     * [net.neuralcheck.calibration.WorkloadCalibration].
     *
     * Used as the viewport + FBO color-attachment dimension, AND
     * passed to the raymarching shader's `uResolution` uniform so
     * the per-pixel cost scales correctly with the buffer size.
     */
    private val fboPixels: Int = FBO_DIM
) {

    companion object {
        private const val TAG = "NeuralCheck.V2.GPU"
        // Phase 1.8 Task 4: legacy FBO side, kept as the constructor
        // default. NEVER reference this directly inside the class
        // body — read [fboPixels] (the per-instance value) so a
        // calibrated instance gets its calibrated buffer.
        private const val FBO_DIM = 2048              // §3.5 item 1
        private const val FENCE_EVERY_N_FRAMES = 30   // §3.5 item 2
        private const val GEMM_DIM = 1024             // §3.5 item 4
        // Mali-G76 (Snapdragon 855 / Exynos 9810) caps total
        // workgroup invocations at 128, so 16×16=256 fails program
        // link with "Max number of total work group invocations
        // exceeded". 8×8=64 is universally supported (every ES 3.1
        // device guarantees ≥128) and trades fewer threads per
        // workgroup for more workgroups dispatched — same total
        // arithmetic throughput, slightly higher dispatch overhead.
        private const val GEMM_LOCAL_SIZE = 8
        private const val GEMM_TOLERANCE_RATIO = 1e-4f
        private const val BANDWIDTH_TEXTURE_DIM = 4096
    }

    private val isRunning = AtomicBoolean(false)
    private val frameCount = AtomicInteger(0)
    /**
     * fieldsVersion 66 diagnostic: number of 30-frame fence waits that
     * returned GL_TIMEOUT_EXPIRED / GL_WAIT_FAILED instead of completing.
     * [frameCount] counts ISSUED draws; when a fence times out (1 s cap)
     * the loop keeps issuing while the GPU is still behind, so the counter
     * — and therefore v[10] — runs ahead of real completion. >0 means
     * v[10] in that phase may overstate throughput. Read via
     * [getAndResetFenceTimeoutCount] at phase end; zero cost (inspects
     * the return value of a wait that already happens).
     */
    private val fenceTimeoutCount = AtomicInteger(0)
    private var renderThread: Thread? = null
    @Volatile private var rendererName: String = "unknown"
    @Volatile private var glVersion: String = "unknown"

    fun start() {
        if (isRunning.getAndSet(true)) return
        frameCount.set(0)
        fenceTimeoutCount.set(0)
        renderThread = Thread({ runFragmentLoop() }, "NeuralCheck-V2-GPU").apply {
            priority = Thread.NORM_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            renderThread?.join(3_000)
        } catch (_: InterruptedException) {
            renderThread?.interrupt()
        }
        renderThread = null
    }

    fun isActive(): Boolean = isRunning.get()
    fun getAndResetFrameCount(): Int = frameCount.getAndSet(0)
    /** fieldsVersion 66 — see [fenceTimeoutCount]. */
    fun getAndResetFenceTimeoutCount(): Int = fenceTimeoutCount.getAndSet(0)
    fun getRendererName(): String = rendererName
    fun getGlVersion(): String = glVersion

    // ─────────── Fragment shader stress loop ───────────

    private fun runFragmentLoop() {
        val ctx = createEglContext() ?: return
        try {
            rendererName = GLES31.glGetString(GLES31.GL_RENDERER) ?: "unknown"
            glVersion = GLES31.glGetString(GLES31.GL_VERSION) ?: "unknown"
            Log.i(TAG, "fragment loop on $rendererName ($glVersion), FBO=${fboPixels}x$fboPixels")

            val program = buildFragmentProgram() ?: return
            val (fbo, fboColor) = createColorFbo(fboPixels, fboPixels) ?: return
            val tex1 = createNoiseTexture(512, 0xC0FFEE01u)
            val tex2 = createNoiseTexture(512, 0xCAFEBABEu)
            val quad = createQuadVbo()

            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)
            GLES31.glViewport(0, 0, fboPixels, fboPixels)
            GLES31.glDisable(GLES31.GL_DEPTH_TEST)
            GLES31.glUseProgram(program)

            val posHandle = GLES31.glGetAttribLocation(program, "aPosition")
            val timeHandle = GLES31.glGetUniformLocation(program, "uTime")
            val tex1Handle = GLES31.glGetUniformLocation(program, "uTexture1")
            val tex2Handle = GLES31.glGetUniformLocation(program, "uTexture2")
            // Phase 1.6.C: raymarching shader has its own uResolution
            // uniform; legacy shader doesn't. glGetUniformLocation
            // returns -1 for an unknown name and glUniform2f silently
            // skips, so this is safe in both modes.
            val resHandle = GLES31.glGetUniformLocation(program, "uResolution")

            GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex1)
            GLES31.glUniform1i(tex1Handle, 0)
            GLES31.glActiveTexture(GLES31.GL_TEXTURE1)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex2)
            GLES31.glUniform1i(tex2Handle, 1)
            if (resHandle >= 0) {
                GLES31.glUniform2f(resHandle, fboPixels.toFloat(), fboPixels.toFloat())
            }

            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, quad)
            GLES31.glEnableVertexAttribArray(posHandle)
            GLES31.glVertexAttribPointer(posHandle, 3, GLES31.GL_FLOAT, false, 12, 0)

            val startNano = System.nanoTime()
            var localFrames = 0
            while (isRunning.get()) {
                val time = (System.nanoTime() - startNano) / 1_000_000_000f
                GLES31.glUniform1f(timeHandle, time)
                GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
                GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                ++localFrames
                // Bug 3 fix: increment counter on EVERY frame so the
                // sampler sees a non-zero count at any sampling rate.
                // The fence still runs every 30 frames to drain the GPU
                // command queue (without it the driver pipelines forever
                // and FPS measurements drift), but counter accuracy no
                // longer depends on the fence cadence aligning with the
                // sampling cadence.
                frameCount.incrementAndGet()
                if (localFrames % FENCE_EVERY_N_FRAMES == 0) {
                    val fence = GLES31.glFenceSync(GLES31.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
                    val waitResult = GLES31.glClientWaitSync(
                        fence, GLES31.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L
                    )
                    // fieldsVersion 66: same wait as before — only its
                    // return value is now inspected. A timeout means the
                    // issued-frame counter has run ahead of GPU completion.
                    if (waitResult == GLES30.GL_TIMEOUT_EXPIRED ||
                        waitResult == GLES30.GL_WAIT_FAILED
                    ) {
                        fenceTimeoutCount.incrementAndGet()
                    }
                    GLES31.glDeleteSync(fence)
                }
            }
            // final fence — counter already up to date, no residual add needed
            val fence = GLES31.glFenceSync(GLES31.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            GLES31.glClientWaitSync(fence, GLES31.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L)
            GLES31.glDeleteSync(fence)

            // Cleanup attached resources
            val tmp = IntArray(2)
            tmp[0] = tex1; tmp[1] = tex2
            GLES31.glDeleteTextures(2, tmp, 0)
            GLES31.glDeleteBuffers(1, intArrayOf(quad), 0)
            GLES31.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES31.glDeleteTextures(1, intArrayOf(fboColor), 0)
            GLES31.glDeleteProgram(program)
        } catch (t: Throwable) {
            Log.w(TAG, "fragment loop error: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            destroyEglContext(ctx)
        }
    }

    // ─────────── Compute shader GEMM (one-shot verification) ───────────

    /**
     * Runs a 1024×1024 FP32 GEMM on the GPU via a compute shader and
     * verifies trace(C) against a CPU-computed scalar reference within
     * Q2.4 tolerance. Returns true iff the verification passes.
     *
     * Allocates ~12 MiB on the GPU (3 × 4 MiB SSBOs); ~4 MiB on the
     * CPU for the reference matrices. Run as a one-shot — the
     * fragment-loop start/stop is unaffected.
     */
    fun runComputeGemmTest(): Boolean {
        val ctx = createEglContext() ?: return false
        try {
            // CPU reference matrices + computed trace
            val A = FloatArray(GEMM_DIM * GEMM_DIM)
            val B = FloatArray(GEMM_DIM * GEMM_DIM)
            generateMatrix(A, 0xDEADBEEFu)
            generateMatrix(B, 0xFEEDF00Du)
            val refTrace = computeReferenceTrace(A, B)

            val program = buildComputeProgram() ?: return false

            // Allocate SSBOs
            val ssboA = createSsbo(A.size * 4)
            val ssboB = createSsbo(B.size * 4)
            val ssboC = createSsbo(GEMM_DIM * GEMM_DIM * 4)

            // Upload A and B
            uploadFloats(ssboA, A)
            uploadFloats(ssboB, B)

            GLES31.glUseProgram(program)
            val nLoc = GLES31.glGetUniformLocation(program, "N")
            GLES31.glUniform1i(nLoc, GEMM_DIM)

            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, ssboA)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, ssboB)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, ssboC)

            val groups = GEMM_DIM / GEMM_LOCAL_SIZE
            GLES31.glDispatchCompute(groups, groups, 1)
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)

            // Read back C
            val C = FloatArray(GEMM_DIM * GEMM_DIM)
            downloadFloats(ssboC, C)

            // Cleanup
            GLES31.glDeleteBuffers(3, intArrayOf(ssboA, ssboB, ssboC), 0)
            GLES31.glDeleteProgram(program)

            // Compute GPU trace and compare
            var gpuTrace = 0.0
            for (i in 0 until GEMM_DIM) {
                gpuTrace += C[i * GEMM_DIM + i].toDouble()
            }
            val absDiff = abs(gpuTrace - refTrace)
            val absRef = abs(refTrace)
            val threshold = absRef * GEMM_TOLERANCE_RATIO
            val ok = absDiff <= threshold

            Log.i(
                TAG,
                "computeGemmTest: refTrace=${"%.4f".format(refTrace)} " +
                    "gpuTrace=${"%.4f".format(gpuTrace)} diff=${"%.6e".format(absDiff)} " +
                    "threshold=${"%.6e".format(threshold)} ok=$ok"
            )
            return ok
        } catch (t: Throwable) {
            Log.w(TAG, "computeGemmTest error: ${t.javaClass.simpleName}: ${t.message}")
            return false
        } finally {
            destroyEglContext(ctx)
        }
    }

    // ─────────── VRAM bandwidth ping-pong ───────────

    /**
     * Ping-pong copy between two 4096×4096 RGBA32F textures (= 64 MiB
     * each). Returns approximate bytes/sec moved. A ~5 s run on
     * Adreno 750 sustains ~80 GB/s; older / throttled GPUs report
     * much lower.
     */
    fun runBandwidthTest(durationMs: Long = 5_000L): Long {
        val ctx = createEglContext() ?: return 0L
        try {
            val program = buildFragmentProgram() ?: return 0L
            val tex1 = createColorTexture(BANDWIDTH_TEXTURE_DIM, BANDWIDTH_TEXTURE_DIM)
            val tex2 = createColorTexture(BANDWIDTH_TEXTURE_DIM, BANDWIDTH_TEXTURE_DIM)
            val (fbo1, _) = createColorFboFromTexture(tex1, BANDWIDTH_TEXTURE_DIM, BANDWIDTH_TEXTURE_DIM)
                ?: return 0L
            val (fbo2, _) = createColorFboFromTexture(tex2, BANDWIDTH_TEXTURE_DIM, BANDWIDTH_TEXTURE_DIM)
                ?: return 0L
            val quad = createQuadVbo()

            GLES31.glViewport(0, 0, BANDWIDTH_TEXTURE_DIM, BANDWIDTH_TEXTURE_DIM)
            GLES31.glDisable(GLES31.GL_DEPTH_TEST)
            GLES31.glUseProgram(program)
            val posHandle = GLES31.glGetAttribLocation(program, "aPosition")
            val timeHandle = GLES31.glGetUniformLocation(program, "uTime")
            val tex1Handle = GLES31.glGetUniformLocation(program, "uTexture1")
            val tex2Handle = GLES31.glGetUniformLocation(program, "uTexture2")
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, quad)
            GLES31.glEnableVertexAttribArray(posHandle)
            GLES31.glVertexAttribPointer(posHandle, 3, GLES31.GL_FLOAT, false, 12, 0)

            val deadline = System.currentTimeMillis() + durationMs
            var frames = 0L
            var pingFromTwo = false
            val tStart = System.nanoTime()
            while (System.currentTimeMillis() < deadline) {
                val srcTex = if (pingFromTwo) tex1 else tex2
                val dstFbo = if (pingFromTwo) fbo1 else fbo2
                pingFromTwo = !pingFromTwo

                GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, dstFbo)
                GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, srcTex)
                GLES31.glUniform1i(tex1Handle, 0)
                GLES31.glActiveTexture(GLES31.GL_TEXTURE1)
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, srcTex)
                GLES31.glUniform1i(tex2Handle, 1)
                GLES31.glUniform1f(timeHandle, frames * 0.001f)

                GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                ++frames
                if (frames % FENCE_EVERY_N_FRAMES == 0L) {
                    val fence = GLES31.glFenceSync(GLES31.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
                    GLES31.glClientWaitSync(fence, GLES31.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L)
                    GLES31.glDeleteSync(fence)
                }
            }
            val finalFence = GLES31.glFenceSync(GLES31.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            GLES31.glClientWaitSync(finalFence, GLES31.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L)
            GLES31.glDeleteSync(finalFence)
            val elapsedSec = (System.nanoTime() - tStart) / 1_000_000_000.0

            // Each frame reads 64 MiB (texture bind + sample) and writes
            // 64 MiB (FBO output) = 128 MiB transferred.
            val bytesPerFrame = (BANDWIDTH_TEXTURE_DIM * BANDWIDTH_TEXTURE_DIM * 4 * 2).toLong()
            val totalBytes = frames * bytesPerFrame
            val mbps = if (elapsedSec > 0.0) (totalBytes / 1_000_000.0 / elapsedSec).toLong() else 0L

            // Cleanup
            GLES31.glDeleteBuffers(1, intArrayOf(quad), 0)
            GLES31.glDeleteFramebuffers(2, intArrayOf(fbo1, fbo2), 0)
            GLES31.glDeleteTextures(2, intArrayOf(tex1, tex2), 0)
            GLES31.glDeleteProgram(program)

            Log.i(TAG, "bandwidthTest: frames=$frames duration=${"%.2f".format(elapsedSec)}s ~${mbps} MB/s")
            return mbps
        } catch (t: Throwable) {
            Log.w(TAG, "bandwidthTest error: ${t.javaClass.simpleName}: ${t.message}")
            return 0L
        } finally {
            destroyEglContext(ctx)
        }
    }

    /**
     * Benchmark single-frame render completion time. Bug fix #20:
     * counter-based FPS on offscreen FBOs is unreliable due to OpenGL
     * ES pipelining (Khronos OpenGL Wiki: "logical specification and
     * actual processing are decoupled by a buffering process which
     * can be tens of milliseconds in length"). This method renders
     * ONE frame and waits for the GPU to finish via glFinish() —
     * producing a deterministic timing measurement immune to
     * pipelining artefacts.
     *
     * Returns elapsed milliseconds from the start of the render call
     * sequence to the return of glFinish(). Returns -1L on failure.
     *
     * Self-contained: creates its own EGL context, builds the same
     * fragment program / FBO / textures / VBO as the stress loop,
     * renders one frame, measures, and tears everything down.
     * Pattern matches runComputeGemmTest() and runBandwidthTest()
     * — safe to call from any thread (e.g. Dispatchers.Default).
     *
     * Cost: ~50-500ms depending on GPU, dominated by EGL context
     * creation + program build (one-time setup) + the frame itself.
     * Should be called outside the stress loop — i.e. before start()
     * or after stop() — not concurrently with the running render
     * thread (creating two EGL contexts simultaneously is supported,
     * but would steal GPU cycles from the stress loop).
     */
    fun benchmarkSingleFrameMs(): Long {
        val ctx = createEglContext() ?: run {
            Log.w(TAG, "benchmarkSingleFrameMs: failed to create EGL context")
            return -1L
        }
        try {
            val program = buildFragmentProgram() ?: run {
                Log.w(TAG, "benchmarkSingleFrameMs: failed to build program")
                return -1L
            }
            val fboPair = createColorFbo(fboPixels, fboPixels) ?: run {
                Log.w(TAG, "benchmarkSingleFrameMs: failed to create FBO")
                GLES31.glDeleteProgram(program)
                return -1L
            }
            val (fbo, fboColor) = fboPair
            val tex1 = createNoiseTexture(512, 0xC0FFEE01u)
            val tex2 = createNoiseTexture(512, 0xCAFEBABEu)
            val quad = createQuadVbo()

            // Same GL state setup as runFragmentLoop(). Critical:
            // any divergence here would produce timing that doesn't
            // reflect actual stress-frame cost.
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)
            GLES31.glViewport(0, 0, fboPixels, fboPixels)
            GLES31.glDisable(GLES31.GL_DEPTH_TEST)
            GLES31.glUseProgram(program)

            val posHandle = GLES31.glGetAttribLocation(program, "aPosition")
            val timeHandle = GLES31.glGetUniformLocation(program, "uTime")
            val tex1Handle = GLES31.glGetUniformLocation(program, "uTexture1")
            val tex2Handle = GLES31.glGetUniformLocation(program, "uTexture2")

            GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex1)
            GLES31.glUniform1i(tex1Handle, 0)
            GLES31.glActiveTexture(GLES31.GL_TEXTURE1)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex2)
            GLES31.glUniform1i(tex2Handle, 1)

            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, quad)
            GLES31.glEnableVertexAttribArray(posHandle)
            GLES31.glVertexAttribPointer(posHandle, 3, GLES31.GL_FLOAT, false, 12, 0)

            // Warm-up frame: ensure shaders are compiled/cached and
            // textures are uploaded to GPU memory before timing starts.
            // Without this, the measured time would include a
            // first-time compilation overhead that's not part of
            // ongoing render cost. This frame is deliberately not
            // measured.
            GLES31.glUniform1f(timeHandle, 0.0f)
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
            GLES31.glFinish()  // drain any first-time setup cost

            // Measured frame: identical GL calls to one stress loop
            // iteration, then glFinish() to drain the pipeline. The
            // returned time is the end-to-end cost of one frame as
            // the GPU actually executes it — not the cost of issuing
            // commands (which the pipelining hides).
            val tStartNs = System.nanoTime()
            GLES31.glUniform1f(timeHandle, 1.0f)
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
            GLES31.glFinish()
            val elapsedNs = System.nanoTime() - tStartNs

            // Cleanup. Same teardown order as runFragmentLoop's
            // cleanup block. Texture+buffer+program deletion is
            // safe even if some failed earlier (delete on 0 is no-op).
            val texArr = IntArray(2)
            texArr[0] = tex1; texArr[1] = tex2
            GLES31.glDeleteTextures(2, texArr, 0)
            GLES31.glDeleteBuffers(1, intArrayOf(quad), 0)
            GLES31.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES31.glDeleteTextures(1, intArrayOf(fboColor), 0)
            GLES31.glDeleteProgram(program)

            val elapsedMs = elapsedNs / 1_000_000L
            Log.i(TAG, "benchmarkSingleFrameMs: ${elapsedMs}ms on $rendererName")
            return elapsedMs
        } catch (t: Throwable) {
            Log.w(TAG, "benchmarkSingleFrameMs failed: " +
                "${t.javaClass.simpleName}: ${t.message}")
            return -1L
        } finally {
            destroyEglContext(ctx)
        }
    }

    // ─────────── EGL setup ───────────

    private data class EglContext(
        val display: EGLDisplay,
        val surface: EGLSurface,
        val context: EGLContext
    )

    private fun createEglContext(): EglContext? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return null

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x40,  // EGL_OPENGL_ES3_BIT_KHR
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val nCfg = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, nCfg, 0) || nCfg[0] <= 0) return null
        val cfg = cfgs[0] ?: return null

        // ES 3.1 minimum (compute shaders); 3.2 if available.
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) return null

        val surfAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 16,
            EGL14.EGL_HEIGHT, 16,
            EGL14.EGL_NONE
        )
        val surface = EGL14.eglCreatePbufferSurface(display, cfg, surfAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) return null
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null

        return EglContext(display, surface, context)
    }

    private fun destroyEglContext(c: EglContext) {
        try {
            EGL14.eglMakeCurrent(c.display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(c.display, c.surface)
            EGL14.eglDestroyContext(c.display, c.context)
            EGL14.eglTerminate(c.display)
        } catch (_: Throwable) { /* best effort */ }
    }

    // ─────────── Resource builders ───────────

    private fun buildFragmentProgram(): Int? {
        val vs = """
            #version 310 es
            in vec4 aPosition;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aPosition.xy * 0.5 + 0.5;
            }
        """.trimIndent()
        // Phase 1.6.C: raymarching shader (heavy ALU, near-zero TMU)
        // when the V2 service constructs us with useRaymarchShader=true.
        // Legacy V5 shader otherwise: 8 texture samples + 30 ALU ops
        // per pixel. The latter stays the safe rollback path while we
        // collect device data on the raymarching variant.
        val fs = if (useRaymarchShader) {
            RaymarchingGpuLoad.FRAGMENT_SHADER
        } else """
            #version 310 es
            precision highp float;
            uniform sampler2D uTexture1;
            uniform sampler2D uTexture2;
            uniform float uTime;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                vec2 coord = vTexCoord;
                vec4 acc = vec4(0.0);
                for (int i = 0; i < 8; i++) {
                    float fi = float(i) * 0.125;
                    vec2 offset = vec2(sin(uTime + fi), cos(uTime + fi)) * 0.01;
                    acc += texture(uTexture1, coord + offset);
                    acc += texture(uTexture2, coord - offset);
                }
                float x = acc.r;
                for (int i = 0; i < 30; i++) {
                    x = sin(x * 1.7) + cos(x * 2.3);
                }
                fragColor = vec4(x, acc.g, acc.b, 1.0);
            }
        """.trimIndent()
        return linkProgram(vs, fs)
    }

    private fun buildComputeProgram(): Int? {
        // §3.5 item 4 — GEMM 1024×1024 FP32, 8×8 workgroup (Mali-G76
        // and other ES 3.1 minimums cap workgroup invocations at 128).
        val cs = """
            #version 310 es
            layout(local_size_x = 8, local_size_y = 8) in;
            layout(std430, binding = 0) readonly buffer MatA { float a[]; };
            layout(std430, binding = 1) readonly buffer MatB { float b[]; };
            layout(std430, binding = 2) writeonly buffer MatC { float c[]; };
            uniform int N;
            void main() {
                uint row = gl_GlobalInvocationID.y;
                uint col = gl_GlobalInvocationID.x;
                if (row >= uint(N) || col >= uint(N)) return;
                float sum = 0.0;
                for (int k = 0; k < N; k++) {
                    sum += a[row * uint(N) + uint(k)] * b[uint(k) * uint(N) + col];
                }
                c[row * uint(N) + col] = sum;
            }
        """.trimIndent()
        return linkComputeProgram(cs)
    }

    private fun linkProgram(vsSrc: String, fsSrc: String): Int? {
        val vs = compileShader(GLES31.GL_VERTEX_SHADER, vsSrc) ?: return null
        val fs = compileShader(GLES31.GL_FRAGMENT_SHADER, fsSrc) ?: return null
        val program = GLES31.glCreateProgram().also {
            GLES31.glAttachShader(it, vs)
            GLES31.glAttachShader(it, fs)
            GLES31.glLinkProgram(it)
        }
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "program link failed: ${GLES31.glGetProgramInfoLog(program)}")
            GLES31.glDeleteProgram(program)
            return null
        }
        GLES31.glDeleteShader(vs)
        GLES31.glDeleteShader(fs)
        return program
    }

    private fun linkComputeProgram(csSrc: String): Int? {
        val cs = compileShader(GLES31.GL_COMPUTE_SHADER, csSrc) ?: return null
        val program = GLES31.glCreateProgram().also {
            GLES31.glAttachShader(it, cs)
            GLES31.glLinkProgram(it)
        }
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "compute program link failed: ${GLES31.glGetProgramInfoLog(program)}")
            GLES31.glDeleteProgram(program)
            return null
        }
        GLES31.glDeleteShader(cs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int? {
        val shader = GLES31.glCreateShader(type)
        if (shader == 0) return null
        GLES31.glShaderSource(shader, src)
        GLES31.glCompileShader(shader)
        val status = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "shader compile failed (type=$type): ${GLES31.glGetShaderInfoLog(shader)}")
            GLES31.glDeleteShader(shader)
            return null
        }
        return shader
    }

    private fun createColorFbo(w: Int, h: Int): Pair<Int, Int>? {
        val fbo = IntArray(1).also { GLES31.glGenFramebuffers(1, it, 0) }[0]
        val tex = createColorTexture(w, h)
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)
        GLES31.glFramebufferTexture2D(
            GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
            GLES31.GL_TEXTURE_2D, tex, 0
        )
        if (GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER) != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            Log.w(TAG, "createColorFbo: incomplete")
            return null
        }
        return fbo to tex
    }

    private fun createColorFboFromTexture(tex: Int, w: Int, h: Int): Pair<Int, Int>? {
        val fbo = IntArray(1).also { GLES31.glGenFramebuffers(1, it, 0) }[0]
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)
        GLES31.glFramebufferTexture2D(
            GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
            GLES31.GL_TEXTURE_2D, tex, 0
        )
        if (GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER) != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            return null
        }
        return fbo to tex
    }

    private fun createColorTexture(w: Int, h: Int): Int {
        val tex = IntArray(1).also { GLES31.glGenTextures(1, it, 0) }[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex)
        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA8,
            w, h, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null
        )
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        return tex
    }

    private fun createNoiseTexture(dim: Int, seed: UInt): Int {
        val tex = createColorTexture(dim, dim)
        val data = ByteBuffer.allocateDirect(dim * dim * 4).order(ByteOrder.nativeOrder())
        var state = seed
        for (i in 0 until dim * dim * 4) {
            state = state * 0x9E3779B1u + 0xDEADBEEFu
            data.put(((state shr 24).toByte().toInt() xor 0xA5).toByte())
        }
        data.position(0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, tex)
        GLES31.glTexSubImage2D(
            GLES31.GL_TEXTURE_2D, 0, 0, 0, dim, dim,
            GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, data
        )
        return tex
    }

    private fun createQuadVbo(): Int {
        val verts = floatArrayOf(
            -1f, -1f, 0f,
             1f, -1f, 0f,
            -1f,  1f, 0f,
             1f,  1f, 0f
        )
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(verts); position(0) }
        val vbo = IntArray(1).also { GLES31.glGenBuffers(1, it, 0) }[0]
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo)
        GLES31.glBufferData(
            GLES31.GL_ARRAY_BUFFER,
            verts.size * 4,
            buf,
            GLES31.GL_STATIC_DRAW
        )
        return vbo
    }

    private fun createSsbo(byteSize: Int): Int {
        val ssbo = IntArray(1).also { GLES31.glGenBuffers(1, it, 0) }[0]
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbo)
        GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, byteSize, null, GLES31.GL_DYNAMIC_DRAW)
        return ssbo
    }

    private fun uploadFloats(ssbo: Int, src: FloatArray) {
        val buf = ByteBuffer.allocateDirect(src.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(src); position(0) }
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbo)
        GLES31.glBufferSubData(GLES31.GL_SHADER_STORAGE_BUFFER, 0, src.size * 4, buf)
    }

    private fun downloadFloats(ssbo: Int, dst: FloatArray) {
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbo)
        val mapped = GLES31.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER, 0, dst.size * 4, GLES31.GL_MAP_READ_BIT
        ) as? java.nio.ByteBuffer
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder()).asFloatBuffer().get(dst)
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
        }
    }

    // ─────────── Helpers (CPU side) ───────────

    private fun generateMatrix(out: FloatArray, seed: UInt) {
        var state = seed
        for (i in out.indices) {
            state = state * 0x9E3779B1u + 0xDEADBEEFu
            out[i] = (state.toLong().toDouble() / 4294967296.0 - 0.5).toFloat()
        }
    }

    private fun computeReferenceTrace(A: FloatArray, B: FloatArray): Double {
        // CPU scalar matmul, only the diagonal of C (we don't need the
        // full output — trace only requires C[i,i]).
        var trace = 0.0
        val N = GEMM_DIM
        for (i in 0 until N) {
            var sum = 0.0f
            for (k in 0 until N) {
                sum += A[i * N + k] * B[k * N + i]
            }
            trace += sum.toDouble()
        }
        return trace
    }
}
