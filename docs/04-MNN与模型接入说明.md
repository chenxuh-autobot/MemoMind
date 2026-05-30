# MNN 与本地模型接入说明

## 1. 真实 MNN 预编译库放置位置

把真实的 MNN 头文件和 `libMNN.so` 放到：

`ai/mnn/src/main/cpp/third_party/mnn`

目录结构应为：

```text
ai/mnn/src/main/cpp/third_party/mnn/
  include/
    MNN/Interpreter.hpp
    ...
  lib/
    arm64-v8a/libMNN.so
```

当前阶段只把 `arm64-v8a` 作为必需项，真机优先。后续如果要补模拟器或更多机型，再加其他 ABI。

当前工程的 CMake 会自动探测这套结构：

- 存在则启用真实 `MNN` 链接
- 不存在则继续走 stub bridge

## 1.1 推荐脚本流程

项目根目录已经带了三份脚本：

- `scripts/build_mnn_android.sh`
- `scripts/install_mnn_prebuilt.sh`
- `scripts/setup_mnn_arm64.sh`

最快的方式是直接执行：

```bash
scripts/setup_mnn_arm64.sh --verify
```

它会完成：

1. 拉取或复用官方 `MNN` 源码
2. 调用官方 `project/android/build_64.sh`
3. 运行 `make install`
4. 拷贝 `include/MNN/...` 和 `lib/arm64-v8a/libMNN.so` 到当前工程
5. 可选执行 `./gradlew :app:assembleDebug`

如果你只想先编再手动看产物，也可以分开跑：

```bash
scripts/build_mnn_android.sh
scripts/install_mnn_prebuilt.sh
```

默认 NDK 路径是：

```text
$HOME/Library/Android/sdk/ndk/28.2.13676358
```

脚本还会自动探测 `Android SDK` 下的 `CMake`，并默认关闭 `MNN_BUILD_TEST` 与 `MNN_BUILD_BENCHMARK`，避免为了 App 接入额外编一大批测试目标。

也支持手动指定：

```bash
scripts/setup_mnn_arm64.sh --ndk /path/to/ndk --mnn-root /path/to/MNN --verify
```

## 2. 本地模型目录结构

当前默认模型目录在 App 私有目录下，例如：

```text
<app files>/creative_ai/models/qwen-local-1_5b-text/
  tokenizer.txt
  llm.mnn
  llm.mnn.weight
  llm_config.json
  config.json
```

当前 `probeModelDirectory()` 会检查：

- `tokenizer.txt` 或 `tokenizer.json`
- `llm.mnn` 或 `model.mnn`
- `llm.mnn.weight`
- `llm_config.json`
- `config.json`

其中 `embeddings_bf16.bin` 仍然是官方 `llmexport.py` 常见导出物，但当前这份现成 `taobao-mnn` 模型包并不强制依赖它，所以本工程不再把它当作安装前置条件。

## 3. 模型安装器支持的来源

当前安装器 `ModelInstaller` 同时支持：

- `https://...`
- `http://...`
- `file:///absolute/path/to/file`
- 直接本地绝对路径

这意味着你有两种接入方式：

### 方式 A：远程下载

把 `ModelManifest.assets[].downloadUrl` 配成公网或局域网地址。

### 方式 B：本地拷贝

把 `ModelManifest.assets[].downloadUrl` 配成：

- `file:///Users/xxx/tokenizer.txt`
- `/Users/xxx/tokenizer.txt`

这样开发阶段不需要先搭文件服务器。

## 4. 当前工程已经具备的能力

- 生成模型安装计划
- 逐文件下载或本地拷贝
- SHA256 校验
- 安装完成后校验目录完整性
- JNI 探测模型目录
- JNI 打开最小会话

## 5. 下一步接真实资源的最短路径

1. 跑 `scripts/setup_mnn_arm64.sh --verify`
2. 准备一个真实模型目录：
   `tokenizer.txt`、`llm.mnn`、`llm.mnn.weight`、`llm_config.json`、`config.json`
3. 把 `ModelManifest.assets[].downloadUrl` 改成可访问的真实地址或本地 `file://` 路径
4. 重新安装模型
5. 运行 App，观察首页中的：
   `真实 MNN 已链接`
   `模型目录探测`
   `会话打开结果`

## 6. 现在还没做的部分

- 真实推理输入张量组装
- tokenizer 真正接入
- 输出 token 解码
- 图片/音频预处理接入真实链路

也就是说，当前阶段已经把“资源进来”和“native 会话打开”这两层地基搭好，下一步就是把真正的推理链串起来。
