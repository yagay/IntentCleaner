# Intentcleaner 1.4.9

包名 `com.yagay.intentcleaner`，版本码23。基于 libxposed API 102 的意图候选过滤模块。不使用 IFW、不修改系统 XML；Root 仅用于用户点击导出时的只读诊断。

本次排序修复及清理项联动见 [CHANGES-1.4.9.md](CHANGES-1.4.9.md)。旧 CHANGES/AUDIT 为历史记录，不代表本版行为。

## 架构

- system：使用 onSystemServerStarting 提供的系统 ClassLoader，在 PackageManager Binder 查询出口过滤。
- android / com.android.intentresolver：选择器客户端兜底及排序。已知 AOSP processSortedList(List, boolean) 处理普通列表/分享推荐区；目标 Intent 从父类 protected getter、字段或 communicator 读取。分享全部应用区在已知 Chooser 的 BaseAdapter 通知前调整 mSortedList，保留调用方专属包的位置；不修改联系人区域。厂商实现不匹配则跳过并记录。
- PROCESS_TEXT：在查询出口对过滤后的候选稳定排序，覆盖不经过 Resolver 的原生选中文字菜单；来源应用后续自行重排的菜单不保证一致。VIEW/SEND 不在系统查询阶段预排，避免干扰默认解析及被推荐算法覆盖。
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

规则页只提供分类、搜索、全部/已选规则/未选规则视图和应用/组件勾选。“已选”指规则勾选，不承诺实际菜单效果，在白名单模式下也不是“已隐藏”的意思。普通列表使用当前扫描结果；已配置但未匹配或属于其他应用非公开组件的规则只在“已选规则”中保留取消入口。分类和搜索仍有效。没有持久化全量历史目录、七天过期策略或历史/受限显示开关；旧版目录缓存不再读取，用户规则不删除。

目录发现保留 MATCH_ALL（文本外同时要求 MATCH_DEFAULT_ONLY），不请求禁用组件。启用状态尊重系统对当前用户的查询结果，不用元数据 enabled 字段再次否决。管理应用缺少某权限只记录为诊断，不代表其他来源也无法使用该组件；非公开跨UID组件仍不列入普通目录。规则列表与排序建议共用管理条件和搜索逻辑，菜单名如 Ask Gemini 可在实际所属应用下被搜到，不按独立App名称猜归属。

更新检测、作用域详情、实际文件检查和导出放在状态页。实际文件检查不使用 MATCH_ALL、不打开文件或读取正文、不记录完整URI，只输出诊断结果，不修改管理目录或规则。查询身份与来源应用不同，不能保证与所有实际菜单一致。

系统确认使用 UID 认证的私有查询返回实际配置摘要；只证明系统查询 Hook，不等于所有选择器效果。已知兼容的19/20/21/22/23版可在严格扫描校验前收到暂停配置，FAILED/RELOADING及未知版本仍不允许；提交不等于生效。诊断导出不等待同步或框架状态查询，保存最近一次观察及时间，再收集原始日志。导出任务由 ViewModel 持有，旋转屏幕不会重新开始；应用被杀后不保证继续导出。

排序页的优先列表、可添加列表和展开组件均按当前分类及规则模式过滤：黑名单隐藏已选组件，白名单保留已选组件，暂停过滤时显示全部匹配组件。应用只清理部分组件时保留其余组件；全部清理后不显示。已保存顺序不因隐藏或扫描缺席而删除，取消清理后恢复。上下移动跳过隐藏项。恢复系统顺序会清空该分类全部排序配置（含暂不显示项）。

排序日志区分 ORDER_CAPABILITY（安装边界）、RULES_READ 的 priorities/digest（该进程读到配置）、ORDER_RESULT 的 matched/changed（计算结果）、ORDER_DELIVERED（交给显示边界但未验证画面）及 ORDER_SKIP/ORDER_FAILED。这些不是最终 UI 验证。保存/恢复顺序后重新打开菜单；热重载重新发现排序入口时尝试恢复宿主 APK ClassLoader，未知实现仍需重启或进一步适配。

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
