package net.dark.threecore.rtp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpManager implements Listener {
    private static final String ITEM_KEY = "3smpcore_rtp_item";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final java.util.Set<UUID> searching = new java.util.HashSet<>();

    public RtpManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() {
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            Text.send(sender, "<green>RTP reloaded.</green>");
            return;
        }
        if (args.length > 0) teleport(player, args[0]);
        else open(player);
    }

    public boolean teleport(Player player) {
        return teleport(player, resolveTargetWorld(player));
    }

    public boolean teleport(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Text.send(player, "<red>No RTP world configured or loaded:</red> <white>" + worldName + "</white>");
            return false;
        }

        String key = "worlds." + world.getName().toLowerCase(Locale.ROOT);
        long cooldown = configs.get("world/rtp.yml").getLong(key + ".cooldown-seconds", configs.get("world/rtp.yml").getLong("defaults.cooldown-seconds", 300L));
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cooldown * 1000L - (now - last);
        if (remaining > 0) {
            Text.send(player, "<red>RTP cooldown: " + (remaining / 1000L) + "s.</red>");
            return false;
        }
        if (!searching.add(player.getUniqueId())) {
            Text.send(player, "<yellow>Already searching for a safe RTP location.</yellow>");
            return false;
        }
        int maxAttempts = configs.get("world/rtp.yml").getInt(key + ".max-attempts", configs.get("world/rtp.yml").getInt("defaults.max-attempts", 20));
        int minRadius = configs.get("world/rtp.yml").getInt(key + ".min-radius", configs.get("world/rtp.yml").getInt("defaults.min-radius", 1000));
        int maxRadius = configs.get("world/rtp.yml").getInt(key + ".max-radius", configs.get("world/rtp.yml").getInt("defaults.max-radius", 5000));
        WorldBounds bounds = bounds(world, minRadius, maxRadius);

        Text.send(player, "<gray>Searching for a safe survival RTP location...</gray>");
        CompletableFuture
                .supplyAsync(() -> candidates(bounds, maxAttempts))
                .thenAccept(candidates -> Bukkit.getScheduler().runTask(plugin, () -> finishTeleport(player, world, candidates)));
        return true;
    }

    private String resolveTargetWorld(Player player) {
        String current = player.getWorld().getName().toLowerCase(Locale.ROOT);
        String explicit = configs.get("world/rtp.yml").getString("worlds." + current + ".world", "");
        if (explicit != null && !explicit.isBlank()) return explicit;
        String survivalWorld = configs.get("world/survival.yml").getString("world", "");
        if (survivalWorld != null && !survivalWorld.isBlank()) return survivalWorld;
        String configuredDefault = configs.get("world/rtp.yml").getString("default-world", "");
        if (configuredDefault != null && !configuredDefault.isBlank()) return configuredDefault;
        return player.getWorld().getName();
    }

    private WorldBounds bounds(World world, int minRadius, int maxRadius) {
        org.bukkit.WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        int requested = Math.max(1, Math.max(minRadius, maxRadius));
        int borderLimit = Math.max(1, (int) Math.floor(border.getSize() / 2.0D) - 16);
        int radius = Math.min(requested, borderLimit);
        int minX = (int) Math.floor(center.getX() - radius);
        int maxX = (int) Math.ceil(center.getX() + radius);
        int minZ = (int) Math.floor(center.getZ() - radius);
        int maxZ = (int) Math.ceil(center.getZ() + radius);
        return new WorldBounds((int) Math.round(center.getX()), (int) Math.round(center.getZ()), Math.max(0, Math.min(minRadius, radius)), minX, maxX, minZ, maxZ);
    }

    private List<int[]> candidates(WorldBounds bounds, int maxAttempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<int[]> out = new ArrayList<>(Math.max(1, maxAttempts));
        int attempts = 0;
        int minDistanceSquared = bounds.minRadius() * bounds.minRadius();
        while (out.size() < maxAttempts && attempts++ < Math.max(maxAttempts * 4, 16)) {
            int x = random.nextInt(bounds.minX(), bounds.maxX() + 1);
            int z = random.nextInt(bounds.minZ(), bounds.maxZ() + 1);
            int dx = x - bounds.centerX();
            int dz = z - bounds.centerZ();
            if (minDistanceSquared > 0 && dx * dx + dz * dz < minDistanceSquared) continue;
            out.add(new int[]{x, z});
        }
        return out;
    }

    private void finishTeleport(Player player, World world, List<int[]> candidates) {
        searching.remove(player.getUniqueId());
        if (!player.isOnline()) return;
        Location location = findSafe(world, candidates);
        if (location == null) {
            cooldowns.remove(player.getUniqueId());
            Text.send(player, "<red>Could not find a safe location.</red>");
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        Text.send(player, "<green>Teleported randomly in survival.</green>");
    }

    public void giveItem(Player player) {
        if (!configs.get("world/rtp.yml").getBoolean("hotbar.enabled", true)) return;
        if (!isSurvivalFamily(player.getWorld())) {
            clearItem(player);
            return;
        }
        int slot = configs.get("world/rtp.yml").getInt("hotbar.slot", 8);
        ItemStack item = new ItemStack(org.bukkit.Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(configs.get("world/rtp.yml").getString("hotbar.name", "<gradient:#22c55e:#38bdf8>Random Teleport</gradient>")));
        meta.lore(List.of(Text.mm("<gray>Choose Overworld, Nether, or End.</gray>")));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, ITEM_KEY), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
    }

    private void clearItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isRtpItem(item)) player.getInventory().setItem(i, null);
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.RTP_MAIN, "rtp"), 27, "Random Teleport");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, icon(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(11, icon(org.bukkit.Material.GRASS_BLOCK, "<green>Overworld</green>"));
        inv.setItem(13, icon(org.bukkit.Material.NETHERRACK, "<red>Nether</red>"));
        inv.setItem(15, icon(org.bukkit.Material.END_STONE, "<light_purple>End</light_purple>"));
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot) {
        String base = configs.get("world/survival.yml").getString("world", "world");
        if (slot == 11) teleport(player, base);
        else if (slot == 13) teleport(player, base + "_nether");
        else if (slot == 15) teleport(player, base + "_the_end");
        player.closeInventory();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isRtpItem(event.getItem())) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && isRtpItem(event.getCurrentItem())) event.setCancelled(true);
    }

    private Location findSafe(World world, List<int[]> candidates) {
        for (int[] pair : candidates) {
            int x = pair[0];
            int z = pair[1];
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            org.bukkit.Material ground = loc.clone().subtract(0, 1, 0).getBlock().getType();
            org.bukkit.Material feet = loc.getBlock().getType();
            org.bukkit.Material head = loc.clone().add(0, 1, 0).getBlock().getType();
            if (!ground.isSolid() || unsafe(ground) || unsafe(feet) || unsafe(head)) continue;
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) return loc;
        }
        return null;
    }

    private boolean unsafe(org.bukkit.Material material) {
        if (material == null) return true;
        String name = material.name();
        return name.contains("WATER") || name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA") || name.contains("CACTUS") || name.contains("POWDER_SNOW");
    }

    private boolean isRtpItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, ITEM_KEY), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private ItemStack icon(org.bukkit.Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSurvivalFamily(World world) {
        if (world == null) return false;
        String base = configs.get("world/survival.yml").getString("world", "world");
        String name = world.getName();
        return name.equalsIgnoreCase(base) || name.equalsIgnoreCase(base + "_nether") || name.equalsIgnoreCase(base + "_the_end");
    }

    private record WorldBounds(int centerX, int centerZ, int minRadius, int minX, int maxX, int minZ, int maxZ) {}
}



