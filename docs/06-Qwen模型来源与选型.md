# Qwen 模型来源与选型

## 当前首选

首版端侧结构化纪要，优先使用：

- `taobao-mnn/Qwen3.5-0.8B-MNN`

原因：

- 已经是 `MNN-LLM` 可直接消费的模型包
- 体积较小，适合首版手机本地验证
- 与当前工程的 `llm.mnn + llm.mnn.weight + config.json` 结构天然对齐

本地目录已经准备好：

```text
`.local/models/taobao-mnn/Qwen3.5-0.8B-MNN`
```

## 对应官方开源来源

文本模型官方仓库：

- `Qwen/Qwen3.5-0.8B`
- `Qwen/Qwen3.5-2B`
- `Qwen/Qwen3.5-4B`

视觉模型官方仓库：

- `Qwen/Qwen3-VL-2B-Instruct`
- `Qwen/Qwen3-VL-4B-Instruct`

## 现成 MNN 包

目前已确认可直接拉取的 `taobao-mnn` 包：

- `taobao-mnn/Qwen3.5-0.8B-MNN`
- `taobao-mnn/Qwen3.5-2B-MNN`

## 建议升级顺序

1. `Qwen3.5-0.8B-MNN`
   用于跑通本地纪要主链路和 JNI 会话
2. `Qwen3.5-2B-MNN`
   用于提升总结质量与稳定性
3. `Qwen3-VL-2B-Instruct`
   用于真正把图片理解纳入本地模型主链

## 当前状态

- 工程已经指向 `Qwen3.5-0.8B-MNN`
- `gradle.properties` 已回填本地文件路径和 `sha256`
- `assembleDebug` 已通过
