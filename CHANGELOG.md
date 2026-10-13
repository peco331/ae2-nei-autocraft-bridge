# Changelog

## 1.1.3 — Unreleased

- Merge the local 1.1.2 intermediate-output routing: return newly crafted intermediates to ME where permitted, preserving existing player quantities and rejected leftovers.
- Bound decompressed NBT bytes, tag depth/count and array/list lengths before native allocation; limit recipe alternatives.
- Add authenticated request cancellation and a bounded successor queue. Clean up a cancelled task before starting its replacement; report busy requests explicitly.
- Reduce native crafting batches to the available player-inventory capacity.
- Require 1.1.3 on both ends because cancellation adds a network message.

Validation: 48 lightweight assertions per dependency combination (three combinations), 58 isolated RC1 native assertions, and paired installer/rollback fixtures passed. Full client UI and large-network TPS acceptance remain unverified.

合并 1.1.2 中间产物归还；修复 NBT 解码边界、取消任务后继续耗料及背包部分容量不足时整批被拒绝的问题。此版本尚未公开发行。

## 1.1.1 — 2026-10-12

First public release: **GTNH NEI autocrafting in AE2 Crafting Terminals**.

Run NEI bookmark crafting chains using ME network inventory. Supports NEI's
**Craft Items** (`Shift+C`) and **Craft Missing Items** (`Shift+Ctrl+C`) actions,
including crafting groups. Results are confirmed by the server and delivered
to the player's inventory.

- Server-side native AE2 recipe filling and bounded crafting.
- Merge matching stackable outputs and return crafting-grid leftovers where possible.
- Quiet normal operation, rate-limited errors, cached reflection and tick-sliced work.
- Install the same bridge version on the client and server; single-player needs one client installation.
- Java 8 bytecode; MIT-licensed bridge, no bundled dependencies or test fixtures.

### Validation

- 22 bytecode/protocol checks passed against local RC1 dependencies.
- The same 22 checks passed against official AE2 `rv3-beta-1078-GTNH` with NEI `2.8.145-GTNH`,
  and official AE2 `rv3-beta-1082-GTNH` with NEI `2.8.159-GTNH`.
- All 23 runtime class files are byte-identical to the previously tested local 1.1.1 build;
  that baseline passed 36 isolated native RC1 checks. Public packaging updates author/license metadata.

The official-version checks validate hooks and protocol, not a full newer-pack client playthrough.
End-to-end UI shortcuts, wireless terminals and large-network TPS still need wider testing.

### Known limits

Only crafting-table recipes are executed. Machines and AE2 pattern/CPU autocrafting are outside this release.
Surplus intermediate outputs may remain in the player inventory when a recipe makes a larger batch than
the next step needs. Automatic intermediate return to ME storage is not included in 1.1.1.

---

首次公开发行：让 **GTNH NEI 自动合成 / 合成链**在 **AE2 合成终端**里直接使用 ME 库存。
支持“合成物品”和“合成缺失物品”，双端安装同一版本。包含产物堆叠、合成格余料回收及日志/执行开销改进。
1.1.1 的中间配方批量余量仍可能留在背包；本次发行不包含 1.1.2 的中间产物归还功能。
