
package com.legacysmp.spawnerbank;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class LegacySpawnerBank extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("LegacySpawnerBank enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LegacySpawnerBank disabled.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (command.getName().equalsIgnoreCase("lsbdebug")) {
            sender.sendMessage(
                    ChatColor.GREEN + "LegacySpawnerBank Java plugin is running!"
            );
            return true;
        }
        return false;
    }
}

