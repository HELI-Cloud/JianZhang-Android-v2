# 简账 1.0.4（versionCode 13）发布记录

构建日期：2026-10-19
包名：`com.jianzhang.app`
minSdk：26 · targetSdk：35
签名证书：`CN=JianZhang, OU=HELISEC, O=JianZhang, L=Local, ST=Local, C=CN`
证书 SHA-256：`4f368a27fc384cabdcc34f2e5fa5b82e3528a998f8555601cbc79402ceef0aed`

## 1.0.4 修复：首页刻度排版与通知可靠性

### 首页刻度排版

- 新增 `Money.formatAxis()`：一万元以下显示取整到元的千分位金额，一万元以上切换为 `1万` / `12.3万` / `1亿`，避免长金额挤爆刻度行。
- 首页预算刻度由 5 个完整金额改为 3 个短标签：`0`、预算中点、预算总额。
- `GaugeScale` 改为等权布局：首项左对齐、中项居中、末项右对齐，单行、禁用换行、超出省略。
- 刻度文字改为 11sp 等宽字体、`ink2` 对比度，并增加 2dp 顶部间距。
- 页面标题、分区标题、金额、分类预算、分类明细、最近流水全部补齐单行省略与横向页面边距。
- 「已用 / 已超支」与「日均可用」改为左右等权布局，避免窄屏碰撞。
- 分类预算行改为 `30% 名称 / 42% 刻度 / 28% 金额` 权重布局，金额右对齐并使用紧凑金额。
- 百分比改为四舍五入，消除 `99.999%` 一类异常显示。

### 通知与提醒

- Android 13+ 同时检查 `POST_NOTIFICATIONS` 运行时权限和系统通知总开关，权限不足时不再假装发送成功。
- 通知渠道升级为 `jianzhang-alerts` 与 `jianzhang-cadence-v2`；每日/每周渠道由 `IMPORTANCE_LOW` 提升为 `IMPORTANCE_DEFAULT`，并删除旧 `jianzhang-cadence`。
- 通知增加提醒分类、私密锁屏可见性、时间戳、排序键、自动取消和点击深链。
- 同一提醒 key 当天只发送一次；跨天自动重新提醒；旧状态自动清理，避免 SharedPreferences 无限增长。
- 每日提醒从 WorkManager 延迟任务改为 `AlarmManager` 墙钟闹钟；Android 12+ 优先 `setExactAndAllowWhileIdle`，无精准闹钟权限时降级 `setAndAllowWhileIdle`。
- 新增 `ReminderAlarmReceiver` 与 `ReminderBootReceiver`：开机、改时间、改时区、应用升级、精准闹钟权限变化后自动恢复调度。
- 提醒页在 `ON_RESUME` 实时刷新通知权限与精准提醒权限；未授权时显示明确状态卡片，并提供系统设置入口。
- 打开「系统通知」开关时，Android 13+ 立即请求通知权限。
- 通知调度与 `AppViewModel` 同步链路增加异常兜底，避免单个 OEM 限制导致进程崩溃。

## 产物

| 文件 | 说明 |
|---|---|
| `dist/jianzhang-1.0.4-v13-release.apk` | 已签名、已 R8 压缩的发布包 |
| `dist/mapping-1.0.4-v13.txt` | R8 混淆映射，崩溃回溯用 |
| `dist/SHA256SUMS.txt` | 产物校验值 |

APK SHA-256：

```text
25E2D3108ACC36AB54F9F2ED7589BD3D5F2D83433393873E454DF51C24EC8067
```

APK 大小：9,706,550 字节。
mapping 大小：42,446,480 字节。

## 已验证

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease`（R8 + 资源压缩） | 通过 |
| `:app:lintDebug` | 0 errors / 6 warnings（均为依赖有新版本） |
| 单元测试合计 | 49 tests，0 失败（analysis、common、data、database） |
| `apksigner verify` | 通过，v2 + v3 有效，v1 关闭 |
| 包信息 | `versionCode=13`、`versionName=1.0.4`、`applicationId=com.jianzhang.app` |
| 签名证书 | 与 1.0.0—1.0.3 一致，可覆盖安装 |
| R8 mapping | 保留 `MainActivity`、`NotificationCenter`、`ReminderAlarmReceiver`、`ReminderBootReceiver`、两个 Worker |
| 数据库结构 | 无 schema 变更，覆盖安装保留数据 |

## 未验证

| 项目 | 原因 / 下一步 |
|---|---|
| 首页刻度真机观感 | 构建时无连接设备；需在 320dp / 360dp / 412dp 宽度检查不换行、不重叠、不截断 |
| 通知实际投递 | 需 API 33+ 真机授权通知权限，验证锁屏、后台、省电模式 |
| 精准提醒 | 需 Android 12+ 真机授予「闹钟和提醒」权限后验证准时性 |
| 开机恢复 | 需真机重启后验证 `BOOT_COMPLETED` 重建闹钟 |
| 旧数据原地升级 | 需在装过 1.0.3 的设备上覆盖安装验收账户、流水、预算、周期规则 |
| 国产 ROM 后台限制 | 小米/华为/OPPO/vivo 需单独验证自启动与后台弹窗权限 |

## 安装

```powershell
adb install -r dist\jianzhang-1.0.4-v13-release.apk
```

覆盖安装保留应用数据。卸载会删除本地账本，操作前先导出加密备份。

## 版本历史

- 1.0.4 / versionCode 13：修复首页刻度文字挤压与窄屏排版；通知权限、渠道、去重、精准闹钟、开机恢复全面加固。
- 1.0.3 / versionCode 12：修复 AI 分析闪退高风险路径，移除 OkHttp 回调链，范围查询，Throwable 兜底，响应长度上限，新增崩溃留痕。
- 1.0.2 / versionCode 11：AI 分析传输与数据处理加固。
- 1.0.1 / versionCode 10：修复解锁后 `DatabaseBootstrap` 重复初始化导致的转圈与闪烁。
- 1.0.0 / versionCode 9：底层重构、02 刻度 UI、液态玻璃底栏、全功能迁移。
