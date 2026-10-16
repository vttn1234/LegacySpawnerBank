# LEGACY SMP Spawner Bank — MVP source

Target: Paper 1.21.11, Java 21, Vault + EssentialsX Economy.

## Current MVP features
- Pays random income every 15 seconds for configured spawners in loaded chunks.
- Stores balances in `plugins/LegacySpawnerBank/balances.yml`.
- Right-click a spawner to open a storage GUI; click Collect to deposit through Vault.
- Saves balance before attempting a deposit and restores it if Vault reports failure.

## Important limitations
- This first MVP is **not production-ready for WildStacker stacked spawners**. It does not read WildStacker stack counts or handle stack merge/split/move events. It assumes stack size 1 and uses block location as the bank key. Do not use it as the live economy bank for stacked spawners until WildStacker API integration is added and tested.
- Only loaded chunks are processed. No offline/unloaded-chunk income.
- The code has not been compiled or tested against your host's exact server build in this environment.

## Build
Requires Maven and Java 21:
`mvn package`
Then copy `target/legacy-spawner-bank-0.1.0.jar` to `plugins/` on a test server.

Install Vault and EssentialsX Economy. Back up your server first. Test with one ordinary spawner before live use.
