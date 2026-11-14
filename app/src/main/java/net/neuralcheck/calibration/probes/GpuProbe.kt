package net.neuralcheck.calibration.probes

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES31
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.service.stress.v2.RaymarchingGpuLoad
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase 1.8 — Raymarching FPS probe at 1024×1024.
 *
 * Mirrors `net.neuralcheck.service.stress.v2.GpuLoadGeneratorV2`'s
 * fragment-loop setup exactly:
 *   - EGL14 + PbufferSurface offscreen context (no GLSurfaceView — the
 *     probe runs at scan start, before any UI is mounted)
 *   - GLES 3.1 minimum (compute-shader-capable; same as the production
 *     phase requires)
 *   - The same vertex pass-through quad
 *   - The same raymarching fragment shader from
 *     [RaymarchingGpuLoad.FRAGMENT_SHADER]
 *   - The same RGBA8 color-attachment FBO
 *   - The same fence cadence (every 30 frames) so per-frame queue depth
 *     matches what the production phase will see
 *
 * Probe size is fixed at 1024 × 1024 — that's also the FBO that
 * [DeviceCapabilityProfile.gpuFpsAt1024Sq] is named for and the input
 * [WorkloadCalibration] expects when computing
 * `gpuFboSidePixels = sqrt(probePps × FACTOR_GPU / TARGET_FPS)`.
 *
 * ### Test strategy
 * No JVM unit test. EGL14 / GLES31 require Android runtime + a real
 * GPU; Robolectric mocks would produce no signal here. Real measurement
 * validation happens in Phase 1.8.E hardware testing (Samsung S10 5G's
 * Mali-G76 + whatever lab adds).
 *
 * ### Implementation note — duplicated helpers
 * The corresponding helpers in `GpuLoadGeneratorV2`
 * (`createEglContext`, `buildFragmentProgram`, `linkProgram`,
 * `compileShader`, `createColorFbo`, `createColorTexture`,
 * `createQuadVbo`) are all `private`. To keep the production file
 * read-only per Phase 1.8 Task 2b's brief, the helpers below are
 * duplicated verbatim. The duplication is bounded (~140 LOC) and
 * scoped to this probe. If/when a refactor extracts a shared
 * `Egl + ProgramBuilder` utility, replace the duplicates here.
 *
 * ### Failure modes (all propagate to ProbePhase, which tags `"gpu"`)
 *  - `eglGetDisplay` returns NO_DISPLAY      → IllegalStateException
 *  - `eglInitialize` fails                   → IllegalStateException
 *  - `eglChooseConfig` returns 0 configs     → IllegalStateException
 *  - `eglCreateContext` returns NO_CONTEXT   → IllegalStateException
 *  - `eglCreatePbufferSurface` fails         → IllegalStateException
 *  - `eglMakeCurrent` fails                  → IllegalStateException
 *  - shader compile fails (GLSL ES 3.1 unsupported, driver bug)
 *                                            → IllegalStateException
 *  - FBO incomplete (rare; wrong FBO size on legacy Mali drivers)
 *                                            → IllegalStateException
 */
object GpuProbe {

    private const val TAG = "NeuralCheck.Probe.Gpu"
    private const val FBO_DIM: Int = 1024
    private const val FENCE_EVERY_N_FRAMES: Int = 30
    private const val WARMUP_NS: Long = 1_000_000_000L
    private const val MEASUREMENT_NS: Long = 5_000_000_000L
    private const val MEASUREMENT_SECONDS: Double = 5.0
    private const val FENCE_TIMEOUT_NS: Long = 1_000_000_000L

    data class Result(
        val fpsAt1024Sq: Double,
        val durationMs: Long,
        val framesRendered: Long
    )

