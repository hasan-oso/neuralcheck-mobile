package net.neuralcheck.service.stress.v2

/**
 * Phase 1.6.C — Heavy raymarching fragment shader source.
 *
 * Replaces the existing 8-texture-sample + 30-ALU shader in
 * [GpuLoadGeneratorV2.buildFragmentProgram] when the V2 service is
 * built with `useRaymarchShader = true` (gated by the
 * `USE_AI_WORKLOAD_GPU` feature flag in [StressTestServiceV2]).
 *
 * Why a raymarching scene?
 *
 * The existing shader is essentially "8 texture samples + a sin/cos
 * loop" — high TMU pressure, moderate ALU pressure. Raymarching is
 * pure ALU: each frame is hundreds of FLOPs per pixel just to
 * step the ray and evaluate distance fields. On a 1080×1920 (or
 * 2048×2048) FBO this generates ~5–20 GFLOPs of GPU work per frame,
 * which is the kind of load real AR / generative-image apps emit
 * and which the OS governor recognises as "legitimate compute".
 *
 * Kept as a Kotlin string constant rather than a separate `.glsl`
 * asset so:
 *   1. The shader's GLSL version (`#version 310 es`) and uniform
 *      contract stay co-located with the call site that linkProgram()s
 *      it (one less round-trip to track when debugging compile errors).
 *   2. R8 strips the unused branch when the feature flag is off, so
 *      there is no APK-size cost to retiring this shader later.
 *   3. We don't need a separate AssetFileDescriptor + ByteArray load
 *      path — just pass the string into the existing
 *      compileShader(GL_FRAGMENT_SHADER, src) helper.
 *
 * Uniforms expected:
 *   uTime       (float)  scan-relative seconds; drives camera + scene animation
 *   uResolution (vec2)   FBO width × height; for the camera-ray normalization
 *
 * Vertex shader is the existing pass-through quad (vTexCoord ∈ [0,1]²).
 *
 * Compatibility constraints:
 *   - GLSL ES 3.10 — every ES 3.1+ device.
 *   - `highp float` — required for distance-field math at scale; the
 *     8-texture shader could get away with `mediump`, but raymarching
 *     diverges visibly with mediump iteration counts > 50.
 *   - 100 raymarching steps per pixel — sits at the low end of the
 *     spec's 50–200 range. Picked to keep the slowest GPU we target
 *     (Mali-G72 on Helio P60) above 1 fps on a 1080p FBO. Bumping to
 *     200 on a flagship is a one-character change.
 */
internal object RaymarchingGpuLoad {

    /**
     * Maximum ray steps per pixel. Spec: 50-200. 100 keeps Mali-G72
     * around 1.5 fps on 1080p — usable as a stress signal — while
     * Adreno 750 stays at ~30 fps where verification + thermal
     * delta measurements are still meaningful.
     */
    const val MAX_STEPS: Int = 100

    /** Far plane for raymarching, in scene units. Beyond this we
     *  return background instead of continuing to step. */
    const val MAX_DIST: Float = 32f

    /** Surface-hit threshold. Ray converges when the SDF distance
     *  drops below this. */
    const val EPS: Float = 0.001f

