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
        getLogger().info("Test-only build enabled. No payouts, balances, or GUI are active.");
        if (getCommand("bankstackcheck") == null) {
            getLogger().severe("Command bankstackcheck missing from plugin.yml!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("bankstackcheck")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof CreatureSpawner spawner)) {
            player.sendMessage(ChatColor.RED + "Look directly at a placed spawner within 6 blocks.");
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "LEGACY BANK TEST " + ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + "Spawner type: " + ChatColor.WHITE + spawner.getSpawnedType());
        if (getServer().getPluginManager().getPlugin("WildStacker") == null) {
            player.sendMessage(ChatColor.RED + "WildStacker is not detected. Check that the plugin is enabled.");
            return true;
        }
        try {
            Class<?> api = Class.forName("com.bgsoftware.wildstacker.api.WildStackerAPI");
            Method method = api.getMethod("getSpawnersAmount", CreatureSpawner.class);
            Object result = method.invoke(null, spawner);
            if (!(result instanceof Integer amount) || amount < 1) {
                player.sendMessage(ChatColor.RED + "WildStacker returned an unexpected stack amount.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "LEGACY BANK TEST " + ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + "WildStacker stack amount: " + ChatColor.GREEN + amount);
            player.sendMessage(ChatColor.GRAY + "Test only: no money was paid or stored.");
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Could not call WildStacker API: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            player.sendMessage(ChatColor.RED + "Could not read WildStacker stack count. Check server console.");
        }
        return true;
    }
}