    @Suppress("UNUSED_PARAMETER")  // context kept for API symmetry with CpuProbe
    suspend fun measure(context: Context): Result = withContext(Dispatchers.Default) {
        val egl = createEglContext()
            ?: throw IllegalStateException("GpuProbe: EGL context creation failed")
        try {
            val rendererName = GLES31.glGetString(GLES31.GL_RENDERER) ?: "unknown"
            val glVersion = GLES31.glGetString(GLES31.GL_VERSION) ?: "unknown"
            Log.i(TAG, "GpuProbe.measure on $rendererName ($glVersion), FBO=${FBO_DIM}x$FBO_DIM")

            val program = buildRaymarchProgram()
                ?: throw IllegalStateException("GpuProbe: raymarching shader compile/link failed")
            val (fbo, fboColor) = createColorFbo(FBO_DIM, FBO_DIM)
                ?: throw IllegalStateException("GpuProbe: FBO allocation incomplete")
            val quad = createQuadVbo()

            try {
                GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)
                GLES31.glViewport(0, 0, FBO_DIM, FBO_DIM)
                GLES31.glDisable(GLES31.GL_DEPTH_TEST)
                GLES31.glUseProgram(program)

                val posHandle = GLES31.glGetAttribLocation(program, "aPosition")
                val timeHandle = GLES31.glGetUniformLocation(program, "uTime")
                val resHandle = GLES31.glGetUniformLocation(program, "uResolution")

                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, quad)
                GLES31.glEnableVertexAttribArray(posHandle)
                GLES31.glVertexAttribPointer(posHandle, 3, GLES31.GL_FLOAT, false, 12, 0)

                if (resHandle >= 0) {
                    GLES31.glUniform2f(resHandle, FBO_DIM.toFloat(), FBO_DIM.toFloat())
                }

                val startNs = System.nanoTime()
                val warmupEndNs = startNs + WARMUP_NS
                val measurementEndNs = warmupEndNs + MEASUREMENT_NS

                // ── Warmup ──
                // Same draw cadence as production — counter-side discards
                // these frames, but the GPU pipeline + driver caches
                // (shader binary cache, register-allocation table) still
                // warm up.
                var warmupFrames = 0
                while (System.nanoTime() < warmupEndNs) {
                    val time = (System.nanoTime() - startNs) / 1_000_000_000f
                    GLES31.glUniform1f(timeHandle, time)
                    GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
                    GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                    warmupFrames++
                    if (warmupFrames % FENCE_EVERY_N_FRAMES == 0) {
                        drainFence()
                    }
                }
                drainFence()  // final warmup drain so measurement starts cold-ish

                // ── Measurement ──
                val measureStartNs = System.nanoTime()
                var frames = 0L
                while (System.nanoTime() < measurementEndNs) {
                    val time = (System.nanoTime() - startNs) / 1_000_000_000f
                    GLES31.glUniform1f(timeHandle, time)
                    GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
                    GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                    frames++
                    if (frames % FENCE_EVERY_N_FRAMES == 0L) {
                        drainFence()
                    }
                }
                drainFence()  // make sure all measurement frames actually rendered

                val durationMs = (System.nanoTime() - startNs) / 1_000_000L
                val measureMs = (System.nanoTime() - measureStartNs) / 1_000_000L
                val fps = frames.toDouble() / (measureMs.toDouble() / 1_000.0)

                Log.i(
                    TAG,
                    "GpuProbe.measure done: warmupFrames=$warmupFrames frames=$frames " +
                        "fps=${"%.2f".format(fps)} measureMs=$measureMs"
                )

                return@withContext Result(
                    fpsAt1024Sq = fps,
                    durationMs = durationMs,
                    framesRendered = frames
                )
            } finally {
                // Cleanup GL resources before context teardown so driver
                // accounting is clean across the next probe / phase.
                GLES31.glDeleteBuffers(1, intArrayOf(quad), 0)
                GLES31.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                GLES31.glDeleteTextures(1, intArrayOf(fboColor), 0)
                GLES31.glDeleteProgram(program)
            }
        } finally {
            destroyEglContext(egl)
        }
    }

    /**
     * Issue a glFenceSync, wait up to 1 s, delete. Same semantics as the
     * production fence drain — keeps the GPU command queue from
     * pipelining beyond ~30 frames so frames-counted matches frames-
     * actually-drawn within tight tolerance.
     */
    private fun drainFence() {
        val fence = GLES31.glFenceSync(GLES31.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        GLES31.glClientWaitSync(fence, GLES31.GL_SYNC_FLUSH_COMMANDS_BIT, FENCE_TIMEOUT_NS)
        GLES31.glDeleteSync(fence)
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers duplicated verbatim from GpuLoadGeneratorV2 (see KDoc above).
    // KEEP IN SYNC if the production helpers ever change — the probe
    // predicts phase performance only when probe and phase use the same
    // EGL config / vertex shader / FBO format.
    // ──────────────────────────────────────────────────────────────────

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

    /**
     * Vertex shader is the same pass-through quad GpuLoadGeneratorV2
     * uses; fragment shader is the production raymarching shader. Both
     * referenced verbatim so per-pixel cost matches the GPU phase.
     */
    private fun buildRaymarchProgram(): Int? {
        val vs = """
            #version 310 es
            in vec4 aPosition;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aPosition.xy * 0.5 + 0.5;
            }
        """.trimIndent()
        val fs = RaymarchingGpuLoad.FRAGMENT_SHADER
        return linkProgram(vs, fs)
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
}
