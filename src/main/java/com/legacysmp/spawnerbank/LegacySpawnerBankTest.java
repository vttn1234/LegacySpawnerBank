
package com.legacysmp.spawnerbank;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class LegacySpawnerBankTest extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Test-only diagnostic plugin enabled.");
        getLogger().info("No payouts, balances, or GUI are active.");

        if (getCommand("bankstackcheck") == null) {
            getLogger().severe("Command bankstackcheck missing from plugin.yml!");
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("bankstackcheck")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        Block block = player.getTargetBlockExact(6);

        if (block == null
                || !(block.getState() instanceof CreatureSpawner spawner)) {
            player.sendMessage(ChatColor.RED
                    + "Look directly at a placed spawner within 6 blocks.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "LEGACY BANK TEST > "
                + ChatColor.GRAY + "Spawner type: "
                + ChatColor.WHITE + spawner.getSpawnedType());

        if (getServer().getPluginManager().getPlugin("WildStacker") == null) {
            player.sendMessage(ChatColor.RED
                    + "WildStacker is not detected or enabled.");
            return true;
        }

        try {
            Class<?> api;

            try {
                api = Class.forName(
                        "com.bgsoftware.wildstacker.api.WildStackerAPI"
                );
            } catch (ClassNotFoundException ex) {
                getLogger().severe(
                        "WildStackerAPI class was not found. "
                        + "Confirm the installed WildStacker API package."
                );
                player.sendMessage(ChatColor.RED
                        + "WildStacker API class not found. Check console.");
                return true;
            }

            Method method = api.getMethod(
                    "getSpawnersAmount",
                    CreatureSpawner.class
            );

            Object result = method.invoke(null, spawner);

            if (!(result instanceof Integer amount) || amount < 1) {
                getLogger().warning(
                        "WildStacker returned an unexpected result: " + result
                );
                player.sendMessage(ChatColor.RED
                        + "WildStacker returned an unexpected stack amount.");
                return true;
            }

            player.sendMessage(ChatColor.GOLD + "LEGACY BANK TEST > "
                    + ChatColor.GRAY + "WildStacker stack amount: "
                    + ChatColor.GREEN + amount);

            player.sendMessage(ChatColor.GRAY
                    + "Test only: no money was paid or stored.");

        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().severe(
                    "WildStacker API lookup failed: "
                    + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage()
            );

            player.sendMessage(ChatColor.RED
                    + "Could not read WildStacker stack count. Check console.");
        }

        return true;
    }
}
