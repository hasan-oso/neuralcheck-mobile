package net.neuralcheck.service.stress

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * GPU stress generator using offscreen EGL PBuffer surface (ADR 4.10).
 *
 * Renders a fullscreen quad with a fragment shader that performs 50 sin/cos iterations
 * per pixel, repeated 100 times per frame. This targets 20-80 FPS on mid/high-end
 * devices instead of the uncapped 300+ FPS of a trivial shader, which caused
 * extreme thermal throttling and a misleading 99%+ FPS drop in G-03/G-04/G-05.
 */
class GpuLoadGenerator {

    companion object {
        private const val TAG = "NeuralCheck"
        private const val PBUFFER_WIDTH = 512
        private const val PBUFFER_HEIGHT = 512
        private const val DRAW_CALLS_PER_FRAME = 100
        // Mali-G71 (Exynos 7904 / Galaxy A30s) batches up draw commands when
        // we use only glFlush + eglSwapBuffers, producing absurd peak FPS like
        // 575 because the counter increments on every requested swap while the
        // GPU has rendered ~10× fewer frames. Adding glFinish forces a real
        // CPU→GPU round-trip per frame and gives an honest FPS reading. The
        // first few frames are still pipeline warm-up — exclude them so the
        // initialFps that drives G-04 throttle math isn't a cold-start spike.
        private const val WARMUP_FRAMES = 5
    }

    private val isRunning = AtomicBoolean(false)
    // Total frames rendered (including warm-up). Used internally to gate the
    // public counter — only frames after WARMUP_FRAMES are visible to
    // GpuFpsTracker, so the first FPS sample isn't a synthetic cold-start.
    private val totalFrameCount = AtomicInteger(0)
    private val frameCount = AtomicInteger(0)
    private var renderThread: Thread? = null
    private var lastFps = 0

