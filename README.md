# 简账 Android v2

本地优先的个人记账应用。1.0 是旧版 0.6.1 的底层重构与视觉重做：沿用 `com.jianzhang.app` 与原签名，可覆盖安装并原地迁移旧数据。

## 技术栈

- Kotlin 2.x · Jetpack Compose · Material 3
- Room + SQLCipher（全库加密）
- Hilt · Navigation Compose · DataStore · WorkManager
- 多模块：`app → feature:* → core:*`

## 模块

```text
app/                 入口、导航、启动引导、通知与周期调度
core/
  common/            金额、日期、口令 KDF
  model/             领域模型
  database/          Room 实体、DAO、旧库导入
  data/              仓储、设置、备份加密、密钥管理
  designsystem/      刻度 UI 令牌与组件（含液态玻璃底栏）
  navigation/        类型安全路由
feature/
  overview ledger record stats accounts planning analysis settings lock
```

## 构建

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21'
.\gradlew.bat :app:assembleDebug :app:lintDebug `
  :core:common:testDebugUnitTest :core:data:testDebugUnitTest :core:database:testDebugUnitTest `
  --no-configuration-cache --console=plain
```

发布签名读取根目录 `keystore.properties`；缺失时 `assembleRelease` 直接失败，不会静默使用 debug 签名。
这些文件与 `keystore/`、`local.properties` 均不得提交或外发。

## 安全模型

- 数据库：随机 32 字节口令，Android Keystore AES-GCM 包裹；SQLCipher 全库加密。
- 应用锁：PBKDF2-HMAC-SHA256（310,000 次，16 字节 salt）；旧 `sha256$` 口令在首次成功解锁后自动升级；历史低迭代 PBKDF2 也会自动重哈希。
- 失败锁定：5 次失败锁定 30 秒，失败次数与锁定截止时间持久化，重启不可绕过。
- 锁屏期间启用 `FLAG_SECURE`，最近任务与截屏不显示账目。
- 备份：v3 = PBKDF2 + AES-256-GCM（独立 salt/iv，头部作为 AAD）；兼容 v1/v2 明文备份导入；明文导出需二次确认。
- 网络：仅 `INTERNET` + `POST_NOTIFICATIONS`（另有 WorkManager 合并权限）；`cleartextTrafficPermitted=false`。
- `allowBackup=false`，数据提取规则排除数据库与偏好。

## 文档

- `RELEASE.md`：本次构建产物、校验值、已验证与未验证项。
- `docs/LEGACY-FEATURES.md`：旧版功能逐项对照。
