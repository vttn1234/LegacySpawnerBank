package com.legacysmp.spawnerbank;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LegacySpawnerBank extends JavaPlugin implements Listener {
    private Economy economy;
    private YamlConfiguration data;
    private File dataFile;
    private NamespacedKey sourceKey;
    private final Map<UUID, String> menus = new HashMap<>();
    private final Map<UUID, String> dropMenus = new HashMap<>();

    private static final String MAIN_TITLE = ChatColor.DARK_GREEN + "Spawner Bank";
    private static final String DROP_TITLE = ChatColor.DARK_GREEN + "Spawner Drops";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        sourceKey = new NamespacedKey(this, "source_spawner");
        dataFile = new File(getDataFolder(), "spawner-data.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);

        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();

        Bukkit.getPluginManager().registerEvents(this, this);

        long interval = Math.max(1, getConfig().getLong("payout-interval-seconds", 15)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::paySpawnerIncome, interval, interval);

        getLogger().info("LegacySpawnerBank enabled. Existing Skript bank was not changed.");
        if (economy == null) getLogger().warning("Vault economy provider not found.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        event.setCancelled(true);
        String id = key(block.getLocation());
        ensureRecord(id, spawner.getSpawnedType());
        openMainMenu(event.getPlayer(), id, spawner.getSpawnedType());
    }

    private void openMainMenu(Player player, String id, EntityType type) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
        inv.setItem(4, makeItem(Material.SPAWNER,
                ChatColor.WHITE + type.name() + " Spawner",
                "Each placed spawner has its own storage."));
        inv.setItem(10, makeItem(Material.CHEST, ChatColor.GOLD + "Mob Drops",
                "Open this spawner's drop storage."));
        inv.setItem(12, makeItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Stored Money",
                "Collect this spawner's stored money."));
        inv.setItem(14, makeItem(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Stored XP",
                "Collect this spawner's stored XP."));
        inv.setItem(16, makeItem(Material.EMERALD, ChatColor.AQUA + "Sell Spawner",
                "Sell the placed spawner and its stack."));
        menus.put(player.getUniqueId(), id);
        dropMenus.remove(player.getUniqueId());
        player.openInventory(inv);
    }

    private void openDropMenu(Player player, String id) {
        Inventory inv = Bukkit.createInventory(null, 54, DROP_TITLE);
        ConfigurationSection section = data.getConfigurationSection(id + ".items");
        if (section != null) {
            for (String slotText : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotText);
                    ItemStack item = section.getItemStack(slotText);
                    if (slot >= 0 && slot < inv.getSize() && item != null) {
                        inv.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        dropMenus.put(player.getUniqueId(), id);
        menus.remove(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerMobSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        Location loc = event.getLocation();
        Block nearest = findNearestSpawner(loc, event.getEntityType());
        if (nearest == null) return;

        String id = key(nearest.getLocation());
        ensureRecord(id, event.getEntityType());
        event.getEntity().getPersistentDataContainer()
                .set(sourceKey, PersistentDataType.STRING, id);
    }

    private Block findNearestSpawner(Location loc, EntityType type) {
        World world = loc.getWorld();
        if (world == null) return null;

        int radius = Math.max(1, getConfig().getInt("spawner-attribution-radius", 8));
        Block nearest = null;
        double best = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x*x + y*y + z*z > radius*radius) continue;
                    Block block = world.getBlockAt(
                            loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    if (!(block.getState() instanceof CreatureSpawner spawner)) continue;
                    if (spawner.getSpawnedType() != type) continue;

                    double distance = block.getLocation().distanceSquared(loc);
                    if (distance < best) {
                        best = distance;
                        nearest = block;
                    }
                }
            }
        }
        return nearest;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String id = entity.getPersistentDataContainer()
                .get(sourceKey, PersistentDataType.STRING);
        if (id == null || !data.contains(id)) return;

        for (ItemStack drop : new ArrayList<>(event.getDrops())) {
            storeDrop(id, drop.clone());
        }
        event.getDrops().clear();

        int xp = event.getDroppedExp();
        if (xp > 0) {
            data.set(id + ".xp", data.getInt(id + ".xp", 0) + xp);
            event.setDroppedExp(0);
        }
        saveData();
    }

    private void storeDrop(String id, ItemStack drop) {
        ConfigurationSection section = data.getConfigurationSection(id + ".items");
        if (section == null) section = data.createSection(id + ".items");

        for (int slot = 0; slot < 54; slot++) {
            String key = String.valueOf(slot);
            ItemStack existing = section.getItemStack(key);

            if (existing == null || existing.getType().isAir()) {
                section.set(key, drop);
                return;
            }

            if (existing.isSimilar(drop)
                    && existing.getAmount() + drop.getAmount() <= existing.getMaxStackSize()) {
                existing.setAmount(existing.getAmount() + drop.getAmount());
                section.set(key, existing);
                return;
            }
        }

        Location loc = parseLocation(id);
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc.add(0.5, 1, 0.5), drop);
        }
    }

    private void paySpawnerIncome() {
        ConfigurationSection prices = getConfig().getConfigurationSection("spawners");
        if (prices == null) return;

        for (String id : new HashSet<>(data.getKeys(false))) {
            Location loc = parseLocation(id);
            if (loc == null || loc.getWorld() == null) continue;
            if (!(loc.getBlock().getState() instanceof CreatureSpawner spawner)) continue;

            ConfigurationSection cfg =
                    prices.getConfigurationSection(spawner.getSpawnedType().name());
            if (cfg == null) continue;

            int min = cfg.getInt("payout-min", 0);
            int max = Math.max(min, cfg.getInt("payout-max", min));
            int stack = getSpawnerStack(loc.getBlock());
            long total = 0;

            for (int i = 0; i < stack; i++) {
                total += ThreadLocalRandom.current().nextInt(min, max + 1);
            }

            data.set(id + ".money", data.getDouble(id + ".money", 0) + total);
        }
        saveData();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (menus.containsKey(uuid) && event.getView().getTitle().equals(MAIN_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

            String id = menus.get(uuid);
            switch (slot) {
                case 10 -> openDropMenu(player, id);
                case 12 -> collectMoney(player, id);
                case 14 -> collectXp(player, id);
                case 16 -> sellSpawner(player, id);
                default -> { }
            }
            return;
        }

        if (dropMenus.containsKey(uuid) && event.getView().getTitle().equals(DROP_TITLE)) {
            int slot = event.getRawSlot();
            if (slot >= event.getView().getTopInventory().getSize()) {
                if (event.isShiftClick()) event.setCancelled(true);
            } else if (event.isShiftClick() || event.getClick().isKeyboardClick()
                    || event.getClick().isCreativeAction()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String id = dropMenus.remove(uuid);

        if (id != null && event.getView().getTitle().equals(DROP_TITLE)) {
            ConfigurationSection old = data.getConfigurationSection(id + ".items");
            if (old != null) data.set(id + ".items", null);

            ConfigurationSection section = data.createSection(id + ".items");
            Inventory inv = event.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    section.set(String.valueOf(slot), item);
                }
            }
            saveData();
        }

        menus.remove(uuid);
    }

    private void collectMoney(Player player, String id) {
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Vault economy is unavailable.");
            return;
        }

        double amount = data.getDouble(id + ".money", 0);
        if (amount <= 0) {
            player.sendMessage(ChatColor.YELLOW + "No money is stored here.");
            return;
        }

        data.set(id + ".money", 0);
        saveData();
        economy.depositPlayer(player, amount);
        player.sendMessage(ChatColor.GREEN + "Collected $" + String.format(Locale.US, "%.2f", amount));
        player.closeInventory();
    }

    private void collectXp(Player player, String id) {
        int xp = data.getInt(id + ".xp", 0);
        if (xp <= 0) {
            player.sendMessage(ChatColor.YELLOW + "No XP is stored here.");
            return;
        }

        data.set(id + ".xp", 0);
        saveData();
        player.giveExp(xp);
        player.sendMessage(ChatColor.GREEN + "Collected " + xp + " XP.");
        player.closeInventory();
    }

    private void sellSpawner(Player player, String id) {
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Vault economy is unavailable.");
            return;
        }

        Location loc = parseLocation(id);
        if (loc == null || !(loc.getBlock().getState() instanceof CreatureSpawner spawner)) {
            player.sendMessage(ChatColor.RED + "This spawner no longer exists.");
            player.closeInventory();
            return;
        }

        ConfigurationSection cfg =
                getConfig().getConfigurationSection("spawners." + spawner.getSpawnedType().name());
        if (cfg == null) {
            player.sendMessage(ChatColor.RED + "This spawner type has no sale price.");
            return;
        }

        int stack = getSpawnerStack(loc.getBlock());
        double amount = cfg.getDouble("shop-price", 0) * stack;
        if (amount <= 0) return;

        data.set(id, null);
        saveData();
        loc.getBlock().setType(Material.AIR, false);
        economy.depositPlayer(player, amount);
        player.sendMessage(ChatColor.GREEN + "Sold " + stack + " "
                + spawner.getSpawnedType().name() + " spawner(s) for $"
                + String.format(Locale.US, "%.2f", amount));
        player.closeInventory();
    }

    private int getSpawnerStack(Block block) {
        try {
            Class<?> api = Class.forName("com.bgsoftware.wildstacker.api.WildStackerAPI");
            Method method = api.getMethod("getSpawnersAmount", CreatureSpawner.class);
            Object result = method.invoke(null, (CreatureSpawner) block.getState());
            if (result instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Unable to read WildStacker stack count; using 1.");
        }
        return 1;
    }

    private void ensureRecord(String id, EntityType type) {
        if (data.contains(id)) return;
        data.set(id + ".type", type.name());
        data.set(id + ".money", 0.0);
        data.set(id + ".xp", 0);
        data.createSection(id + ".items");
        saveData();
    }

    private ItemStack makeItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(ChatColor.GRAY + lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX()
                + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocation(String id) {
        try {
            String[] parts = id.split(",");
            if (parts.length != 4) return null;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save spawner data: " + e.getMessage());
        }
    }
}
