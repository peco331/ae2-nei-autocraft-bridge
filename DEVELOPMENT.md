# Development

The release JAR contains only `src/ae2nei/` classes, localized assets, the coremod manifest, mod metadata and license notices. Runtime dependencies and test fixtures are not bundled.

## Build

Use Python 3 and JDK 9 or newer (`javac --release 8`). Supply an installed 1.7.10 / Forge 1614 pack and its libraries:

```powershell
py -3 -B build.py --jdk "path/to/jdk" --server-root "path/to/server" --client-mods "path/to/client/mods" --libraries "path/to/launcher/libraries"
```

The default output is `dist/release-<version>/ae2-nei-autocraft-bridge-<version>.jar`; `--output-dir` overrides it. The version comes from `mcmod.info` and must match the mod annotation and network handshake in `AE2NEIMod.java`. The builder requires Forge 1614, launchwrapper 1.15, NEI 2.8.145-GTNH, ASM/ASM-tree 9.7.1 and Netty-all 4.0.10.Final. Paths can also be set with `AE2NEI_SERVER_ROOT`, `AE2NEI_CLIENT_MODS`, `AE2NEI_LIBRARIES`, and `AE2NEI_JDK` (or `JAVA_HOME`). There are no machine-specific defaults.

An ignored `.local-paths.json` may supply `server_root`, `client_mods`, `libraries` and `jdk` instead of environment variables. The optional paired installer also accepts `client_root`, `backup_root`, and `maintenance_document`. Keep this file on your machine.

## Checks

```powershell
py -3 -B tests/verify.py --bridge "dist/release-1.1.7/ae2-nei-autocraft-bridge-1.1.7.jar" --nei "path/to/NotEnoughItems.jar" --ae2 "path/to/appliedenergistics2.jar"
py -3 -B tests/headless.py --bridge "dist/release-1.1.7/ae2-nei-autocraft-bridge-1.1.7.jar" --nei "path/to/NotEnoughItems.jar"
```

Set the same path environment variables as above. This is a small JVM check of the four transformed classes, bounded protocol/NBT decoding, cancellation and successor queue handling, inventory capacity, snapshot consumption, reflection caching and log throttling. It does not launch Minecraft.

`tests/headless.py` runs with a 128 MiB heap and two logical processors. It reads unchanged `RecipeChainMath`, `RecipeChainIterator` and `RestartableTask` classes from the selected NEI JAR and executes the actual original/patched worker batch loop. It also asks the JVM to verify the full transformed worker. Small test-only value types model solid materials and bookmarks; they do not model fluid containers, GT tool damage, native AE2 inventory movement, GUI input, or recipe overlay resolution. The fixtures never enter the mod JAR.

## 1.1.7 worker corrections

The native NEI loop subtracts 64 after each successful boolean `craft` call. The bridge can acknowledge a partial batch, so that fixed decrement discards real progress information. The worker now consumes the confirmed count once; ordinary overlays retain the upstream default. The regression reproduces a planned batch of 70 with only 9 actually crafted being marked complete for that pass, then checks exact accounting after the patch.

NEI's `RestartableTask` leaves its thread reference set if `execute()` throws. Calling `restart()` then sets flags on a dead thread. The AE2 lookup wrapper now terminates the bridge run normally on recipe lookup exceptions, allowing NEI's normal cleanup and a later retry. This is limited to AE2 bridge workers. `Shift+C` calls NEI's private `autocraftingIgnoreInventory`, which creates the master root and then replaces every goal already met by `getInventoryItems` with a larger next-batch goal. In an AE2 terminal that view includes ME stock, so the 1.1.6 preparation step retrieved the entire stored amount of such goals. The bridge wraps that single `createMasterRoot()` call, records the original root goals by identity, and retrieves only goals NEI did not replace. Crafting counts and output routing are unchanged.

These proven code errors are not evidence that every reported in-game failure has the same cause.

The 1.1.1 baseline previously passed 36 isolated RC1 checks using actual AE2 containers and native crafting with a controlled ME monitor. The original two-turbine output stacking and empty-grid case passed. These checks are server fixtures, not a full client UI acceptance test. The 1.1.1 public package was rebuilt from the same Java sources with updated author/license metadata; official AE2 1078/1082 and NEI 145/159 hooks were checked separately before that release.

`tests/isolated.py` is a machine-local integration harness, using an independent world and loopback address. It refuses to run while the production game is active and limits its own JVM to two logical CPUs. Configure its local pack paths before use; it needs the previous 1.1.0 JAR for a historical snapshot comparison. Never clean through the shared `libraries` junction. Do not start a complete RC1 test server or whole-mod decompilation alongside a running game.

Optional `--bridge` and `--harness-source` arguments select an explicit JAR and native fixture for version comparisons. The run record retains the selected JAR hash and source path locally.

`install.py` is an optional offline paired installation/rollback utility, configured through explicit paths. Historical deployment/evidence scripts are retained locally and excluded from published source. Install downloaded releases manually as described in README.

