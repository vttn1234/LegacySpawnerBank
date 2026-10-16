package com.legacysmp.spawnerbank;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class LegacySpawnerBank extends JavaPlugin implements Listener {
    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;
    private final Random random = new Random();
    private final Map<EntityType, int[]> payouts = new HashMap<>();
    private NamespacedKey bankGuiKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider not found. Install/enable EssentialsX Economy and Vault.");
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        bankGuiKey = new NamespacedKey(this, "bank_gui");
        dataFile = new File(getDataFolder(), "balances.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadPayouts();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::payAllLoadedSpawners, 300L, 300L);
        getLogger().warning("This build uses location-based balances and assumes stack size 1. WildStacker stack-count/merge integration is NOT included yet.");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider(); return economy != null;
    }

    private void loadPayouts() {
        payouts.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("payouts");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            EntityType type;
            try { type = EntityType.valueOf(name.toUpperCase()); } catch (IllegalArgumentException ex) { continue; }
            int min = section.getInt(name + ".min", 0), max = section.getInt(name + ".max", min);
            if (min >= 0 && max >= min) payouts.put(type, new int[]{min, max});
        }
    }

    private void payAllLoadedSpawners() {
        for (var world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                for (var state : chunk.getTileEntities()) {
                    if (!(state instanceof CreatureSpawner spawner)) continue;
                    EntityType mob = spawner.getSpawnedType();
                    if (mob == null) continue;
                    int[] range = payouts.get(mob);
                    if (range == null) continue;
                    double amount = range[0] + (range[1] == range[0] ? 0 : random.nextInt(range[1] - range[0] + 1));
                    String key = key(spawner.getLocation());
                    data.set(key, data.getDouble(key, 0D) + amount);
                }
            }
        }
        saveData();
    }

    @EventHandler public void onSpawnerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof CreatureSpawner spawner)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        double balance = data.getDouble(key(spawner.getLocation()), 0D);
        Inventory inv = Bukkit.createInventory(new BankHolder(), 27, ChatColor.DARK_GREEN + "Spawner Storage");
        ItemStack info = item(Material.SPAWNER, ChatColor.GOLD + "Spawner Bank", ChatColor.GRAY + "Type: " + spawner.getSpawnedType(), ChatColor.GRAY + "Stored: " + economy.format(balance));
        inv.setItem(13, info);
        ItemStack collect = item(Material.GOLD_INGOT, ChatColor.GREEN + "Collect", ChatColor.GRAY + "Click to deposit stored money", ChatColor.YELLOW + economy.format(balance));
        collect.editMeta(meta -> meta.getPersistentDataContainer().set(bankGuiKey, PersistentDataType.STRING, key(spawner.getLocation())));
        inv.setItem(15, collect);
        player.openInventory(inv);
    }

    @EventHandler public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BankHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 15) return;
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || !clicked.hasItemMeta()) return;
        String key = clicked.getItemMeta().getPersistentDataContainer().get(bankGuiKey, PersistentDataType.STRING);
        if (key == null) return;
        double amount = data.getDouble(key, 0D);
        if (amount <= 0) { player.sendMessage(ChatColor.YELLOW + "This spawner bank is empty."); player.closeInventory(); return; }
        // Remove and persist before depositing to prevent duplicate collection after a restart.
        data.set(key, 0D); saveData();
        var response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            data.set(key, amount); saveData();
            player.sendMessage(ChatColor.RED + "Deposit failed. Your spawner balance was restored.");
        } else player.sendMessage(ChatColor.GREEN + "Collected " + economy.format(amount) + " from this spawner bank.");
        player.closeInventory();
    }

    private String key(Location loc) {
        return "balances." + loc.getWorld().getUID() + "." + loc.getBlockX() + "." + loc.getBlockY() + "." + loc.getBlockZ();
    }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(name); meta.setLore(java.util.List.of(lore)); stack.setItemMeta(meta); return stack;
    }
    private void saveData() { try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save balances.yml: " + e.getMessage()); } }
    @Override public void onDisable() { if (data != null) saveData(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spawnerbank.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); loadPayouts(); sender.sendMessage(ChatColor.GREEN + "Spawner Bank config reloaded."); return true; }
        sender.sendMessage(ChatColor.YELLOW + "/spawnerbank reload"); return true;
    }
    private static final class BankHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
