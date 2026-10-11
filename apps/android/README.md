# MetaCubeXD Mobile（安卓面板）

这是面向 `xx025/metacubexd-mobile` 的安卓外壳。只连接用户已有的 Mihomo
控制端，不附带内核、不申请 VPN 权限。最低 Android 8.0，使用系统 Android
WebView；APK 不含原生架构库，因此适用于 arm64、armv7 和 x86_64。

`1.273.1-mobile.2` 将文字缩放调为原来的 85%，并保留 Android 系统字体缩放设置。

面板和字体打包在 APK 内。后端地址、Secret 和面板设置保存在本应用私有的
WebView 存储中，独立于 Chrome 等浏览器。应用升级保留数据，但卸载或清除本应用
数据会删除这些记录。自动云备份已关闭。

## 连接路由器

输入路由器的 Mihomo 控制地址，例如 `http://192.168.1.1:9090`，以及它的 Secret。
手机需要能访问这个地址。支持 HTTP/HTTPS REST 和 WS/WSS 实时连接。
如果路由器限制面板来源，允许 `http://localhost`：

```yaml
external-controller-cors:
  allow-origins:
    - http://localhost
```

localhost 是应用内面板的固定来源，控制地址应填路由器 IP，而不是手机 localhost。
HTTPS 证书仍需由系统信任；本应用不会绕过证书错误。

## 本地构建

需要 JDK 17、Python 3、Node.js 24、pnpm 10.34.1 和 Android SDK。
无需 Android Studio 或 Gradle。使用官方 aapt2 / d8 / zipalign / apksigner 构建。

```bash
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
pnpm install --frozen-lockfile
MCXD_DISABLE_PWA=true pnpm build:ui
export ANDROID_HOME=/path/to/android-sdk
bash apps/android/build-apk.sh
```

APK 位于 `apps/android/build/MetaCubeXD-Mobile.apk`。PWA 必须在此次构建中关闭，
应用内页面无需 Service Worker。未改动任何上游面板功能或代理内核代码。

首次本地构建自动生成个人签名密钥及随机密码，放在
`apps/android/build/signing/`。请保存此目录；以后使用相同包名和密钥才能覆盖安装。
新版本应递增 `AndroidManifest.xml` 的 `versionCode`，并更新 `versionName`。
可通过 `MCXD_KEYSTORE` 和 `MCXD_PASSWORD_FILE` 指定已有签名文件。
签名文件和密码已排除于 Git，不应提交到仓库。

## GitHub Actions

新增的 Android APK 工作流可手动运行。先设置仓库 Actions Secrets：

- `ANDROID_KEYSTORE_BASE64`：上述 mobile.jks 文件的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：mobile.password 文件里的密码。

工作流生成 APK 下载附件，不自动发布 Release。使用相同的密钥才能覆盖安装
已经交付的 APK；密钥别名为 `mobile`。工作流源码及签名备份分别交付。

## 验证

此次构建基于上游提交 `8bbc8f58fef71148a94fb5c0ff808f79b057337d`。
APK 使用个人构建证书签名，区别于 MetaCubeX 官方发行版。
测试范围及结果见交付的安装说明。真实路由器连接仍取决于其地址、Secret、
网络可达性和 CORS 配置。
