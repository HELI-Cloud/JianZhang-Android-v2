# 简账 1.0.1（versionCode 10）发布记录

构建日期：2026-10-17
包名：`com.jianzhang.app`
minSdk：26 · targetSdk：35
签名证书：`CN=JianZhang, OU=HELISEC, O=JianZhang, L=Local, ST=Local, C=CN`
证书 SHA-256：`4f368a27fc384cabdcc34f2e5fa5b82e3528a998f8555601cbc79402ceef0aed`

## 1.0.1 修复

### 现象

输入应用锁密码后持续转圈，界面在启动页与账本之间反复闪烁，无法进入主界面。

### 根因

`core/data/.../DatabaseBootstrap.kt` 的启动逻辑不幂等：

1. 每个 `LedgerRepository` Flow 被收集时都会调用 `bootstrap.await()`。
2. `await()` 每次都调用 `start()`。
3. `start()` 只用 `job?.isActive` 判断；首次初始化完成后 job 已结束，于是再次启动 `initialize()`。
4. `initialize()` 把状态重置为 `Loading`，根界面切回启动页并销毁 NavHost。
5. 初始化完成回到 `Ready`，NavHost 重建并再次收集 Flow，再次触发初始化。

形成 `Loading → Ready → Loading` 的无限循环，表现为持续转圈与整屏闪烁。

### 修复

- 新增 `BootstrapStartGate`：同一进程内只允许一次初始化，并发调用只有一个赢家。
- `await()` 在数据库已打开时直接返回实例，不再触碰启动状态。
- `initialize()` 在锁内二次检查实例，天然幂等。
- `retry()` 仅在初始化失败且当前没有运行中的任务时重置 gate 并重试；已就绪的数据库不会被关闭或重开。
- 初始化被取消时正确抛出 `CancellationException`，不再误记为 `Failed`。
- 不再把重新初始化产生的 `Loading` 推给 UI。

## 产物

| 文件 | 说明 |
|---|---|
| `dist/jianzhang-1.0.1-v10-release.apk` | 已签名、已 R8 压缩的发布包 |
| `dist/mapping-1.0.1-v10.txt` | R8 混淆映射，崩溃回溯用 |
| `dist/SHA256SUMS.txt` | 产物校验值 |

APK SHA-256：

```text
5fd0eaf38be51f4ddc73320222015cc7a6d2d3a15e915831ef958c328d5cd63f
```

APK 大小：9,788,282 字节。

## 已验证

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease`（R8 + 资源压缩） | 通过 |
| `:app:lintDebug` | 0 errors / 6 warnings（均为依赖有新版本） |
| `:core:common:testDebugUnitTest` | 20 tests，0 失败 |
| `:core:data:testDebugUnitTest` | 20 tests，0 失败（含新增 `BootstrapStartGateTest` 3 项） |
| `:core:database:testDebugUnitTest` | 4 tests，0 失败 |
| `apksigner verify` | 通过，v2 + v3 有效，v1 关闭 |
| 包信息 | `versionCode=10`、`versionName=1.0.1`、`applicationId=com.jianzhang.app` |
| 数据库结构 | Room version 4，无迁移变更 |
| 签名证书 | 与 1.0.0 一致，可覆盖安装 |

## 未验证（发布前必须补）

| 项目 | 原因 / 下一步 |
|---|---|
| 真机解锁流程 | 构建时 `adb devices` 无连接设备；需安装后实测“开锁 → 进入概览”不再闪烁 |
| 旧数据原地迁移 | 需在装过旧版的设备上验收账户、流水、预算、标签、周期规则 |
| 系统通知投递 | 需 API 33+ 真机授权 `POST_NOTIFICATIONS` 后验收 |
| API 26 / 30 / 35 矩阵 | 需模拟器或真机；重点看底栏 inset、字号 0.85/1.3、键盘弹出 |
| 5 万条流水性能 | 分页单测覆盖有限条数，未跑 5 万条基准 |

## 安装

```powershell
adb install -r dist\jianzhang-1.0.1-v10-release.apk
```

覆盖安装保留应用数据。卸载会删除本地账本，操作前先导出加密备份。

## 版本历史

- 1.0.1 / versionCode 10：修复解锁后 `DatabaseBootstrap` 重复初始化导致的转圈与闪烁。
- 1.0.0 / versionCode 9：底层重构、02 刻度 UI、液态玻璃底栏、全功能迁移。