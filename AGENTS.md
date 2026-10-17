# AE2 NEI AutoCraft Bridge

- 当前适配 MC 1.7.10 / Forge 1614 的 GTNH RC1；具体依赖和构建入口见 README。输出保持 Java 8 字节码，测试夹具不得进入模组 JAR。
- 这是 ASM coremod，桥类有意排除 FML remap 并使用运行时名称反射。修改钩子须按实际依赖字节码核对；不改写 AE2、NEI、NEE 的原始 JAR。
- 合成结果及库存以服务端真实状态为准，不恢复客户端虚拟耗料/产物预测。修改网络协议时同步双端版本和握手校验。
- 目标按 NEI 真实 master-root 解析；回归须覆盖真实 RecipeChainMath/Iterator，以及 ME 已有目标的领取和补缺流程，不能把目标塞进模拟 initialItems 或只测服务端合成。
- 本机路径配置、部署历史、隐私检查记录仅留在 Git 忽略的本地文件中；发布前核对全部待发布历史与源码归档，不能只检查最新工作树。

## 本地资源约束

- 优先用轻量 JVM 回归和定向字节码/API 诊断、修复；游戏内验收不是代码修复的前置条件。正式 Minecraft 客户端或服务端运行期间，不启动完整 RC1 隔离测试服或整 JAR 反编译；重型验证安排在正式双端退出后。
- 大型模组的 class-source 查询可能触发整 JAR 反编译；优先使用 members/javap，确认范围后再调用源码工具。后台分析进程必须记录 PID、启动时间和目标；结束或异常时核对退出，避免遗留后台工作。
- 隔离测试仅使用独立世界与回环地址。`build/` 内的 libraries junction 指向正式依赖，不沿链接清理；`build/`、`dist/`、`backups/` 留在本机并排除 Git。
