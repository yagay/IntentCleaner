# 1.4.3 集中审查与验收

## 依据

已读全部业务源码：模块、仓库、扫描、作用域、备份、所有 UI、诊断，以及 Manifest、模块元数据、R8、构建配置和现有测试；结合07:42:43诊断包及此前结论。未连接手机，未执行完整 Android 构建。

日志确认：system加载版本16、规则5条读取成功，但系统 Hook 总数0。同进程其他模块已取得 IPackageManagerImpl，不能解释为系统不存在该类。Resolver安装4个查询 Hook 并捕获 SEND；此包没有模块 VIEW/PROCESS_TEXT/SEND_MULTIPLE 查询记录。

官方参考：
- [系统生命周期与热重载](https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html)
- [系统 ClassLoader](https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.SystemServerStartingParam.html)
- [日志和模块 ApplicationInfo](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html)
- [拦截链参数与 proceed](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Chain.html)

## 集中修复

| 问题 | 处理 |
| --- | --- |
| 系统类使用其他 APK 加载器查找 | 专用 onSystemServerStarting 入口，使用其系统 ClassLoader |
| 自动热重载声明与实现不符 | 关闭自动热重载、最低 API 102；更新要求重启 |
| 查询方法筛选过宽/遗漏继承方法 | 精确查询名称及返回类型，系统入口扫描公开继承方法 |
| 嵌套重载重复过滤 | ThreadLocal guard，finally清理；调用前捕获UID |
| 系统过滤反过来隐藏管理列表 | 模块自身UID查询豁免 |
| 文本候选全隐藏后恢复 | 仅PROCESS_TEXT允许空结果；其他分类保留保护 |
| 跨用户相同appId误当自己 | 同调用方保护改为UID完全相等 |
| 后处理异常可能影响系统 | 模块处理失败保留原结果，原方法只调用一次 |
| 恢复规则时新旧字段混合 | 仓库操作锁、完成版本触发同步、远端单JSON原子配置；兼容旧键 |
| 服务绑定和后台同步竞争 | 单一后台同步入口，旧连接完成不覆盖新连接状态 |
| 监听注册竞态 | 加锁、先注册再读取；注册失败后续回调可重试 |
| 反射日志API可能受保护限制 | 使用正式API102日志方法 |
| BROWSABLE误分类、selector遗漏 | HTTP/HTTPS才归浏览器；读取selector，显式目标仍放行 |
| 相对组件名称不匹配 | .Activity规则ID规范化，导入规范化；拒绝控制字符 |
| 排序被后续系统排名覆盖 | 新增AOSP排名结束入口探测，未知结构不改结果 |
| 扫描取消/单图标异常/缓存陈旧 | 查询间检查取消、刷新清缓存、失败用占位；扩展示例MIME |
| 导出受Activity重建影响 | 任务与状态移到ViewModel，使用Application context，导出前刷新快照 |
| 取消后继续执行采集命令 | runInterruptible及中断传播，清理子进程 |
| 日志难集中查看 | 新增有界module-evidence，保留来源/PID并注明历史性 |
| 状态页按钮无效及旧说明 | 恢复详情入口，修正system、文本、排序说明，明确加载不等于命中 |
| 无生产者的诊断状态链 | 移除死DiagnosticEvent状态 |

## 测试与限制

已执行：DiagnosticBufferCheck 24项通过；FilterPolicyCheck 50项通过。覆盖缓冲区边界、环回、空列表策略和UID隔离。新增ModuleConfigTest检查配置往返、相对组件名、非法字符及保护策略，但未运行Android/JUnit套件。已做源码差异空白检查和ZIP完整性验证。

这些测试不等价于Kotlin编译、API链接、Binder调用或真机验收。新系统入口、排序入口、导出旋转行为均待本地/真机验证。

没有盲目在ComputerEngine所有内部方法重复安装Hook；先验证Binder边界。没有把SelectionMenuControl/HideMyApplist推定为冲突原因。没有取消文件空列表保护或阻止显式启动。

仍有兼容边界：硬编码/缓存文本菜单、厂商独立查询和排序、联系人直达分享、默认应用直接打开、其他模块改变结果。规则按组件全局生效，未新增用户资料级独立编辑。扫描不穷举私有协议及工作资料。状态页未新增跨进程实时健康通道，安装和命中需看日志。

## 一轮验收，最后导出一次

先备份规则、原签名安装17版、打开应用确认提交、授权并重启、开启诊断。每项记录操作时间、来源应用、预期和实际结果。

| 项目 | 操作与判据 |
| --- | --- |
| 启动 | SYSTEM_SERVER_STARTING；SYSTEM_HOOKS总数>0；RULES_READ atomic=true |
| 单文件分享 | 选一个分享目标隐藏后分享；SEND查询，目标消失而其他目标可用 |
| 多文件分享 | 单独设置多文件分类，再分享多张图；应为SEND_MULTIPLE |
| 打开方式 | 隐藏Acode，用有多个候选的文本文件测试；VIEW/OPEN，不触发RESTORE_ALL时应隐藏 |
| 浏览器 | 从会展示候选的来源打开HTTP/HTTPS；VIEW/BROWSER，默认直接打开另行注明 |
| 文本处理 | 隐藏四个AI目标，重新进入来源应用选中文本；PROCESS_TEXT可全空，不再RESTORE_ALL |
| 管理列表 | 刷新后隐藏目标仍能管理，取消勾选可恢复 |
| 显式调用 | 已指定组件入口仍正常启动，不把隐藏等同禁止启动 |
| 模式/备份 | 切换三种模式、恢复JSON后重开菜单；检查原子配置日志 |
| 排序 | 设置两个优先应用，重开选择器；需ORDER_HOOK_INSTALLED及ORDER_APPLIED证据 |
| 导出 | 导出时旋转，完成后ZIP可打开，含analysis/module-evidence.txt |

最后发一个ZIP和操作记录即可。先确认系统入口安装并命中，不要盲目扩大作用域。没有命中日志时应标记“未知/待适配”，不能直接判定成功。
