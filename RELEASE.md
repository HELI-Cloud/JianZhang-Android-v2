# 简账 1.0.0（versionCode 9）发布记录

构建日期：2026-10-17
包名：`com.jianzhang.app`
minSdk：26 · targetSdk：35
签名证书：`CN=JianZhang, OU=HELISEC, O=JianZhang, L=Local, ST=Local, C=CN`
证书 SHA-256：`4f368a27fc384cabdcc34f2e5fa5b82e3528a998f8555601cbc79402ceef0aed`

## 产物

| 文件 | 说明 |
|---|---|
| `dist/jianzhang-1.0.0-v9-release.apk` | 已签名、已 R8 压缩的发布包 |
| `dist/mapping-1.0.0-v9.txt` | R8 混淆映射，崩溃回溯用 |
| `dist/SHA256SUMS.txt` | 产物校验值 |

APK SHA-256：

```text
39af8351d50cd523576b46d9d591bbfb700f14dc35f451955fd93d9cf8185340
```

## 1.0 范围

- 新底层：多模块（`app → feature:* → core:*`）、Hilt、Room + SQLCipher、Paging 3、DataStore、WorkManager。
- 旧功能等价：记账、账户/转账、流水与批量操作、预算、周期记账、标签、提醒、统计、AI 解读、备份导入导出、应用锁、设置、关于。
- 新 UI：固定「02 刻度」体系，石墨底 + 琥珀强调 + 零圆角面板 + 液态玻璃底栏；移除看门狗 HUD、青色黑客风、切角、扫描线。
- 流水分页：60/页，首屏 120，不再整月一次性载入；标签独立筛选。
- 图表：概览近 6 月双折线 + 分类环形图；统计年度柱状、日折线、分类排行均支持点按读数。
- 安全：5 次失败锁定持久化，重启不可绕过；修改密码、关闭应用锁与解锁共用同一 guard。
- 备份：v3 加密格式；兼容 v1/v2 导入；明文导出需二次确认。

## 已验证

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease`（R8 + 资源压缩） | 通过 |
| `:app:lintDebug` | 通过，0 errors / 6 warnings（均为依赖有新版本） |
| `:core:common:testDebugUnitTest` | 通过 |
| `:core:data:testDebugUnitTest` | 通过 |
| `:core:database:testDebugUnitTest` | 通过（含分页、标签筛选、旧库导入） |
| `apksigner verify` | 通过，v2 + v3 方案有效（minSdk 26） |
| 数据库结构 | Room version 4，本次无迁移变更 |

## 未验证（发布前必须补）

| 项目 | 原因 / 下一步 |
|---|---|
| 真机安装与 0.6.1 覆盖升级 | 当前无连接设备；接机后执行 `adb install -r dist/jianzhang-1.0.0-v9-release.apk` |
| 旧数据原地迁移 | 需在装过旧版的设备上验收账户、流水、预算、标签、周期规则 |
| 系统通知投递 | 需 API 33+ 真机授权 `POST_NOTIFICATIONS` 后验收 |
| API 26 / 30 / 35 矩阵 | 需模拟器或真机；重点看底栏 inset、字号 0.85/1.3、键盘弹出 |
| 5 万条流水性能 | 分页单测覆盖 130 条；未跑 5 万条基准 |

## 已知限制

- 生物识别未启用（旧版同样只有未接线字段），列为 1.1 候选。
- `LedgerRepository` 仍为单文件聚合仓储（约 1.3k 行），计划 1.1 拆分为 transaction/account/budget/tag/planning/backup/settings 子仓储，并以 facade 保持调用方兼容。
- 图表仅支持点按单点读数，不支持拖拽扫读。

## 安装

```powershell
adb install -r dist\jianzhang-1.0.0-v9-release.apk
```

覆盖安装会保留应用数据。卸载会删除本地账本，操作前先导出加密备份。
