package net.dark.threecore.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.warp.WarpManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmpCoreExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final PerkService perkService;
    private final WarpManager warpManager;
    private final SpawnService spawnService;
    private final MoneyService moneyService;

    public SmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService) {
        this.plugin = plugin;
        this.perkService = perkService;
        this.warpManager = warpManager;
        this.spawnService = spawnService;
        this.moneyService = moneyService;
    }

    @Override public String getIdentifier() { return "smpcore"; }
    @Override public String getAuthor() { return plugin.getDescription().getAuthors().isEmpty() ? "dark" : plugin.getDescription().getAuthors().get(0); }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean canRegister() { return true; }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        var data = perkService.data(player.getUniqueId());
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "tag" -> data.activeTag();
            case "tag_id" -> data.activeTag();
            case "has_tag" -> String.valueOf(!data.activeTag().isBlank());
            case "tag_priority" -> String.valueOf(priority(data.activeTag()));
            case "available_tags" -> String.valueOf(data.unlockedPerks().size());
            case "spawn_set" -> String.valueOf(spawnService.getSpawnLocation() != null);
            case "spawn_world" -> spawnService.getSpawnLocation() != null && spawnService.getSpawnLocation().getWorld() != null ? spawnService.getSpawnLocation().getWorld().getName() : "";
            case "spawn_x" -> spawnService.getSpawnLocation() != null ? String.valueOf(spawnService.getSpawnLocation().getX()) : "0";
            case "spawn_y" -> spawnService.getSpawnLocation() != null ? String.valueOf(spawnService.getSpawnLocation().getY()) : "0";
            case "spawn_z" -> spawnService.getSpawnLocation() != null ? String.valueOf(spawnService.getSpawnLocation().getZ()) : "0";
            case "spawn_yaw" -> spawnService.getSpawnLocation() != null ? String.valueOf(spawnService.getSpawnLocation().getYaw()) : "0";
            case "jobs_count" -> String.valueOf(0);
            case "money", "balance" -> String.valueOf(moneyService.balance(player.getUniqueId()));
            case "money_formatted", "balance_formatted" -> moneyService.format(moneyService.balance(player.getUniqueId()));
            case "cosmetic" -> data.activeCosmetic();
            case "cosmetic_id" -> data.activeCosmetic();
            case "has_cosmetic" -> String.valueOf(!data.activeCosmetic().isBlank());
            default -> {
                if (params.startsWith("job_")) yield jobPlaceholder(data, params);
                yield "";
            }
        };
    }

    private String jobPlaceholder(net.dark.threecore.model.PlayerProgressionData data, String params) { return "0"; }
    private int priority(String tag) { return tag == null || tag.isBlank() ? 0 : 1; }
}
