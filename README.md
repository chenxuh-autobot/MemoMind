# MemoMind

一个面向 Android 手机端的多模态会议纪要与创意整理原型工程。当前工程优先保证：

- 手机端源码工程可稳定协作
- 默认 APK 体积可控，不再强塞 2GB 级本地大模型
- 本地模型不可用时，App 仍能生成轻量纪要结果而不是直接闪退

## 模块结构

- `app`：Compose 宿主应用与页面编排
- `core:model`：共享数据模型
- `core:database`：本地任务与纪要持久化
- `core:filesystem`：应用私有目录管理
- `core:network`：云端辅助能力预留层
- `feature:home`：设置页
- `feature:capture`：采集页
- `feature:result`：结果页
- `feature:history`：历史页
- `ai:orchestrator`：任务编排与纪要整理
- `ai:modelmanager`：模型清单与安装状态管理
- `ai:mnn`：MNN/JNI 接入层

## 这次重点修正了什么

之前的问题主要有三类：

1. APK 内直接塞了 `Qwen` 大模型，`app-debug.apk` 约 `1.86GB`。
2. 首次启动会把内置模型再复制到 `files/creative_ai/models/...`，导致“安装包大 + 数据目录再膨胀一份”。
3. App 启动时就尝试预热本地大模型会话，低内存或低存储手机更容易闪退。

现在默认行为改为：

- 默认构建轻量包，不再把 `qwen-vl-2b-instruct-mnn` 打进 APK
- 纪要生成改成按需加载本地模型，不在启动阶段强开大会话
- 本地生成失败时自动回退到轻量纪要整理，优先保证可用性
- 内置模型复制前会先检查存储空间，复制失败也会优雅返回

最近一轮又补了三项直接影响内存的改造：

- 麦克风 `ASR + VAD` 改成真正按需加载，录音结束后立即释放运行时
- 音频文件转写改成 `PCM 增量读取 + VAD 分段 + 分段识别`，不再把整段音频一次性读进内存
- 结构化纪要执行器增加“长文本分块摘要 + 滚动合并”，避免 20 到 30 分钟会议转写一次性塞进本地模型

## 推荐构建方式

### 1. 默认轻量包

适合协作开发、真机联调、比赛演示前的稳定验证：

```bash
./gradlew :app:assembleDebug
```

默认配置：

- `memomind.bundleLargeModels=false`
- `memomind.bundleBundledAsr=false`

这意味着：

- 不内置 2GB 级 Qwen 大模型
- 不内置离线 ASR 模型资源
- 更适合大多数手机安装与调试
- 麦克风转写入口会自动降级为不可用，但图片/OCR/文本纪要链路仍可正常验证

### 2. 全量本地模型包

仅建议高端真机、充足存储、明确需要端侧大模型时使用：

```bash
./gradlew :app:assembleDebug -Pmemomind.bundleLargeModels=true -Pmemomind.bundleBundledAsr=true
```

说明：

- 全量包仍然会很大
- 首次启动仍需准备本地模型目录
- 低内存设备不建议直接分发这个 APK
- 如果只想保留 ASR、不打 Qwen 大模型，也可以单独传 `-Pmemomind.bundleBundledAsr=true`

## Android Studio 打开方式

1. 用 Android Studio 打开项目根目录。
2. IDE 使用 Android Studio 自带 JDK 21。
3. 安装 Android SDK Platform 36、Build Tools 36.1.0、NDK 28.2.13676358、CMake 3.22.1。
4. 首次 Sync 后再继续接入本地模型和原生层。

## MNN 准备方式

当前工程默认按 `arm64-v8a` 真机优先。

真实模型接入最短路径见：

- [真实模型接入步骤](docs/05-真实模型接入步骤.md)

如果你想一键完成“拉官方源码 -> 编译 MNN -> 拷贝到项目 -> 可选验证”，可运行：

```bash
scripts/setup_mnn_arm64.sh --verify
```

也可以拆开跑：

```bash
scripts/build_mnn_android.sh
scripts/install_mnn_prebuilt.sh
```

## 协作规范

请不要把下面这些内容提交到仓库：

- `build/`、`app/build/`、`**/.cxx/`
- 本地生成的 `apk/aab`
- 本地大模型权重
- 本机 `local.properties`、IDE 缓存

仓库已经补了 `.gitignore`，但如果历史里已经推过大文件，仍建议后续单独清理 Git 历史。

## 体积优化建议

按个人开发者可承受范围，优先建议这条路线：

1. GitHub 仓库只保留源码，不保留大模型和构建产物。
2. 大模型放到仓库外分发。
3. 默认发轻量 APK，把“大模型下载/导入”变成可选动作。

可选的低成本托管方案：

- `GitHub Releases`：适合发 APK、配置文件、小型模型分片
- `Hugging Face Model Repo`：适合发模型文件，下载体验比直接塞 Git 更合理
- `Cloudflare R2`：个人项目可用，适合做稳定直链下载

当前工程里的 `core:network` 还是预留层；如果后面你决定接云端纪要增强，可以在这个层里加一个极简云端 fallback。

## 给协作者的建议

如果你伙伴手机上装的是旧的超大包，建议先：

1. 卸载旧版 App
2. 清理旧数据目录
3. 安装新的轻量 APK 再验证

这样能避免旧版本残留模型和缓存继续占空间，影响排查结果。
