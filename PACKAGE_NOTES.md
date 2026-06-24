# MemoMind 分享包说明

这个 zip 是干净源码包，适合发给别人用 Android Studio 打开和继续开发。

为了避免文件过大，本包不包含：

- `build/`、`.gradle/`、`.idea/` 等本机构建缓存
- `local.properties` 等本机 SDK 路径配置
- `app/src/main/assets/models/` 下的大模型权重
- `app/src/main/assets/asr/` 下的离线 ASR 模型
- 已构建出的 `apk/aab`

对方打开方式：

1. 用 Android Studio 打开项目根目录。
2. 等 Gradle Sync 完成。
3. 先运行轻量包：`./gradlew :app:assembleDebug`
4. 如果需要完整 Qwen/ASR 模型，请单独复制模型目录或重新按项目文档准备。

当前源码已包含最近修改：

- PDF / Word / PPT / TXT 文档读取
- 录音中红点动效
- 底部导航顺序改为：任务 / 历史 / 结果 / 设置
- 历史任务长按拖动的视觉与触感反馈
