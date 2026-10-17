# 简账 1.0.3（versionCode 12）发布记录

构建日期：2026-10-17
包名：`com.jianzhang.app`
minSdk：26 · targetSdk：35
签名证书：`CN=JianZhang, OU=HELISEC, O=JianZhang, L=Local, ST=Local, C=CN`
证书 SHA-256：`4f368a27fc384cabdcc34f2e5fa5b82e3528a998f8555601cbc79402ceef0aed`

## 1.0.3 修复：AI 分析闪退

### 现象

进入账本「AI 分析」并开始分析后，应用闪退。

### 根因状态

本次修复时 `adb devices` 无连接设备，未取得崩溃瞬间的 logcat，因此**根因未由堆栈直接确认**。

已确认的高风险路径：

1. 旧实现通过 OkHttp 异步回调 + 顶层 `Call.await()` 扩展完成请求；R8 mapping 中该扩展所属的 `AiAnalyzerKt` 被标记为 `R8$$REMOVED$$CLASS$$549`，存在 release 混淆/内联后回调异常的风险。
2. `buildContext()` 在 ViewModel 的默认调度器上读取数据库，并每次读取全部流水；大账本会造成主线程阻塞、ANR 或内存压力。
3. `observeBudgets(month).first()` 任一失败都会终止整个分析。
4. `AnalysisViewModel` 只捕获 `Exception`；release 下的 `LinkageError` 等会直接崩溃。

### 修复内容

- 移除 AI 调用链中的 OkHttp，改用平台 `HttpURLConnection`，删除顶层 `Call.await()` 扩展。
- 数据库查询与网络请求全部切到 `Dispatchers.IO`。
- 新增范围查询 `TransactionDao.csvRowsRange(start, end)`，只读取所选时间窗口，不再全表加载。
- 新增 `BudgetDao.month(month)` 挂起查询；预算查询失败时降级为空预算，`CancellationException` 继续透传。
- `AnalysisViewModel` 捕获 `Throwable`，写入 `Log.e` 并显示可读错误，不再让分析失败直接杀死进程。
- 新增 `AiAnalysisService` 接口与 Hilt 绑定，隔离 UI 与网络传输，便于替换和测试。
- 新增请求策略：接口地址规范化、temperature/timeout 边界、网络错误友好提示。
- 响应读取上限 512,000 字符，结果上限 32,000 字符，超出部分截断并提示，避免超长响应拖垮 Compose 文本渲染。
- 新增 `CrashReporter`：未捕获异常写入 `filesDir/crash/last-crash.txt` 后再交给系统默认处理器。
- 新增 R8 keep 规则，保留 `AiRequestPolicy` 错误路径辅助类，避免再次被整体移除。
- `SettingsViewModel.saveAi()` 校验 endpoint 与非有限 temperature。

## 产物

| 文件 | 说明 |
|---|---|
| `dist/jianzhang-1.0.3-v12-release.apk` | 已签名、已 R8 压缩的发布包 |
| `dist/mapping-1.0.3-v12.txt` | R8 混淆映射，崩溃回溯用 |
| `dist/SHA256SUMS.txt` | 产物校验值 |

APK SHA-256：

```text
958C710D3D2BE118F42F53169AFF4CCD0E41BF7DBC1E30E733F5724DCC386591
```

APK 大小：9,706,362 字节。
mapping 大小：42,287,021 字节。

## 已验证

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease`（R8 + 资源压缩） | 通过 |
| `:app:lintDebug` | 0 errors / 6 warnings（均为依赖有新版本） |
| 单元测试合计 | 48 tests，0 失败（analysis 4、common 20、data 20、database 4） |
| R8 mapping 检查 | 0 处 `okhttp3`；`AiRequestPolicy` 已保留 |
| `apksigner verify` | 通过，v2 + v3 有效，v1 关闭 |
| 包信息 | `versionCode=12`、`versionName=1.0.3`、`applicationId=com.jianzhang.app` |
| 签名证书 | 与 1.0.0/1.0.1/1.0.2 一致，可覆盖安装 |
| 数据库结构 | 无 schema 变更，覆盖安装保留数据 |

## 未验证

| 项目 | 原因 / 下一步 |
|---|---|
| 真机 AI 分析全流程 | 构建时 `adb devices` 无连接设备；需安装 v12 后实测未配置 Key、错误 Key、正确 Key、超时、取消、切换月份/范围/重点 |
| 旧数据原地迁移 | 需在装过旧版的设备上验收账户、流水、预算、标签、周期规则 |
| 系统通知投递 | 需 API 33+ 真机授权 `POST_NOTIFICATIONS` 后验收 |
| 大账本性能 | 已消除全表读取，但未跑 5 万条流水基准 |

## 安装

```powershell
adb install -r dist\jianzhang-1.0.3-v12-release.apk
```

覆盖安装保留应用数据。卸载会删除本地账本，操作前先导出加密备份。

## 版本历史

- 1.0.3 / versionCode 12：修复 AI 分析闪退高风险路径，移除 OkHttp 回调链，范围查询，Throwable 兜底，响应长度上限，新增崩溃留痕。
- 1.0.2 / versionCode 11：AI 分析传输与数据处理加固（未包含 1.0.3 的最后补丁）。
- 1.0.1 / versionCode 10：修复解锁后 `DatabaseBootstrap` 重复初始化导致的转圈与闪烁。
- 1.0.0 / versionCode 9：底层重构、02 刻度 UI、液态玻璃底栏、全功能迁移。
