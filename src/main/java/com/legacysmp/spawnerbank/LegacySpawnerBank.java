
package com.legacysmp.spawnerbank;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LegacySpawnerBank extends JavaPlugin implements Listener {

    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;

    private final Map<UUID, String> openDropMenus = new HashMap<>();

    private static final String MENU_TITLE =
            ChatColor.DARK_GREEN + "Spawner Bank";
    private static final String DROP_TITLE =
            ChatColor.DARK_GREEN + "Spawner Drops";

    @Override
    public void onEnable() {
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager()
                        .getRegistration(Economy.class);

        if (provider != null) {
            economy = provider.getProvider();
        }

        saveDefaultConfig();

        dataFile = new File(getDataFolder(), "spawner-data.yml");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LegacySpawnerBank enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }

        if (!(event.getClickedBlock().getState()
                instanceof CreatureSpawner spawner)) {
            return;
        }

        event.setCancelled(true);

        Location location = event.getClickedBlock().getLocation();
        String id = key(location);
        ensureRecord(id, spawner);

        Player player = event.getPlayer();
        Inventory menu = Bukkit.createInventory(null, 27, MENU_TITLE);

        menu.setItem(4, item(Material.SPAWNER,
                ChatColor.WHITE + spawner.getSpawnedType().name() + " Spawner"));
        menu.setItem(10, item(Material.CHEST, ChatColor.GOLD + "Mob Drops"));
        menu.setItem(12, item(Material.GOLD_INGOT,
                ChatColor.YELLOW + "Stored Money: $"
                        + data.getDouble(id + ".money", 0)));
        menu.setItem(14, item(Material.EXPERIENCE_BOTTLE,
                ChatColor.GREEN + "Stored XP: "
                        + data.getInt(id + ".xp", 0)));
        menu.setItem(16, item(Material.EMERALD, ChatColor.AQUA + "Selling"));

        player.openInventory(menu);
        player.setMetadata("lsb-current-spawner",
                new org.bukkit.metadata.FixedMetadataValue(this, id));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(MENU_TITLE)) {
            event.setCancelled(true);

            if (event.getRawSlot() != 10
                    || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            String id = getCurrentSpawner(player);
            if (id == null) return;

            openDropMenu(player, id);
            return;
        }

        if (title.equals(DROP_TITLE)) {
            int topSize = event.getView().getTopInventory().getSize();

            if (event.getRawSlot() < 0) {
                event.setCancelled(true);
                return;
            }

            if (event.getRawSlot() >= topSize) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
            } else if (event.isShiftClick()
                    || event.getClick().isKeyboardClick()
                    || event.getClick().isCreativeAction()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(MENU_TITLE)) {
            event.setCancelled(true);
        }
    }

    private void openDropMenu(Player player, String id) {
        Inventory inventory = Bukkit.createInventory(null, 54, DROP_TITLE);
        ConfigurationSection section =
                data.getConfigurationSection(id + ".items");

        if (section != null) {
            for (String slotText : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotText);
                    ItemStack item = section.getItemStack(slotText);
                    if (slot >= 0 && slot < inventory.getSize()
                            && item != null) {
                        inventory.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore invalid saved slots.
                }
            }
        }

        openDropMenus.put(player.getUniqueId(), id);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onDropMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String id = openDropMenus.remove(player.getUniqueId());
        if (id == null || !event.getView().getTitle().equals(DROP_TITLE)) {
            return;
        }

        data.set(id + ".items", null);
        ConfigurationSection section = data.createSection(id + ".items");

        Inventory inventory = event.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                section.set(String.valueOf(slot), item);
            }
        }
        saveData();
    }

    private String getCurrentSpawner(Player player) {
        if (!player.hasMetadata("lsb-current-spawner")) return null;
        return player.getMetadata("lsb-current-spawner").stream()
                .filter(value -> value.getOwningPlugin() == this)
                .map(org.bukkit.metadata.MetadataValue::asString)
                .findFirst().orElse(null);
    }

    private void ensureRecord(String id, CreatureSpawner spawner) {
        if (data.contains(id)) return;
        data.set(id + ".type", spawner.getSpawnedType().name());
        data.set(id + ".money", 0.0);
        data.set(id + ".xp", 0);
        data.createSection(id + ".items");
        saveData();
    }

    private String key(Location location) {
        World world = location.getWorld();
        return world.getName() + ","
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save spawner data: "
                    + exception.getMessage());
        }
    }

    private ItemStack item(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + "Spawner Bank"));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
