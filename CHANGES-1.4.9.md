# 1.4.9 / 23 — 排序执行与清理项联动

## 修复

- 修复在 ResolverListAdapter 上误找 public getIntent() 导致排序直接退出。支持父类 protected getTargetIntent、mTargetIntent 及旧版 mResolverListCommunicator.getTargetIntent。
- 按适配器 Context 补全 MIME，无法读取时保留安全跳过，不读取文件正文。
- 普通列表及分享推荐区在已知两参数 processSortedList 入口重排；不猜测未知厂商签名。
- 分享全部应用区在 BaseAdapter.notifyDataSetChanged 之前重排已知 Chooser 的 ArrayList mSortedList。仅匹配两种 AOSP Chooser 及其子类，不处理其他适配器；不移动联系人区域，调用方专属包（含合并组）保持原槽位。全部反射及排序计算完成后才更新 backing list，不更改候选数量或对象。
- PROCESS_TEXT 在系统/客户端查询出口对过滤后的结果稳定排序，保留管理查询豁免；无过滤变化时也可排序。其他分类仍不在 PM 查询阶段排序。
- 热重载支持替换新增字母区 Hook，并从当前宿主 Application 补充 APK ClassLoader；查询 Hook 更新成功不被当作排序适配证据，单独输出能力记录。
- 记录缺失类/方法、每进程排序配置数量和摘要、计算结果、跳过及失败原因。ORDER_DELIVERED 不声称画面验证成功。
- 排序页所有列表和展开组件共用当前分类的规则显示条件。全部清理的应用不显示；部分清理只显示存活组件。暂时隐藏的顺序保留，取消清理恢复；移动时交换可见邻项，隐藏配置槽位不变。

## 验证与边界

- 本次运行了无需 Android SDK 的 Java 检查：反射访问 8 项、缓冲 24 项、过滤 60 项、分类/身份 32 项、协议 21 项及诊断证据检查，全部通过。
- 新增 Kotlin 回归用例覆盖五类清理联动、部分组件清理、白名单/暂停、候选限制及隐藏项移动；此环境无 Android SDK/Kotlin 编译器，未执行 Gradle 测试或 APK 构建。
- 本地执行 `./gradlew testDebugUnitTest assembleDebug`。环境依赖、SDK 和签名设置未调整。
- 设备验证：各分类配置两个优先应用，重新打开菜单；分享分别查看推荐区和全部应用区；长按文本查看文本动作；清理其中一项后确认排序页隐藏，取消清理后确认恢复。
- 保留不支持的 OEM 菜单、来源应用自行重排、默认应用直达和联系人快捷目标的边界，不宣称全 ROM 通用。
- 现有诊断包为旧版、无排序配置，不能作为本版设备效果证明。需要本地编译安装后验证。
