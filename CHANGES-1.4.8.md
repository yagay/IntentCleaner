# ListCleaner 1.4.8 (22)

## 原因与修复范围

旧诊断的文本扫描返回 Google 的 ProcessTextGatewayActivity，却被本模块标记“未启用”。这证明目录存在重复否决风险；旧记录不能证明当前手机的菜单来源或动态设置。此版本修复通用判定，不给 Gemini 单独加白名单。

- IntentCatalog：删除 ActivityInfo.enabled / ApplicationInfo.enabled 作为候选剔除条件。目录与文件查询均不请求 MATCH_DISABLED_COMPONENTS / MATCH_DISABLED_UNTIL_USED_COMPONENTS，并在查询前检查这一约束。有效启用状态交由 PackageManager 按用户及动态覆盖设置处理；元数据默认值只保留为诊断信息。
- 权限：checkSelfPermission 只反映管理App，不代表选择文字、分享文件的来源App。拒绝或检查失败只记录 managerGranted=false/unknown，不排除公共候选，不请求新权限、不强制启用、不启动组件。
- 非公开保护：FilterPolicy.catalogRestricted 只保留未导出且非管理App同UID的限制。同包不同用户资料不视为同UID；未知UID不会被错误认作同一应用。
- ResolverScopeDetector：已安装检测只判断存在；实际宿主只接受系统解析到的可信系统Resolver组件，不再用原始enabled字段否决动态启用的Activity或别名。PROCESS_TEXT探针不强加DEFAULT类别。没有扩大推荐作用域。
- Rule/MainViewModel/PriorityDialog：共用 isCatalogCandidate 和 matchesQuery。应用名、菜单/组件标签、包名、类名的搜索保持一致；修复排序待添加列表只搜App名而搜不到 Ask Gemini 这类菜单标签的问题。
- 文件检查文案区分“符合管理目录条件”和“实际可启动”。排序规则只描述期望行为，不把保存的隐藏规则断言为已经实际隐藏。
- 诊断：记录查询flags、原始enabled元数据、exported、UID及管理App权限结果；它们不再混成一个“未启用/受限”结论。

## 全量检查后保留

Hook过滤、Intent分类、白名单/黑名单、规则读写、排序算法中未发现另一份enabled/管理权限否决逻辑。继续保留管理查询豁免、真实调用方同UID保护、显式目标放行、非文本空结果保护、未知类型放行、配置摘要确认和旧模块检查。没有重新引入历史目录或显示受限候选开关。

## 验证

- Java检查新增公开/未公开、同UID/跨用户/未知UID管理策略和版本22协议边界。
- Kotlin用例覆盖五类候选不因诊断元数据/管理权限文字隐藏、菜单标签跨页面搜索、所有查询flags不包含禁用/未安装选项。
- Java及源码引用检查不等于Android构建或真机验证；本地仍需运行 testDebugUnitTest 并编译APK。

## 升级验收

覆盖安装，状态页应用模块更新（失败则重启），确认运行版本22后刷新。无需清数据。文本分类可搜索“Gemini”或展开“Google”；是否有该项仍取决于当前系统实际返回的组件，不保证自定义/硬编码菜单都走PROCESS_TEXT。

## 系统依据

- [AOSP PackageUserStateUtils](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/pkg/PackageUserStateUtils.java)：动态启用/禁用覆盖优先于清单默认值；查询是否匹配禁用组件由flags决定。
- [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)：MATCH_ALL与MATCH_DISABLED_COMPONENTS为不同选项；组件启用设置可独立于清单默认值。
