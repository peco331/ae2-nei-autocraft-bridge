# AE2 NEI AutoCraft Bridge

**在 AE2 合成终端里使用 GTNH NEI 的自动合成 / 合成链，原料直接从 ME 网络取。**

工作台上怎么用 NEI 书签合成链，在 ME 合成终端里就怎么用：`Shift+C` 合成物品，`Shift+Ctrl+C` 只补缺失。中间材料会先做出来，成品进背包。

> Use GTNH NEI autocrafting (crafting chains, Craft Items / Craft Missing Items) in AE2 Crafting Terminals, pulling ingredients from your ME network. Install the same version on client and server.

## 安装

1. 从 [Releases](https://github.com/peco331/ae2-nei-autocraft-bridge/releases) 下载最新的 `ae2-nei-autocraft-bridge-<版本>.jar`。
2. 退出游戏和服务器，放进 `mods/`，删掉旧版本的本模组。
   - **单人**：只放客户端。
   - **多人**：客户端和服务端都要放，**版本必须一致**，否则无法进服。
3. 不需要替换 AE2、NEI、NEE 的 JAR。

## 使用

1. 在 NEI 配方页点心形，收藏目标物品及其中间材料的**工作台配方**。
2. 在目标配方的心形上按 `Shift+A`，把整条合成链加入书签；按住 `Ctrl` 滚轮调整目标数量。
3. 打开 **ME 合成终端**，鼠标指向书签组左侧的括号：

| 快捷键 | 效果 |
| --- | --- |
| `Shift+C` | 合成物品：按书签数量做一批，成品进背包 |
| `Shift+Ctrl+C` | 合成缺失物品：先把 ME 里已有的目标取到背包，只合成不够的部分 |

例：目标 4 个涡轮，ME 里有 2 个，按 `Shift+Ctrl+C` → 取出 2 个，再做 2 个，背包得到 4 个。

- 指向组内某个产物而不是括号时，只执行通向该产物的步骤。
- 需要在 NEI 设置里开启“自动合成”；合成期间不要关闭终端。
- 多做出来的中间材料会尽量存回 ME；ME 拒收时留在背包。合成格里的余料同样处理。
- 快捷键以你的 NEI 键位设置为准。

## 注意

- **只执行工作台（2×2 / 3×3）配方。** 机器配方可以收藏，但材料要先自己用机器做好；合成链卡在机器配方上时，聊天栏会提示缺什么。
- 不会调用 AE2 合成 CPU 或样板。
- ME 网络需要通电，并且你要有提取权限（存回 ME 需要注入权限）；背包要留空位接收产物。
- 断电、关终端、背包满等情况会中途停止；已完成的步骤不会回滚。
- 超大 ME 网络、无线终端、带耐久工具或矿辞替代较多的配方链测试得还不充分，遇到问题欢迎反馈。

## 兼容性

| 组件 | 已验证版本 |
| --- | --- |
| GTNH | 2.9.0-RC-1（Minecraft 1.7.10 / Forge 1614） |
| AE2 | `rv3-beta-1078-GTNH`、`rv3-beta-1082-GTNH` |
| NEI | `2.8.145-GTNH`、`2.8.159-GTNH` |
| NotEnoughEnergistics | `1.7.45` |

游戏内实测基于 GTNH 2.9.0-RC-1 自带的 AE2 1078 与 NEI 145；AE2 1082、NEI 159 只做过钩子兼容检查。其他 AE2 / NEI 分支或版本未必兼容。各版本的改动见 [CHANGELOG](CHANGELOG.md)。

## 反馈问题

在 [Issues](https://github.com/peco331/ae2-nei-autocraft-bridge/issues) 里写清楚：

- 整合包版本，以及 AE2 / NEI / NEE / 本模组版本；
- 目标配方和数量、按的快捷键、实际发生了什么；
- 合成前后背包、ME、合成格里相关物品的数量变化；
- 日志中带 `[AE2-NEI-Bridge]` 的行。排查时可加 JVM 参数 `-Dae2nei.debug=true` 输出详细记录。

## 许可证

[MIT](LICENSE)。第三方许可见 [THIRD_PARTY.md](THIRD_PARTY.md)。本项目为非官方附加模组，与 GTNH、AE2 官方无关。构建说明见 [DEVELOPMENT.md](DEVELOPMENT.md)。
