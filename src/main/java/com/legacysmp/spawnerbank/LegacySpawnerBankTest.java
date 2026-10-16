
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
        getLogger().info("Diagnostic plugin enabled.");
        getLogger().info("No bank balances or payouts are modified.");

        if (getCommand("lsbdebug") == null) {
            getLogger().severe("Command lsbdebug is missing from plugin.yml!");
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("lsbdebug")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this command in-game.");
            return true;
        }

        Block block = player.getTargetBlockExact(6);

        if (block == null
                || !(block.getState() instanceof CreatureSpawner spawner)) {
            player.sendMessage(ChatColor.RED
                    + "Look at a placed spawner within 6 blocks.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD
                + "LSB DEBUG: New Java command is running.");
        player.sendMessage(ChatColor.GRAY + "Spawner type: "
                + ChatColor.WHITE + spawner.getSpawnedType());

        if (getServer().getPluginManager().getPlugin("WildStacker") == null) {
            player.sendMessage(ChatColor.RED
                    + "WildStacker is not detected.");
            return true;
        }

        try {
            Class<?> api = Class.forName(
                    "com.bgsoftware.wildstacker.api.WildStackerAPI"
            );

            Method method = api.getMethod(
                    "getSpawnersAmount",
                    CreatureSpawner.class
            );

            Object result = method.invoke(null, spawner);

            player.sendMessage(ChatColor.YELLOW
                    + "API result: " + result);
            player.sendMessage(ChatColor.GRAY
                    + "Diagnostic only: no money was paid.");

        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().severe("WildStacker API test failed: "
                    + ex.getClass().getSimpleName() + ": "
                    + ex.getMessage());

            player.sendMessage(ChatColor.RED
                    + "API test failed. Check the server console.");
        }

        return true;
    }
}
