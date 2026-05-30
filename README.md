# MemoMind: Creative AI Android

一个面向 Android 手机端的多模态会议纪要与创意整理原型工程，主路线是：

- `Qwen` 本地部署优先
- `MNN` 作为端侧推理接入位
- 云端仅做增强和兜底

## 当前工程包含

- `app`：Compose 宿主应用
- `core:model`：跨模块共享数据模型
- `core:database`：本地任务存储抽象
- `core:filesystem`：本地目录与文件路径抽象
- `core:network`：云端辅助抽象
- `feature:home`：首页骨架
- `feature:capture`：多模态采集页
- `feature:result`：结构化纪要结果页
- `feature:history`：任务历史页
- `ai:orchestrator`：本地优先编排层
- `ai:modelmanager`：模型清单与安装管理抽象
- `ai:mnn`：`MNN/JNI` 接入位

## 打开方式

1. 用 Android Studio 打开项目根目录。
2. 让 IDE 使用 Android Studio 自带 JDK 21。
3. 安装 Android SDK Platform 36、Build Tools 36.1.0、NDK 28.2.13676358、CMake 3.22.1。
4. 首次 Sync 后再继续接入本地模型和原生层。

## 当前状态

- 已完成多模块 Android 工程骨架。
- 已完成 `Home / Capture / History / Result` 四页闭环。
- 已接入真实 `MNN` JNI 桥接、会话探测与本地文本生成接口。
- 已接入本地模型管理、安装计划、下载器与 SHA256 校验链路。
- 已支持图片 `OCR`、图片语义标签提取、麦克风转写、自动录音留档、结果页音频回放。
- 已支持“音频文件级重跑转写”，可把已保存录音再次转写并回填到 `Capture` 页。
- 任务与结构化纪要会持久化到本地 JSON 数据源，方便历史回看与结果复用。

## MNN 准备方式

当前工程默认按 `arm64-v8a` 真机优先。

真实模型接入的最短操作流见：

- [真实模型接入步骤](docs/05-真实模型接入步骤.md)

如果你想一键完成“拉官方源码 -> 编译 MNN -> 拷贝到项目 -> 可选验证”，直接运行：

```bash
scripts/setup_mnn_arm64.sh --verify
```

也可以拆开跑：

```bash
scripts/build_mnn_android.sh
scripts/install_mnn_prebuilt.sh
```

脚本会把真实预编译产物安装到：

```text
ai/mnn/src/main/cpp/third_party/mnn/
  include/MNN/...
  lib/arm64-v8a/libMNN.so
```

默认会使用本机的：

- `ANDROID_NDK=$HOME/Library/Android/sdk/ndk/28.2.13676358`

脚本会优先自动探测 `Android SDK` 里的 `CMake 3.22.1`，并默认关闭 `MNN_BUILD_TEST` 与 `MNN_BUILD_BENCHMARK`，这样更适合当前 App 接入阶段。

如果你后面想强制指定别的 NDK 或本地 MNN 源码目录，可以传：

```bash
scripts/setup_mnn_arm64.sh --ndk /path/to/ndk --mnn-root /path/to/MNN --verify
```

## 推荐下一步

1. 在真机上完成完整闭环验收，并记录耗时、发热、识别成功率与稳定性。
2. 把 JSON 本地仓库升级为更稳健的持久化方案，例如 `Room`。
3. 继续增强图片理解与长音频处理能力，把多模态前处理做得更稳。
