# MemoMind

MemoMind 的 Android 源码仓库，当前只保留最核心的工程源码、资源和 Gradle 构建文件。

## 仓库中保留的内容

- `app/`、`ai/`、`core/`、`feature/` 下的核心 Android 源码
- Gradle 工程文件与 Wrapper
- App 必需的基础资源与少量运行时配置

## 仓库中不保留的内容

- 大模型权重与离线 ASR 模型
- 本地 AAR、APK、AAB、构建缓存
- 文档、脚本、bridge 辅助工具、Supabase 迁移文件
- 本机环境相关配置与临时文件

## 补充依赖

源码仓库默认不包含下面这些外部依赖：

- `app/libs/sherpa-onnx-1.13.2.aar`
- `app/src/main/assets/asr/`
- `app/src/main/assets/models/qwen-vl-2b-instruct-mnn/`
- `ai/mnn/src/main/cpp/third_party/mnn/include/`
- `ai/mnn/src/main/cpp/third_party/mnn/lib/`

如果只是查看和协作源码，这些文件可以不放进 GitHub。

如果需要本地编译当前工程，至少需要先补齐：

- `app/libs/sherpa-onnx-1.13.2.aar`

如果需要验证完整的端侧模型能力，还需要额外准备：

- 离线 ASR 资源
- Qwen MNN 模型目录
- 真实 MNN 预编译库
