# Release 签名与发布

正式版本使用固定的 RSA-3072 / PKCS12 密钥，并启用 APK v2/v3 签名。
仓库只保存公开证书的 SHA-256（`signing/release-certificate.sha256`），私钥和密码不提交。

## GitHub 一次性配置

在仓库 Settings → Secrets and variables → Actions 新增 Repository secret：

- 名称：`ANDROID_SIGNING_JSON`
- 值：签名备份包中 `ANDROID_SIGNING_JSON.txt` 的全部内容。

该 JSON 包含 `keystore_base64`、`store_password`、`key_alias`、`key_password`。
不要把 JSON、密钥或备份包提交到源码、Issue、Actions artifacts 或 Releases。
请长期保管备份；不要重新生成密钥来替代已发布版本使用的密钥。

配置完成后，进入 Actions → Build and Publish Release → Run workflow，选择 `main`。
也可以重跑此前因缺少 Secret 而失败的运行。工作流会执行 Release 编译、签名校验、
对齐校验和包身份检查，再上传 APK、SHA256SUMS.txt、signature.txt 到 GitHub Releases。
签名证书必须与仓库记录匹配。缺少 Secret 时只验证未签名的 Release 编译，随后停止；
不会发布未签名包或使用 Debug 密钥代替。

修改 `app/build.gradle.kts` 中的版本号、发布工作流或签名配置后也会触发发布流程。
发布新版本前同时递增 `versionCode` 和 `versionName`。已存在的版本不会被覆盖。

## 本地构建

将备份中的 `keystore.properties` 放在项目根目录，把 `storeFile` 改为密钥的绝对路径：

```properties
storeFile=/absolute/path/to/ListCleaner-release.p12
storePassword=your-private-password
keyAlias=listcleaner
keyPassword=your-private-password
storeType=PKCS12
```

执行 `./gradlew :app:assembleRelease`，输出为 `app/build/outputs/apk/release/app-release.apk`。
也支持同名用途的 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、
`RELEASE_KEY_PASSWORD`、`RELEASE_STORE_TYPE` 环境变量，环境变量优先。
没有签名配置时正式构建报错。仅编译检查可显式传入 `-PallowUnsignedRelease=true`；
得到的未签名包不能用于安装或发布。

## 从 Debug 版迁移

新固定 Release 证书通常与旧 Debug 证书不同，因此首次切换可能无法直接覆盖安装。
先在 App 内导出规则备份。后续始终使用同一 Release 签名即可正常覆盖升级。
