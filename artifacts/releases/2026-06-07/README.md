# MemoMind APK Artifact

This directory contains a split debug APK artifact because the full APK exceeds GitHub single-file size limits.

Rebuild locally:
```bash
cat MemoMind-debug-2026-06-07.apk.part-* > MemoMind-debug-2026-06-07.apk
shasum -a 256 -c MemoMind-debug-2026-06-07.apk.sha256
```
