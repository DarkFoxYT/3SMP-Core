package net.dark.threecore.fixes;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ThreeSMPGeneralFixes implements Listener {
    private static final String RTP_ITEM_KEY = "3smpcore_rtp_item";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Set<String> SAFE_WORLDS = Set.of("spawn", "world", "world_nether", "world_the_end", "market");
    private static boolean installed;

    private final JavaPlugin plugin;
    private final File snapshotsFile;
    private final YamlConfiguration snapshots;

    private ThreeSMPGeneralFixes(JavaPlugin plugin) {
        this.plugin = plugin;
        this.snapshotsFile = new File(plugin.getDataFolder(), "player/inventory-snapshots.yml");
        this.snapshots = YamlConfiguration.loadConfiguration(this.snapshotsFile);
    }

    public static void install(JavaPlugin plugin) {
        if (installed || plugin == null) {
            return;
        }
        installed = true;
        ThreeSMPGeneralFixes fixes = new ThreeSMPGeneralFixes(plugin);
        Bukkit.getPluginManager().registerEvents(fixes, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, fixes::snapshotSafePlayers, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, fixes::normalizeSurvivalPlayers, 40L, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, fixes::applyLuckPermsBootstrap, 80L);
        plugin.getLogger().info("3SMP general fixes enabled inside 3SMPCore.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Player player = event.getPlayer();
            this.ensureMemberRank(player);
            this.normalizeIfSurvival(player);
            this.removeRtpCompass(player);
            this.snapshotIfSafe(player);
            this.restoreIfBadDuelExit(player);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.snapshotIfSafe(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (this.isSafeWorld(event.getFrom())) {
            this.snapshotIfSafe(player);
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.normalizeIfSurvival(player);
            this.removeRtpCompass(player);
            this.restoreIfBadDuelExit(player);
            this.snapshotIfSafe(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onNpcInteractEarly(PlayerInteractEntityEvent event) {
        this.allowNpcInteraction(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcInteractLate(PlayerInteractEntityEvent event) {
        this.allowNpcInteraction(event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onNpcInteractAtEarly(PlayerInteractAtEntityEvent event) {
        this.allowNpcInteraction(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcInteractAtLate(PlayerInteractAtEntityEvent event) {
        this.allowNpcInteraction(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSurvivalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!this.isSurvivalWorld(player.getWorld())) {
            return;
        }
        player.setInvulnerable(false);
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void snapshotSafePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.snapshotIfSafe(player);
        }
    }

    private void normalizeSurvivalPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.normalizeIfSurvival(player);
            this.removeRtpCompass(player);
            this.restoreIfBadDuelExit(player);
        }
    }

    private void snapshotIfSafe(Player player) {
        if (player == null || !player.isOnline() || !this.isSafeWorld(player.getWorld()) || this.isLikelyDuelInventory(player)) {
            return;
        }
        String base = "players." + player.getUniqueId();
        this.snapshots.set(base + ".world", player.getWorld().getName());
        this.snapshots.set(base + ".contents", player.getInventory().getContents());
        this.snapshots.set(base + ".armor", player.getInventory().getArmorContents());
        this.snapshots.set(base + ".offhand", player.getInventory().getItemInOffHand());
        this.snapshots.set(base + ".gamemode", player.getGameMode().name());
        this.saveSnapshots();
    }

    private void restoreIfBadDuelExit(Player player) {
        if (player == null || !player.isOnline() || !this.isSafeWorld(player.getWorld())) {
            return;
        }
        if (!this.isLikelyDuelInventory(player)) {
            return;
        }
        String base = "players." + player.getUniqueId();
        if (!this.snapshots.isSet(base + ".contents")) {
            return;
        }
        player.getInventory().setContents(this.listToArray(this.snapshots.getList(base + ".contents"), 41));
        player.getInventory().setArmorContents(this.listToArray(this.snapshots.getList(base + ".armor"), 4));
        player.getInventory().setItemInOffHand(this.snapshots.getItemStack(base + ".offhand"));
        player.setItemOnCursor(null);
        this.normalizeIfSurvival(player);
        player.updateInventory();
        this.plugin.getLogger().info("Restored safe inventory snapshot for " + player.getName() + " after duel exit.");
    }

    private ItemStack[] listToArray(List<?> list, int max) {
        ItemStack[] out = new ItemStack[max];
        if (list == null) {
            return out;
        }
        for (int i = 0; i < Math.min(max, list.size()); i++) {
            Object value = list.get(i);
            if (value instanceof ItemStack item) {
                out[i] = item;
            }
        }
        return out;
    }

    private boolean isLikelyDuelInventory(Player player) {
        int nonAir = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                nonAir++;
            }
        }
        if (nonAir == 0) {
            return true;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return this.isSafeWorld(player.getWorld()) && (player.getGameMode() == GameMode.SPECTATOR || world.startsWith("arena_") || world.startsWith("duel_"));
    }

    private void normalizeIfSurvival(Player player) {
        if (player == null || !player.isOnline() || !this.isSurvivalWorld(player.getWorld())) {
            return;
        }
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCollidable(true);
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private boolean isSafeWorld(World world) {
        return world != null && SAFE_WORLDS.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    private boolean isSurvivalWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("world") || name.equals("world_nether") || name.equals("world_the_end");
    }

    private void ensureMemberRank(Player player) {
        if (player != null && player.isOnline()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " parent add member");
        }
    }

    private void allowNpcInteraction(PlayerInteractEntityEvent event) {
        if (event == null || !this.isCitizensNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(false);
    }

    private boolean isCitizensNpc(Entity entity) {
        return entity != null && (entity.hasMetadata("NPC") || entity.getScoreboardTags().contains("CITIZENS_NPC"));
    }

    private void removeRtpCompass(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (this.isRtpCompass(item)) {
                player.getInventory().setItem(i, null);
                changed = true;
            }
        }
        if (this.isRtpCompass(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private boolean isRtpCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(new NamespacedKey(this.plugin, RTP_ITEM_KEY), PersistentDataType.BYTE)) {
            return true;
        }
        if (meta.displayName() == null) {
            return false;
        }
        String plain = PLAIN.serialize(meta.displayName()).toLowerCase(Locale.ROOT);
        return plain.contains("random teleport") || plain.contains("rtp");
    }

    private void applyLuckPermsBootstrap() {
        CommandSender console = Bukkit.getConsoleSender();
        String[] commands = new String[] {
            "lp group default parent add member",
            "lp group default permission unset 3smpcore.backpack.use",
            "lp group default permission unset 3smpcore.backpack.limit.1",
            "lp group default permission unset 3smpcore.backpack.limit.2",
            "lp group default permission unset 3smpcore.backpack.limit.3",
            "lp group default permission unset 3smpcore.backpack.limit.10",
            "lp group default permission unset 3smpcore.backpack.limit.unlimited",
            "lp group default permission unset 3smpcore.chat.hex",
            "lp group default permission unset 3smpcore.chat.gradient",
            "lp group default permission unset 3smpcore.visual.*",
            "lp group member permission set 3smpcore.perks.use true",
            "lp group member permission set 3smpcore.back.use true",
            "lp group member permission unset 3smpcore.backpack.use",
            "lp group member permission unset 3smpcore.backpack.limit.1",
            "lp group member permission unset 3smpcore.backpack.limit.2",
            "lp group member permission unset 3smpcore.backpack.limit.3",
            "lp group member permission unset 3smpcore.backpack.limit.10",
            "lp group member permission unset 3smpcore.backpack.limit.unlimited",
            "lp group member permission unset 3smpcore.chat.hex",
            "lp group member permission unset 3smpcore.chat.gradient",
            "lp group member permission unset 3smpcore.visual.*",
            "lp group member permission set 3smpcore.tpa.use true",
            "lp group member permission set 3smpcore.home.use true",
            "lp group member permission set 3smpcore.home.set true",
            "lp group member permission set 3smpcore.home.delete true",
            "lp group 3 permission set 3smpcore.backpack.use true",
            "lp group 3 permission set 3smpcore.backpack.limit.1 true",
            "lp group 3 permission set 3smpcore.chat.hex true",
            "lp group 3 permission set 3smpcore.chat.gradient true",
            "lp group 3 permission set 3smpcore.visual.* true",
            "lp group pro permission set 3smpcore.backpack.use true",
            "lp group pro permission set 3smpcore.backpack.limit.1 true",
            "lp group pro permission set 3smpcore.chat.hex true",
            "lp group pro permission set 3smpcore.chat.gradient true",
            "lp group pro permission set 3smpcore.visual.* true",
            "lp group mvp permission set 3smpcore.backpack.use true",
            "lp group mvp permission set 3smpcore.backpack.limit.2 true",
            "lp group mvp permission set 3smpcore.chat.hex true",
            "lp group mvp permission set 3smpcore.chat.gradient true",
            "lp group mvp permission set 3smpcore.visual.* true",
            "lp group ultra permission set 3smpcore.backpack.use true",
            "lp group ultra permission set 3smpcore.backpack.limit.3 true",
            "lp group ultra permission set 3smpcore.chat.hex true",
            "lp group ultra permission set 3smpcore.chat.gradient true",
            "lp group ultra permission set 3smpcore.visual.* true",
            "lp group patron permission set 3smpcore.backpack.use true",
            "lp group patron permission set 3smpcore.backpack.limit.10 true",
            "lp group patron permission set 3smpcore.chat.hex true",
            "lp group patron permission set 3smpcore.chat.gradient true",
            "lp group patron permission set 3smpcore.visual.* true",
            "lp group admin permission set 3smpcore.backpack.use true",
            "lp group admin permission set 3smpcore.backpack.limit.10 true",
            "lp group admin permission set 3smpcore.chat.hex true",
            "lp group admin permission set 3smpcore.chat.gradient true",
            "lp group admin permission set 3smpcore.visual.* true",
            "lp group sr-admin permission set 3smpcore.backpack.use true",
            "lp group sr-admin permission set 3smpcore.backpack.limit.10 true",
            "lp group sr-admin permission set 3smpcore.chat.hex true",
            "lp group sr-admin permission set 3smpcore.chat.gradient true",
            "lp group sr-admin permission set 3smpcore.visual.* true",
            "lp group sradmin permission set 3smpcore.backpack.use true",
            "lp group sradmin permission set 3smpcore.backpack.limit.10 true",
            "lp group sradmin permission set 3smpcore.chat.hex true",
            "lp group sradmin permission set 3smpcore.chat.gradient true",
            "lp group sradmin permission set 3smpcore.visual.* true",
            "lp group dev permission set 3smpcore.backpack.use true",
            "lp group dev permission set 3smpcore.backpack.limit.10 true",
            "lp group dev permission set 3smpcore.chat.hex true",
            "lp group dev permission set 3smpcore.chat.gradient true",
            "lp group dev permission set 3smpcore.visual.* true",
            "lp group mod permission set 3smpcore.backpack.use true",
            "lp group mod permission set 3smpcore.backpack.limit.10 true",
            "lp group mod permission set 3smpcore.chat.hex true",
            "lp group mod permission set 3smpcore.chat.gradient true",
            "lp group mod permission set 3smpcore.visual.* true",
            "lp group sr-mod permission set 3smpcore.backpack.use true",
            "lp group sr-mod permission set 3smpcore.backpack.limit.10 true",
            "lp group sr-mod permission set 3smpcore.chat.hex true",
            "lp group sr-mod permission set 3smpcore.chat.gradient true",
            "lp group sr-mod permission set 3smpcore.visual.* true",
            "lp group jr-mod permission set 3smpcore.backpack.use true",
            "lp group jr-mod permission set 3smpcore.backpack.limit.10 true",
            "lp group jr-mod permission set 3smpcore.chat.hex true",
            "lp group jr-mod permission set 3smpcore.chat.gradient true",
            "lp group jr-mod permission set 3smpcore.visual.* true",
            "lp group builder permission set 3smpcore.backpack.use true",
            "lp group builder permission set 3smpcore.backpack.limit.10 true",
            "lp group builder permission set 3smpcore.chat.hex true",
            "lp group builder permission set 3smpcore.chat.gradient true",
            "lp group builder permission set 3smpcore.visual.* true",
            "lp group owner permission set 3smpcore.backpack.use true",
            "lp group owner permission set 3smpcore.backpack.limit.unlimited true",
            "lp group owner permission set 3smpcore.chat.hex true",
            "lp group owner permission set 3smpcore.chat.gradient true",
            "lp group owner permission set 3smpcore.visual.* true",
            "lp group h318 permission set 3smpcore.backpack.use true",
            "lp group h318 permission set 3smpcore.backpack.limit.unlimited true",
            "lp group h318 permission set 3smpcore.chat.hex true",
            "lp group h318 permission set 3smpcore.chat.gradient true",
            "lp group h318 permission set 3smpcore.visual.* true"
        };
        for (String command : commands) {
            Bukkit.dispatchCommand(console, command);
        }
    }

    private void saveSnapshots() {
        try {
            File parent = this.snapshotsFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            this.snapshots.save(this.snapshotsFile);
        } catch (Exception ex) {
            this.plugin.getLogger().warning("Failed to save inventory snapshots: " + ex.getMessage());
        }
    }
}
