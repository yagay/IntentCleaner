# 1.5.0 / 24 — 快捷设置磁贴清理

## 使用

1. 覆盖安装后打开“磁贴”，启用磁贴清理，选择不想在待添加列表看到的磁贴。
2. 申请 SystemUI（com.android.systemui）作用域，首次授权后重启手机。
3. 重新打开系统快捷设置的磁贴编辑界面。已有固定磁贴不受影响。
4. 要恢复，取消勾选或关闭独立开关，再重新打开编辑界面。

## 实现与边界

- 独立 TileConfig：默认关闭、精确 spec 集合最多512项。custom 组件短类名规范化，不按应用名称误删所有服务。无需 WRITE_SECURE_SETTINGS 或组件禁用权限。
- 管理目录查询公开且具备 BIND_QUICK_SETTINGS_TILE 权限声明的 TileService，并读取 SystemUI stock 资源。内置名称资源缺失时显示标识；目录可能不完整，已配置缺席项可取消。查询失败有提示。
- AOSP 传统 TileAdapter.recalcSpecs：当前 mCurrentSpecs 中的磁贴保护，过滤 mAllTiles 副本供本次重建，finally 恢复原引用。未知当前状态不删除；只在主线程执行。
- AOSP 新版 EditModeViewModel.getTiles：使用宿主 Flow/FlowCollector 接口代理，不跨 ClassLoader 强制转换 Kotlin 类型；只过滤未固定编辑模型，保留元素身份和顺序、Continuation、挂起返回值及原异常，不重试原调用。
- 不写入系统固定布局、不调用 QSHost 删除磁贴、不过滤全局 queryIntentServices、不停止磁贴服务。
- 新作用域有明确提示，传统磁贴 Hook 可替换和重新发现；新版编辑器可能缓存 Flow 回调，因此安装该路径后拒绝 SystemUI 代码热更新并要求重启，避免新旧代码共存。配置更新不受此限制。旧版或无可复用句柄也需重启。
- v4 JSON 备份包含磁贴配置；读取 v1–v3 时默认关闭。远程恢复/重置同时包含磁贴配置。Intent 的显示模式不控制磁贴开关。
- 诊断包含磁贴配置、扫描快照、SystemUI 安装信息及按阶段统计的 TILE_* 事件。安装入口不代表实际命中；0个被移除也不代表功能失败。

## 验证

Java 检查全部通过：磁贴34项、反射8项、缓冲24项、过滤60项、分类/身份32项、协议22项，以及诊断证据检查。磁贴测试覆盖精确标识、固定项保护、传统列表恢复、未知状态、现代列表副本、Continuation/挂起返回值和异常透传。

新增 Kotlin 配置/备份测试；当前无 Android SDK/Kotlin 编译器，未执行 Gradle、APK构建或设备测试。保持现有 Gradle/AGP/SDK/签名配置不变，用户本地执行 `./gradlew testDebugUnitTest assembleDebug`。

OnePlus/OxygenOS 定制编辑器未验证；如果不命中上述两种 AOSP 路径，将记录不支持并保持原行为，不宣称已适配所有厂商。
