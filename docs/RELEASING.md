# Android App 发布与版本对齐

`app/build.gradle.kts` 是 App 版本的唯一源头：

- `versionName` 必须使用完整 SemVer，例如 `1.1.6`；
- `versionCode` 必须是正整数，每次对外发布新的 APK 时递增；
- GitHub Release 标签使用 `kemo-v{versionName}`，例如 `kemo-v1.1.6`；版本检查器也兼容
  `v1.1.6` 形式的简写标签。

发布前还必须同步以下可见版本信息：

- `app/src/main/res/values/strings.xml`；
- `app/src/main/res/values-en/strings.xml`；
- `README.md` 的版本徽章和当前版本；
- `README_EN.md` 的版本徽章和当前版本。

## 本地检查

仅检查源码、文档、中英文资源与更新仓库地址：

```powershell
python scripts/release_contract.py --expected-tag kemo-v1.1.6
```

构建并检查 APK 输出元数据：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
python scripts/release_contract.py `
  --expected-tag kemo-v1.1.6 `
  --metadata app/build/outputs/apk/debug/output-metadata.json
```

验证脚本自身的回归测试：

```powershell
python -m unittest discover -s scripts/tests -p "test_*.py" -v
```

## CI 检查范围

常规 Push 和 Pull Request 会检查：

1. `versionName` 是完整 SemVer，`versionCode` 是正整数；
2. 中英文版本文案、README 徽章和版本说明与 Gradle 对齐；
3. 中英文字符串资源键集合一致；
4. App 内更新检查使用的 GitHub 项目地址、Release API 与 README 项目链接一致；
5. Android 单元测试、Lint 和 Debug APK 构建通过；
6. 构建产物的 applicationId、versionName 和 versionCode 与源码一致。

标签 Push 或 GitHub Release 发布时，还会要求标签与 `versionName` 对齐。Release 发布事件会
下载已经挂载到该 Release 的每个 APK，并检查：

- Release 至少包含一个 APK；
- APK ZIP 结构完整；
- APK 签名有效；
- applicationId 为 `com.kesepain.kemoapp`；
- APK 内的 versionName、versionCode 与源码及 Release 标签完全一致。

任意一项不一致都会让 CI 失败，防止出现“源码、关于页、文档、标签和实际 APK 各自显示不同
版本”的发布结果。

已经发布的历史 Release 可以从 GitHub Actions 手动运行此工作流，并在 `release_tag` 输入框
填写标签（例如 `kemo-v1.1.6`）重新核验。留空手动运行时只执行源码、测试、Lint 和本地构建产物检查。
