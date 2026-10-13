# Development

The release JAR contains only `src/ae2nei/` classes, the coremod manifest, mod metadata and license notices. Runtime dependencies and test fixtures are not bundled.

## Build

Use Python 3 and JDK 9 or newer (`javac --release 8`). Supply an installed 1.7.10 / Forge 1614 pack and its libraries:

```powershell
py -3 -B build.py --jdk "path/to/jdk" --server-root "path/to/server" --client-mods "path/to/client/mods" --libraries "path/to/launcher/libraries"
```

The default output is `dist/release-<version>/ae2-nei-autocraft-bridge-<version>.jar`; `--output-dir` overrides it. The version comes from `mcmod.info` and must match the mod annotation and network handshake in `AE2NEIMod.java`. The builder requires Forge 1614, launchwrapper 1.15, NEI 2.8.145-GTNH, ASM/ASM-tree 9.7.1 and Netty-all 4.0.10.Final. Paths can also be set with `AE2NEI_SERVER_ROOT`, `AE2NEI_CLIENT_MODS`, `AE2NEI_LIBRARIES`, and `AE2NEI_JDK` (or `JAVA_HOME`). There are no machine-specific defaults.

An ignored `.local-paths.json` may supply `server_root`, `client_mods`, `libraries` and `jdk` instead of environment variables. The optional paired installer also accepts `client_root`, `backup_root`, and `maintenance_document`. Keep this file on your machine.

## Checks

```powershell
py -3 -B tests/verify.py --bridge "dist/release-1.1.3/ae2-nei-autocraft-bridge-1.1.3.jar" --nei "path/to/NotEnoughItems.jar" --ae2 "path/to/appliedenergistics2.jar"
```

Set the same path environment variables as above. This is a small JVM check of the three transformed classes, bounded protocol/NBT decoding, cancellation and successor queue handling, inventory capacity, snapshot consumption, reflection caching and log throttling. It does not launch Minecraft.

The 1.1.1 baseline previously passed 36 isolated RC1 checks using actual AE2 containers and native crafting with a controlled ME monitor. The original two-turbine output stacking and empty-grid case passed. These checks are server fixtures, not a full client UI acceptance test. The 1.1.1 public package was rebuilt from the same Java sources with updated author/license metadata; official AE2 1078/1082 and NEI 145/159 hooks were checked separately before that release.

`tests/isolated.py` is a machine-local integration harness, using an independent world and loopback address. It refuses to run while the production game is active and limits its own JVM to two logical CPUs. Configure its local pack paths before use; it needs the previous 1.1.0 JAR for a historical snapshot comparison. Never clean through the shared `libraries` junction. Do not start a complete RC1 test server or whole-mod decompilation alongside a running game.

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
