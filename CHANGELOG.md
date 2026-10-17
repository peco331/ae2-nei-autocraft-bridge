# Changelog

## 1.1.7 — Unreleased

- Subtract the server-confirmed number of completed crafts from the NEI worker's remaining batch instead of always subtracting 64. Keep the original batch behavior for ordinary NEI overlays.
- Catch recipe-reference failures in AE2 bridge tasks so NEI can clear and restart its worker. Keep failures in ordinary crafting outside this bridge handling; isolate recipe-notice failures as well.
- Add a small JVM regression that executes the original and patched NEI batch loops, verifies the full transformed worker, runs the real NEI planner/iterator, and exercises the real NEI restartable task after a lookup failure. Solid-material fixtures replace Minecraft value types; no game or server starts.
- Stop `Shift+C` (Craft Items) from moving the whole ME stock of a goal into the player inventory. NEI raises goals already met by the terminal's ME + player view to the next batch; the bridge now records the root goals before that raise and retrieves only unraised goals. A goal of four steel plates with 1900 stored no longer pulls about 1900 plates and then fails for lack of inventory space. `Shift+Ctrl+C` is unchanged.
- Require matching 1.1.7 bridge versions. The preparation and crafting wire operations retain their existing format.

Validation: 69 bytecode/protocol assertions passed for each of three dependency combinations, and 18 headless worker/planner assertions passed on each of NEI 145 and 159. No game or test server was launched for these checks. Native AE2 inventory movement and client interaction were not revalidated in this revision.

修正部分完成时的计数和配方查找异常后的任务恢复；`Shift+C` 不再把 ME 中目标物品的全部库存搬进背包；这两处代码错误不代表已确定所有现场故障的原因。尚未公开发行，完整客户端流程仍待验收。

## 1.1.6 — Unreleased

- Retrieve available requested outputs from ME into the player inventory before NEI calculates missing crafts. A goal of four with two stored now retrieves two and crafts the remaining two; already satisfied goals can be collected without crafting.
- Bound preparation across ticks, respecting item stack limits, inventory capacity, NBT identity, view-cell filters, power, EXTRACT permission, window identity and cancellation.
- Show the existing recipe notice even when NEI cannot resolve a bookmarked machine recipe; report unresolved non-machine bookmarks separately.
- Stop labelling normal NEI cancellation as a request timeout. Actual timeouts still stop uncertain requests without retrying.
- Require matching 1.1.6 peers for the new preparation operation.

Validation: three dependency combinations each passed 56 lightweight assertions; 83 native RC1 assertions and four local scenarios using the actual bookmarked GT turbine recipe passed. Client interaction and large-network TPS acceptance remain pending.

补上 ME 已有目标的领取步骤，再按真实库存计算需要合成的数量；保留 NEI 两种合成模式的原有规则。收藏机器配方未能解析时也会提示，正常取消不再被记录为请求超时。此版本尚未公开发行。

## 1.1.5 — Unreleased

- Add one localized chat notice per NEI chain when a bookmarked recipe cannot run in the crafting terminal. Name the material and recipe type so machine ingredients can be prepared beforehand; preserve bookmarks and existing controls.
- Keep final-output classification limited to master-root ingredients. NEI replaces `initialItems` with inventory during iteration, which must not turn existing intermediates into requested final outputs.
- Add a regression using the real NEI iterator's inventory refresh and an explicit JAR/fixture selection option for isolated version comparisons.
- Require matching 1.1.5 bridge versions on both ends.

Validation: 55 lightweight assertions passed for each of three dependency combinations, plus 61 isolated RC1 native assertions. Client UI and large-network TPS acceptance remain pending.

合成链需要执行机器等不受终端支持的收藏配方时，聊天栏提示材料和配方类型，同一轮只提示一次。保留机器配方收藏；修正库存刷新后的中间产物分类。此版本尚未公开发行。

## 1.1.4 — Unreleased

- Fix final recipe outputs being deposited into ME storage. Read target recipe outputs from NEI master-root ingredients instead of treating ordinary `initialItems` bookmarks as the complete goal list.
- Retain all 1.1.3 fixes and NEI's existing interaction; no additional overlay, buttons or progress packets.
- Replace the routing integration fixture with actual `RecipeChainMath`, including unrelated ordinary bookmarks, multiple goals, NBT variants and the basic steam turbine output.
- Require matching 1.1.4 bridge versions on both ends.

Validation: 52 lightweight assertions per dependency combination (three combinations) and 60 isolated RC1 native assertions passed. Full client UI and large-network TPS acceptance remain unverified.

修复 1.1.2/1.1.3 把最终产物误送入 ME 的回归，保留 1.1.3 的安全、取消及容量修复，沿用 NEI 原有界面和操作。此版本尚未公开发行。

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
