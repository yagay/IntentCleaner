# Intentcleaner 1.4.5

包名 `com.yagay.intentcleaner`，版本码19。基于 libxposed API 102 的意图候选过滤模块。不使用 IFW、不修改系统 XML；Root 仅用于用户点击导出时的只读诊断。

本次运行版本保护、配置恢复、系统确认及热重载见 [CHANGES-1.4.5.md](CHANGES-1.4.5.md)。旧 CHANGES/AUDIT 为历史记录，不代表本版行为。

## 架构

- system：使用 onSystemServerStarting 提供的系统 ClassLoader，在 PackageManager Binder 查询出口过滤。
- android / com.android.intentresolver：选择器客户端兜底及排序；额外探测 AOSP processSortedList(List, boolean) 排名结束入口，避免置顶被后续排名覆盖。厂商实现不匹配则跳过。
- 扫描和Hook共用分类：SEND、SEND_MULTIPLE、PROCESS_TEXT分别对应三类；VIEW的content+MIME、file、无scheme但带MIME归打开方式。HTTP/HTTPS无类型或HTML归浏览器，明确文件MIME归打开方式。自定义协议、无MIME的content、联系人cursor类型不分类，保持原行为。支持selector和系统resolvedType。
- 显式 component/package 放行；管理身份通过框架远端配置同步，不依赖启动时的ApplicationInfo。身份未知时系统查询暂时不作过滤，打开本模块同步后恢复；其余调用方同应用保护仍按完整UID。
- PROCESS_TEXT 允许隐藏全部候选；其他分类保留空列表恢复保护，触发时记录 RESTORE_ALL。
- 未勾选的分类暂时显示全部。排序不插入缺席目标，不跨用户资料位置移动目标。
- 不拦截显式启动、不删除硬编码菜单、不清除其他应用缓存、不接管默认应用。扫描只覆盖当前用户可见组件和示例 MIME，不承诺穷举。

## 安装与验证

1. 先导出 JSON 备份。本地编译后使用原签名覆盖安装。
2. 授权 system、android 及实际使用的 com.android.intentresolver，不要勾选所有应用。
3. **首次从 1.4.4 或更早版本升级必须重启一次。** 后续版本支持 API102 热重载尝试，可点“检测并应用模块更新”；框架不支持或失败时仍需重启。
4. 如提示远程配置恢复，明确选择恢复或重置。运行版本与系统配置摘要核实后才扫描；旧代码或未确认配置不会覆盖当前管理列表。
5. 开启诊断，按审查报告一次复现五类场景，最后只导出一个 ZIP。

普通扫描列表不混入仅宽泛探针命中的候选；可打开“显示高级候选”。历史候选与已配置项保留管理入口，匹配未确认时明确标记，分类、搜索及视图过滤照常生效。“用实际文件检查打开方式”只获取MIME并查询候选，不打开目标、不读取正文、不保存完整URI。以本模块身份查询不保证和所有来源应用的菜单完全相同；后续刷新未命中的历史项不代表仍是有效打开目标。

系统确认使用 UID 认证的私有查询返回实际配置摘要；只证明系统查询 Hook，不等于所有选择器效果。界面会明确显示旧代状态与更新结果。导出任务由 ViewModel 持有，旋转屏幕不会重新开始；应用被杀后不保证继续导出。

## 日志与隐私

API102日志和Logcat同时记录。管理扫描不消耗调用栈额度；其他查询按层/分类/调用UID记录首次非显式查询栈（最多约128个键）。每进程每5秒关键记录最多200条、候选明细另限40条，超量计数在下个活跃窗口报告。

ZIP 包含作用域、运行目标、规则、系统信息、相关 Logcat、事件、崩溃、进程、安装信息，以及最近24小时内修改的最新12份 LSPosed .log（每份最多4 MiB）。普通输出最多8 MiB，超限保留开头和最新结尾；每条命令超时20秒，记录退出码、时间、截断及读取完整性。

analysis/module-evidence.txt去重并按进程/PID/分类统计历史观察，事件数量不是活跃Hook数量。新增scan-probes、scan-candidates、real-file-probe记录扫描依据和查询统计。日志实际保留时间由系统与LSPosed轮转决定，已覆盖内容不能恢复。模块不主动记录正文或完整URI，但原始系统日志可能包含隐私。

## 本地环境

保留原构建配置：Gradle 9.4.1、AGP 9.2.0、Compile/Target SDK37、Min SDK31、Java/JVM17、libxposed API/Service102.0.0。SDK、网络依赖、签名及插件兼容配置由本地环境处理，不附带 local.properties 或私钥。

```shell
./gradlew testDebugUnitTest assembleDebug
```

无需 Android SDK 的部分检查：

```shell
javac -d /tmp/intentcleaner-check app/src/main/java/com/yagay/intentcleaner/ui/DiagnosticBuffer.java app/src/main/java/com/yagay/intentcleaner/ui/DiagnosticEvidence.java app/src/main/java/com/yagay/intentcleaner/domain/FilterPolicy.java app/src/main/java/com/yagay/intentcleaner/domain/IntentClassification.java app/src/main/java/com/yagay/intentcleaner/domain/ManagerIdentity.java tools/DiagnosticBufferCheck.java tools/FilterPolicyCheck.java tools/ClassificationCheck.java tools/DiagnosticEvidenceCheck.java
java -cp /tmp/intentcleaner-check DiagnosticBufferCheck
java -cp /tmp/intentcleaner-check FilterPolicyCheck
java -cp /tmp/intentcleaner-check ClassificationCheck
java -cp /tmp/intentcleaner-check DiagnosticEvidenceCheck
javac -d /tmp/intentcleaner-check app/src/main/java/com/yagay/intentcleaner/domain/RuntimeProtocol.java tools/RuntimeProtocolCheck.java
java -cp /tmp/intentcleaner-check RuntimeProtocolCheck
```