## Upstream hook references

- [AE2 1082 SlotCraftingTerm](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/blob/rv3-beta-1082-GTNH/src/main/java/appeng/container/slot/SlotCraftingTerm.java): `capCraftingAttempts(int)`, invoked by `doClick`.
- [NEI 145 AutoCraftingManager](https://github.com/GTNewHorizons/NotEnoughItems/blob/2.8.145-GTNH/src/main/java/codechicken/nei/recipe/AutoCraftingManager.java): inventory snapshots and background chain worker.
- [NEI 145 ShortcutInputHandler](https://github.com/GTNewHorizons/NotEnoughItems/blob/2.8.145-GTNH/src/main/java/codechicken/nei/api/ShortcutInputHandler.java): both Shift+C and Shift+Ctrl+C, including crafting groups, call `runProcessing`.

Bridge runtime calls use reflection because the bridge is excluded from FML remapping. Verify the exact target bytecode after changing ASM hooks. Keep client/server versions synchronized when changing the protocol.

## 1.1.3 limits

Compressed messages retain the 30,000-byte recipe and 4,096-byte expected-output limits. Expanded NBT is capped at 512 KiB per recipe, 64 KiB per expected stack, and 8 MiB per inventory snapshot. Depth is limited to 32, total tags to 262,144, list elements to 16,384 and ingredient alternatives to 256 per cell. Native NBT parsing additionally uses a finite `NBTSizeTracker`.

Cancellation is scoped to the authenticated sender and request ID. A replacement waits behind the cancelled request's cleanup; at most one successor is retained per player. Native calls already in progress complete before cancellation takes effect. Wire-compatible peers must both report 1.1.3.

The 1.1.3 candidate passed 48 bytecode/protocol checks for each of local RC1, official AE2 1078/NEI 145, and official AE2 1082/NEI 159. It also passed 58 isolated RC1 native assertions, including intermediate routing, cancellation followed by a replacement request, limited inventory space, output stacking, and matrix cleanup. The paired installer/rollback fixture passed. Full client UI and large-network TPS acceptance remain unverified.

## 1.1.4 routing regression

Recipe goals become ingredients of the synthetic master root after `createMasterRoot()`. `RecipeChainMath.initialItems` initially contains ordinary bookmarks, but `RecipeChainIterator.next()` replaces it with initial items and current inventory. Since 1.1.5, only master-root ingredients authorize final-output classification; unknown plans preserve outputs in the player inventory. The native routing fixture constructs real NEI bookmark items and calls `RecipeChainMath.of()` and `createMasterRoot()` before choosing the server operation, including an unrelated ordinary bookmark that reproduced the regression.

The 1.1.4 candidate passed 52 lightweight assertions on each of the three dependency combinations above and 60 isolated native assertions. Network execution, cancellation, snapshots and ASM hooks retain the 1.1.3 implementation. The bridge adds no custom progress UI or input handlers; NEI retains its existing interaction. Actual client feedback and large-network TPS remain pending gameplay acceptance.

## 1.1.5 recipe notice

The unsupported-recipe notice is triggered only when the NEI worker attempts a recipe that lacks a supported terminal overlay. Inventory-satisfied recipes do not reach this check. A thread-local limit permits one notice per chain, and the chat message is queued onto the client tick thread. It uses the actual output name and recipe handler name with the packaged English/Chinese translation; it does not change recipe selection or inventory handling.

The candidate passed 55 lightweight assertions for each of the three dependency combinations and 61 isolated RC1 native assertions. The native routing regression also exercises `RecipeChainIterator.updateInventory()` and `next()` to ensure existing intermediates remain intermediates. Actual chat display in the client and large-network TPS remain pending gameplay acceptance.

## 1.1.6 goal preparation

Operation 3 prepares master-root goals before the worker's first inventory observation. Its recipe payload contains up to 256 normalized item stacks with positive `bridgeGoalQuantity` values; it retains the existing compressed and expanded recipe limits. Duplicate precise goals are combined with overflow checking. The server moves only the amount needed to satisfy each goal in the player inventory, at most one physical stack per preparation step and four steps per tick. Returned inventory is an authoritative observation; preparation never predicts a craft or reports transferred items as completed crafts.

The same container, power, permissions and cancellation checks apply to preparation and crafting. Ordinary workbenches keep the NEI implementation. Matching 1.1.6 peers are required because earlier servers do not support this operation. The worker also wraps recipe-reference lookup to explain unsupported machine bookmarks that resolve to null, while retaining NEI's recipe selection.

Three dependency combinations passed 56 lightweight assertions each. Native RC1 validation passed 83 assertions, including real NEI deficit planning, physical retrieval, crafting completion, already satisfied goals, capacity, NBT variants, unstackable items, large goals, permissions, cancellation and payload limits. Four additional local scenarios used the actual bookmarked GT turbine recipe, including missing materials and two stored plus two newly crafted outputs. Full client interaction and large-network TPS acceptance remain separate.