    @Volatile private var rendererName: String = "unknown"
    @Volatile private var vendorName: String = "unknown"
    @Volatile private var glVersion: String = "unknown"

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        frameCount.set(0)
        totalFrameCount.set(0)
        renderThread = Thread({ runRenderLoop() }, "NeuralCheck-GPU").apply {
            priority = Thread.NORM_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            renderThread?.join(2000)
        } catch (_: InterruptedException) {
            renderThread?.interrupt()
        }
        renderThread = null
    }

    fun isActive(): Boolean = isRunning.get()

    /** Frame count since last call (and reset counter). Called by [GpuFpsTracker] once per sample. */
    fun getAndResetFrameCount(): Int {
        val count = frameCount.getAndSet(0)
        lastFps = count
        return count
    }

    fun getLastFps(): Int = lastFps
    fun getRendererName(): String = rendererName
    fun getVendorName(): String = vendorName
    fun getGlVersion(): String = glVersion

    private fun runRenderLoop() {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var context: EGLContext = EGL14.EGL_NO_CONTEXT

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "GPU: no EGL display available")
                return
            }
            val versionArr = IntArray(2)
            if (!EGL14.eglInitialize(display, versionArr, 0, versionArr, 1)) {
                Log.w(TAG, "GPU: eglInitialize failed")
                return
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfig, 0)
                || numConfig[0] <= 0
            ) {
                Log.w(TAG, "GPU: eglChooseConfig failed")
                return
            }
            val chosenConfig = configs[0] ?: run {
                Log.w(TAG, "GPU: no suitable EGL config")
                return
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "GPU: eglCreateContext failed (error=${EGL14.eglGetError()})")
                return
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, PBUFFER_WIDTH,
                EGL14.EGL_HEIGHT, PBUFFER_HEIGHT,
                EGL14.EGL_NONE
            )
            surface = EGL14.eglCreatePbufferSurface(display, chosenConfig, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                Log.w(TAG, "GPU: eglCreatePbufferSurface failed (error=${EGL14.eglGetError()})")
                return
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                Log.w(TAG, "GPU: eglMakeCurrent failed (error=${EGL14.eglGetError()})")
                return
            }

            rendererName = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
            vendorName = GLES20.glGetString(GLES20.GL_VENDOR) ?: "unknown"
            glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "unknown"

            val program = buildProgram()
            if (program == 0) {
                Log.w(TAG, "GPU: program build failed — aborting render loop")
                return
            }

            // Fullscreen quad in clip space (-1..1): two triangles as TRIANGLE_STRIP
            val quadVertices = floatArrayOf(
                -1f, -1f, 0f,
                 1f, -1f, 0f,
                -1f,  1f, 0f,
                 1f,  1f, 0f
            )
            val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .apply { put(quadVertices); position(0) }

            val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            val timeHandle = GLES20.glGetUniformLocation(program, "uTime")

            GLES20.glViewport(0, 0, PBUFFER_WIDTH, PBUFFER_HEIGHT)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

            val startNano = System.nanoTime()
            var totalFrames = 0L
            val logStartMs = System.currentTimeMillis()

            while (isRunning.get()) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                val time = (System.nanoTime() - startNano) / 1_000_000_000f
                GLES20.glUniform1f(timeHandle, time)

                repeat(DRAW_CALLS_PER_FRAME) {
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                }

                // glFlush kicks the command stream toward the GPU; glFinish
                // blocks the CPU until every issued draw has actually executed.
                // Without glFinish, Mali drivers (G71 / Exynos 7904 in
                // particular) batch up requests so the swap counter races
                // ahead of real rendering, producing >500 FPS on devices that
                // physically render ~60. With glFinish the swap rate equals
                // the GPU's real throughput.
                GLES20.glFlush()
                GLES20.glFinish()

                if (!EGL14.eglSwapBuffers(display, surface)) {
                    Log.w(TAG, "GPU: eglSwapBuffers failed (error=${EGL14.eglGetError()})")
                    break
                }
                val total = totalFrameCount.incrementAndGet()
                // Skip the first WARMUP_FRAMES frames in the public counter.
                // Cold-start renders include shader-cache misses + driver
                // setup; counting them inflates initialFps and breaks G-04
                // throttle math (which compares initialFps to minFps).
                if (total > WARMUP_FRAMES) {
                    frameCount.incrementAndGet()
                }
                totalFrames++

                if (totalFrames % 100 == 0L) {
                    val elapsed = (System.currentTimeMillis() - logStartMs) / 1000.0
                    if (elapsed > 0) {
                        Log.d(TAG, "GPU FPS: ${(totalFrames / elapsed).toInt()}, total frames: $totalFrames")
                    }
                }
            }

            GLES20.glDisableVertexAttribArray(posHandle)
        } catch (e: Exception) {
            Log.w(TAG, "GPU render loop error: ${e.message}")
        } finally {
            try {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            } catch (_: Exception) { /* best-effort cleanup */ }
        }
    }

    private fun buildProgram(): Int {
        // Fullscreen quad pass-through: positions are already in clip space.
        val vertexShaderCode = """
            attribute vec4 aPosition;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aPosition.xy * 0.5 + 0.5;
            }
        """.trimIndent()

        // 50-iteration sin/cos loop per fragment gives substantial GPU load without
        // reaching the trivial-shader extreme (300+ FPS → instant thermal throttle).
        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform float uTime;
            void main() {
                float x = vTexCoord.x;
                float y = vTexCoord.y;
                float r = 0.0;
                float g = 0.0;
                float b = 0.0;
                for (int i = 0; i < 50; i++) {
                    float fi = float(i) * 0.1;
                    r += sin(x * fi + uTime) * 0.02;
                    g += cos(y * fi + uTime * 0.7) * 0.02;
                    b += sin((x + y) * fi + uTime * 1.3) * 0.02;
                }
                gl_FragColor = vec4(r + 0.5, g + 0.5, b + 0.5, 1.0);
            }
        """.trimIndent()

        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        if (vs == 0) return 0
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        if (fs == 0) return 0

        val program = GLES20.glCreateProgram()
        if (program == 0) return 0
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.w(TAG, "GPU: program link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.w(TAG, "GPU: shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