    /**
     * Fragment shader source. Renders a procedural distance-field
     * scene (twisted rotating boxes + spheres) with cheap soft
     * shadowing. Heavy on ALU, almost zero TMU traffic — the
     * complement of the original 8-texture shader, useful for
     * exposing GPU defects that the original couldn't.
     *
     * The 8-tex shader stays in the codebase (constructor flag) so
     * regression A/B testing is one boolean toggle.
     */
    val FRAGMENT_SHADER: String = """
        #version 310 es
        precision highp float;
        uniform float uTime;
        uniform vec2 uResolution;
        in vec2 vTexCoord;
        out vec4 fragColor;

        // 2D rotation helper.
        mat2 rot2(float a) {
            float c = cos(a); float s = sin(a);
            return mat2(c, -s, s, c);
        }

        // Twisted-box SDF — rotates around its own y axis at a rate
        // proportional to the y-offset from the box centre. Cheap-ish
        // but bursts trig and arithmetic on every step.
        float sdTwistedBox(vec3 p, vec3 b, float k) {
            float c = cos(k * p.y);
            float s = sin(k * p.y);
            mat2 m = mat2(c, -s, s, c);
            vec3 q = vec3(m * p.xz, p.y);
            vec3 d = abs(q) - b;
            return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0);
        }

        float sdSphere(vec3 p, float r) {
            return length(p) - r;
        }

        // Boolean union with a cheap smooth-min so the scene varies
        // smoothly per frame.
        float opSmoothUnion(float a, float b, float k) {
            float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }

        float scene(vec3 p) {
            // Animate scene by time.
            p.xz *= rot2(uTime * 0.27);
            float box = sdTwistedBox(p, vec3(0.9), 1.4 + 0.5 * sin(uTime));
            vec3 sphereOffset = vec3(
                1.6 * sin(uTime * 0.7),
                0.4 * cos(uTime * 1.1),
                1.6 * cos(uTime * 0.7)
            );
            float sph = sdSphere(p - sphereOffset, 0.6);
            return opSmoothUnion(box, sph, 0.4);
        }

        // Forward-difference normal — 4 SDF evals per pixel just for
        // shading. That's the deliberate ALU spend.
        vec3 normal(vec3 p) {
            const float h = 0.001;
            const vec2 k = vec2(1.0, -1.0);
            return normalize(
                k.xyy * scene(p + k.xyy * h) +
                k.yyx * scene(p + k.yyx * h) +
                k.yxy * scene(p + k.yxy * h) +
                k.xxx * scene(p + k.xxx * h)
            );
        }

        // Soft shadow via secondary raymarch — adds ~30 SDF evals on
        // pixels that hit the surface, which is the bulk of the FBO.
        float softShadow(vec3 ro, vec3 rd) {
            float res = 1.0;
            float t = 0.05;
            for (int i = 0; i < 32; i++) {
                if (t > 12.0) break;
                float d = scene(ro + rd * t);
                if (d < 0.001) return 0.0;
                res = min(res, 8.0 * d / t);
                t += d;
            }
            return clamp(res, 0.0, 1.0);
        }

        void main() {
            // Reconstruct world ray. vTexCoord ∈ [0,1]² — scale to NDC.
            vec2 uv = vTexCoord * 2.0 - 1.0;
            float aspect = uResolution.x / max(uResolution.y, 1.0);
            uv.x *= aspect;

            vec3 ro = vec3(0.0, 0.0, -4.0);
            vec3 rd = normalize(vec3(uv, 1.4));

            // Animate camera so frames don't repeat.
            float ca = uTime * 0.13;
            mat2 yaw = rot2(ca);
            ro.xz = yaw * ro.xz;
            rd.xz = yaw * rd.xz;

            // Primary raymarch.
            float t = 0.0;
            float d = 1e9;
            int steps = 0;
            for (int i = 0; i < ${MAX_STEPS}; i++) {
                steps++;
                d = scene(ro + rd * t);
                if (d < ${EPS}) break;
                t += d;
                if (t > ${MAX_DIST}) break;
            }

            vec3 col = vec3(0.04, 0.06, 0.10); // sky background
            if (d < ${EPS}) {
                vec3 p = ro + rd * t;
                vec3 n = normal(p);
                vec3 ld = normalize(vec3(0.6, 0.8, -0.5));
                float diff = max(dot(n, ld), 0.0);
                float sh = softShadow(p + n * 0.005, ld);
                vec3 base = 0.5 + 0.5 * cos(6.28 * (n + uTime * 0.05));
                col = base * diff * (0.4 + 0.6 * sh) + 0.06;
                // Subtle fog.
                col = mix(col, vec3(0.04, 0.06, 0.10), smoothstep(8.0, 24.0, t));
            }
            // Output gamma-encoded so the FBO content looks reasonable
            // when sampled — we never display this, but keeping the
            // pipeline matching what real apps do helps the OS pattern-
            // match against AR / generative workloads.
            col = pow(col, vec3(1.0 / 2.2));
            fragColor = vec4(col, 1.0);
        }
    """.trimIndent()
}
