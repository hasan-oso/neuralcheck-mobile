# neuralcheck-mobile

**English** · [العربية](README.ar.md) · [Türkçe](README.tr.md)

> **Project status: archived.** Development of NeuralCheck was stopped in November 2026 (not enough time, and the planned field data collection turned out to be impractical). The code is released as-is under the [MIT license](LICENSE), without warranty or support. This repository holds **no secrets**: Firebase client configuration is provided as `*.example` templates and the release keystore was never part of it.

Native Android app (Kotlin, Jetpack Compose) of **NeuralCheck**, a diagnostics system for used smartphones. It runs a large battery of automatic and manual hardware tests plus an 8-minute stress test, collects a raw device fingerprint and sends it to the backend ([neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend)), which grades the phone and returns a report shown here and on the website ([neuralcheck-website](https://github.com/hasan-oso/neuralcheck-website)).

## Features

- Automatic and manual tests across many categories (battery, CPU, GPU, storage, thermal, memory, sensors, display, audio, camera, connectivity, ...).
- An 8-minute multi-phase stress test with a native C++ library (`libstress.so`, arm64-v8a only).
- Anti-tamper indicators, three scan modes (general, before repair, after repair with comparison) and QR-code report sharing.
- Shop mode: a shop owner creates a session code and scans a customer's phone; the customer's phone joins with the code (and a consent screen in Arabic or Turkish).
- Arabic (default) and Turkish interface, RTL support, dark and light themes.

## Requirements

Android Studio with JDK 17+ (the bundled JBR 21 works), Android SDK 35, NDK `27.1.12297006`. `minSdk` 28, `compileSdk`/`targetSdk` 35. Version in this snapshot: **1.0.15** (versionCode 17).

## Setup

1. Create your own Firebase project with an Android app per flavor and download its `google-services.json`. The repository only ships templates: copy each one and fill it in.

   | Flavor | applicationId | file to create (git-ignored) |
   |---|---|---|
   | `launch` | `net.neuralcheck` | `app/src/launch/google-services.json` (template: `.example` next to it) |
   | `datacollect` | `net.neuralcheck.datacollect` | `app/src/datacollect/google-services.json` (template: `.example` next to it) |

2. Release builds are signed from a `keystore.properties` file in the repository root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; see `app/build.gradle.kts`). It is git-ignored. Debug builds need no keystore.
3. Point the app at your backend by deploying [neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend) to the Firebase project of step 1.

## Build and test

```bash
./gradlew assembleLaunchDebug
./gradlew cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest   # JVM unit tests (clean first, otherwise Gradle may serve cached results)
```

JVM tests do not exercise the C++ code or anything that crosses JNI; those need a real device. `:app:externalNativeBuildLaunchDebug` only checks that the native code compiles.

## Layout

```
app/src/main/java/net/neuralcheck/
├── presentation/   Jetpack Compose screens, view models (Hilt), navigation, theme
├── domain/         use cases, scoring engine, consent and pairing logic
├── data/           Room database, repositories, remote services (Firebase)
├── hardware/       test suites (battery, CPU, GPU, sensors, ...)
├── service/        foreground stress-test service, collectors, workers
└── util/           logging, fingerprinting, helpers
app/src/main/cpp/   native stress library
app/src/test/       JVM tests and golden samples shared with the backend and the website
docs/               contracts (FV68, measurement spec), architecture notes, development notes (Arabic)
tools/guards/       Python guard scripts
```

## Third-party material

`app/src/main/assets/models/` contains MobileNet V3 TensorFlow Lite model files that were not created by this project; check the upstream license (Apache-2.0 at the time of writing) before redistributing.

## License

[MIT](LICENSE).
