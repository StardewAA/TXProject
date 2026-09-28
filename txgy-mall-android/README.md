# 防腐保温商城 Android 容器

包名 `cn.txgy.mall`。这是现有移动 H5 的原生 WebView 容器，老师可以直接使用 PC 下载页随附的签名 APK；商城地址在安装后选择，不需要为每台电脑重新打包。

完整的安装、证书、构建和验证说明见 [Android 交付记录](../docs/mall-android-signing-20260924.md)。

维护者使用 Windows JDK 17+ / Android SDK platform 35、build-tools 35.0.0：

```powershell
..\txgy-mall-uniapp-master\scripts\prepare-android-signing.ps1
.\build.ps1 -SdkRoot '你的Android SDK目录' -Publish
```

不带 `-Publish` 只生成本地构建，不更改下载页的发布文件。私钥不在仓库中；新建证书不能覆盖升级已有不同签名的安装包。
