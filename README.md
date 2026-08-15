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

## 正式发布签名

正式发布不能使用 debug 签名。请在本地安全生成一次发布密钥，并且不要把密钥文件或密码提交到 Git：

```powershell
keytool -genkeypair -v -keystore lumir-release.jks -alias lumir -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("lumir-release.jks")) | Set-Content release-keystore.b64
```

在 GitHub 仓库的 `Settings > Secrets and variables > Actions` 中添加以下 Repository secrets：

- `LUMIR_KEYSTORE_BASE64`：`release-keystore.b64` 的完整内容
- `LUMIR_KEYSTORE_PASSWORD`：生成密钥时设置的 keystore 密码
- `LUMIR_KEY_ALIAS`：上例为 `lumir`
- `LUMIR_KEY_PASSWORD`：生成密钥时设置的 key 密码

工作流会把密钥仅写入 GitHub Runner 的临时目录，执行 `assembleRelease` 并上传签名后的 `app-release.apk`。未配置全部 secrets 时仍会构建 debug 包，但会跳过 release 包。

请永久、安全地备份 `lumir-release.jks` 和密码。密钥一旦丢失，已经发布的应用将无法用新密钥直接升级。不要把 `.jks`、`release-keystore.b64` 或密码提交到仓库。

如果使用 SSH：

```powershell
git remote add origin git@github.com:<你的用户名>/Lumir.git
git push -u origin main
```

`lumic-logo.png` 已作为应用启动图和图标使用。Actions 中的 `lumir-debug-apk` 仅适合测试；配置上述 secrets 后，`lumir-release-apk` 是可上传 GitHub Releases 的正式签名包。Google Play 通常应上传签名的 AAB，可在发布时使用 `gradle bundleRelease` 生成。
