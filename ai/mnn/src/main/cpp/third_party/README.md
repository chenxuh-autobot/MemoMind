# third_party/mnn

把真实的 MNN 预编译产物放到下面结构中：

```text
third_party/mnn/
  include/
    MNN/Interpreter.hpp
    ...
  lib/
    arm64-v8a/libMNN.so
```

当前工程默认只要求 `arm64-v8a`，真机优先。

当前 CMake 会自动探测这套目录：

- 如果存在，将编译 `HAS_REAL_MNN=1` 并链接 `libMNN.so`
- 如果不存在，将继续使用 stub bridge，但上层 API 不需要改

这样可以先把 Android 工程和 JNI 协议跑通，再逐步切到真实 `MNN` 推理。
