# Lumir

Lumir 是 Lumic 自托管服务的 Android 客户端。客户端启动时显示 Lumic 图标，输入 Lumic 服务器地址、账号和密码后，通过 WebView 使用 Docker 服务中已经完成的界面与功能。

## 本地构建

使用 Android Studio 打开本目录，安装 JDK 17 和 Android SDK 35，等待 Gradle 同步完成，然后执行：

```powershell
gradle assembleDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`

也可以在 Android Studio 中点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。

## 推送到 GitHub

先在 GitHub 创建一个空仓库，例如 `Lumir`，不要勾选自动创建 README、`.gitignore` 或 License。然后在本目录执行：

```powershell
git init
git add .
git commit -m "Initial Lumir Android client"
git branch -M main
git remote add origin https://github.com/<你的用户名>/Lumir.git
git push -u origin main
```

推送后打开 GitHub 仓库的 `Actions` 页面，等待 `Build Android APK` 工作流完成。在工作流详情页底部的 `Artifacts` 中下载 `lumir-debug-apk`。

## 正式签名与自动创建 GitHub Release

正式发布不能使用 debug 签名。项目中的 [`release.yml`](.github/workflows/release.yml) 已配置为：推送 `v*` 标签后，自动构建签名 APK 并创建 GitHub Release；也可以在 Actions 页面手动运行。

### 1. 只在本地生成一次发布密钥

请在 Windows PowerShell 中执行，密码自行设置并妥善保存：

```powershell
keytool -genkeypair -v -keystore lumir-release.jks -alias lumir -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("lumir-release.jks")) | Set-Content -NoNewline release-keystore.b64
```

不要把 `lumir-release.jks`、`release-keystore.b64` 或任何密码提交到 Git。项目的 [`.gitignore`](.gitignore:12) 已排除这些文件。

### 2. 配置 GitHub Actions Secrets

打开 `Hyaeve/lumir > Settings > Secrets and variables > Actions > New repository secret`，逐项创建以下 4 个 **Repository secrets**：

| Secret 名称 | 填写内容 |
|---|---|
| `LUMIR_KEYSTORE_BASE64` | `release-keystore.b64` 文件的完整单行内容 |
| `LUMIR_KEYSTORE_PASSWORD` | 创建 keystore 时输入的密码 |
| `LUMIR_KEY_ALIAS` | `lumir` |
| `LUMIR_KEY_PASSWORD` | 创建 key 时输入的密码 |

GitHub Secret 保存后无法再次查看原文；如果输错，只能重新更新该 Secret。不要使用 Environment secret，除非你同时修改工作流的环境配置。

### 3. 创建正式版本

先在本地递增 [`versionCode`](app/build.gradle.kts:32) 和 [`versionName`](app/build.gradle.kts:33)，然后执行：

```powershell
git add .
git commit -m "发布 Lumir v1.0.0"
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

推送标签后，打开仓库的 `Actions > 发布 Lumir 正式版本`。成功后，打开 `Releases`，即可看到 `Lumir v1.0.0` 和签名的 `app-release.apk`。

也可以不推送标签，进入 `Actions > 发布 Lumir 正式版本 > Run workflow`，输入标签（例如 `v1.0.0`）后运行。

### 4. 重要安全事项

- 永久备份 `lumir-release.jks`、keystore 密码和 key 密码。
- 丢失发布密钥后，后续版本无法覆盖升级已经发布的应用。
- 不要把签名文件、Base64 内容或密码写进仓库、Issue、日志或聊天记录。
- `build-apk.yml` 仍用于普通 debug 构建；正式发布使用 `release.yml`。

如果使用 SSH：

```powershell
git remote add origin git@github.com:<你的用户名>/Lumir.git
git push -u origin main
```

`lumic-logo.png` 已作为应用启动图和图标使用。Actions 中的 `lumir-debug-apk` 仅适合测试；正式版本由 `release.yml` 自动创建并上传签名的 `app-release.apk`。Google Play 通常应上传签名的 AAB，可将正式发布工作流中的 `assembleRelease` 改为 `bundleRelease` 并上传 `app-release.aab`。
