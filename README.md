# MemoMind

MemoMind is an Android prototype for turning voice, images, and documents into structured notes and creative outputs on device.

This repository is intentionally kept source-first: it contains the core app code, module wiring, UI, and native integration points, but does not include large model assets, local build artifacts, or project-specific operational tooling. The goal is to make contribution, review, and code navigation practical for new collaborators.

## What Is In This Repo

- Android app source built with Kotlin, Jetpack Compose, and a modular Gradle layout
- Local-first note capture, history, result, OCR, and document parsing flows
- AI orchestration and model-management code paths
- Native MNN bridge code with a stub-friendly fallback path
- Minimal app resources and Gradle configuration needed to understand and extend the project

## What Is Not In This Repo

- Large model weights
- Offline ASR model bundles
- Local `aar`, `apk`, and build outputs
- Internal bridge utilities, migrations, and archived docs
- Machine-specific secrets and environment config

## Repository Layout

```text
app/                App entrypoint, wiring, Android resources, multimodal pipelines
ai/mnn/             JNI bridge and native MNN integration
ai/modelmanager/    Model catalog, install planning, local model status
ai/orchestrator/    Structured note generation and task orchestration
core/model/         Shared data models
core/database/      Local persistence for tasks and memo results
core/filesystem/    App-private storage helpers
core/network/       Cloud-assist client abstractions
feature/home/       Settings and status surfaces
feature/capture/    Capture and ingestion flows
feature/result/     Result presentation and actions
feature/history/    History and task review
```

## Current Build Reality

This is a source-focused repository, not a fully self-contained binary distribution.

To compile the current app checkout, you must provide:

- `app/libs/sherpa-onnx-1.13.2.aar`

For full on-device AI behavior, you will also need project-local assets that are intentionally excluded from Git:

- `app/src/main/assets/asr/`
- `app/src/main/assets/models/qwen-vl-2b-instruct-mnn/`
- `ai/mnn/src/main/cpp/third_party/mnn/include/`
- `ai/mnn/src/main/cpp/third_party/mnn/lib/`

Without those larger assets, the repository is still useful for code review, UI work, orchestration changes, persistence changes, and general Android contribution.

## Build Requirements

- A recent Android Studio version compatible with AGP `9.2.0`
- Android SDK `36`
- Build Tools `36.1.0`
- Min SDK `29`
- Java/Kotlin toolchain compatible with the project's Java `17` target

If you plan to work on the native MNN layer, you will also want:

- NDK support for `arm64-v8a`
- CMake `3.22.1`

## Build Flags

The app exposes two important Gradle properties in [app/build.gradle.kts](/Users/chenxuhang/MemoMind/app/build.gradle.kts):

- `memomind.bundleLargeModels`
- `memomind.bundleBundledAsr`

These control whether model and ASR assets are bundled into the app package when those assets exist locally.

Example:

```bash
./gradlew :app:assembleDebug \
  -Pmemomind.bundleLargeModels=false \
  -Pmemomind.bundleBundledAsr=false
```

## Contribution Opportunities

Contributors can make meaningful progress even without access to the excluded model files.

High-value areas include:

- UI and UX improvements across capture, result, history, and settings flows
- Refactoring large screens and state handling in `feature/*`
- Better error handling and fallback behavior for missing local AI assets
- Local persistence and task-state improvements in `core/database`
- Document and image ingestion improvements in `app/`
- Cloud-assist abstractions and request shaping in `core/network`
- Native bridge cleanup and observability in `ai/mnn`

## Working Without Private Assets

If you do not have the external model files:

- You can still review and improve most Kotlin code
- You can still work on Compose UI and app architecture
- You can still improve data flow, storage, and orchestration logic
- You should avoid claiming full end-to-end local model validation

If you do have the assets, please keep them outside Git and wire them in locally only.

## Contribution Guidelines

- Keep the repository source-first and lightweight
- Do not commit model weights, APKs, AABs, AARs, or build caches
- Do not commit machine-specific files such as `local.properties`
- Prefer focused PRs with a clear scope
- If a change depends on excluded local assets, explain that clearly in the PR description

## Notes For Native Contributors

The native bridge under [ai/mnn/src/main/cpp](/Users/chenxuhang/MemoMind/ai/mnn/src/main/cpp) is designed to tolerate missing real MNN prebuilts:

- if prebuilts are present, the bridge links against real `libMNN.so`
- if prebuilts are absent, the project can still fall back to a stub-oriented integration path

That makes it possible to contribute to API shape and JNI plumbing without requiring every contributor to carry the full native runtime locally.
