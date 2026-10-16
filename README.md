# LegacySpawnerBankTest (safe diagnostic build)

This is a **test-only diagnostic plugin** for Paper 1.21.11 and Java 21. It does not pay money, save bank balances, open a bank GUI, or alter the existing Skript bank. It tests whether the WildStacker API method documented by the server owner can return a spawner's stack amount.

## Build without installing development software locally

1. Create a private GitHub repository and upload the contents of this folder (not the outer folder itself).
2. Open the repository's **Actions** tab and select **Build test plugin**.
3. Click **Run workflow** if it has not started automatically.
4. When the workflow succeeds, open the run and download the `LegacySpawnerBankTest` artifact. It contains `legacy-spawner-bank-test-0.1.0.jar`.

## Test install

1. Back up the server first. Do not remove or edit `legacy_spawner_bank.sk`.
2. Stop the MCServerHost server.
3. Upload the JAR to `plugins/`.
4. Start the server and confirm `/plugins` lists `LegacySpawnerBankTest` and WildStacker.
5. As an operator, look directly at a placed spawner and run `/bankstackcheck`.
6. Confirm the displayed count matches WildStacker's actual stack size. This plugin is only a diagnostic; it has no payout code.

If the GitHub Actions build fails, share the failed step/log before uploading anything to the server. Never install an unbuilt source ZIP as a plugin.
