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

如果使用 SSH：

```powershell
git remote add origin git@github.com:<你的用户名>/Lumir.git
git push -u origin main
```

注意：该 APK 是 debug 包，仅适合测试安装。正式发布到 GitHub Releases 或 Google Play 前，应配置签名密钥并构建 release 包。
