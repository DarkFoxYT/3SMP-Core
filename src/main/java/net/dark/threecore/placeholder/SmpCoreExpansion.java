package net.dark.threecore.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.social.SocialTabService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.warp.WarpManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class SmpCoreExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final PerkService perkService;
    private final WarpManager warpManager;
    private final SpawnService spawnService;
    private final MoneyService moneyService;
    private final SapphireService sapphireService;
    private final PartyService partyService;
    private final DungeonService dungeonService;
    private final FriendService friendService;
    private final SocialTabService socialTabService;

    public SmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService, SapphireService sapphireService, PartyService partyService, DungeonService dungeonService, FriendService friendService, SocialTabService socialTabService) {
        this.plugin = plugin;
        this.perkService = perkService;
        this.warpManager = warpManager;
        this.spawnService = spawnService;
        this.moneyService = moneyService;
        this.sapphireService = sapphireService;
        this.partyService = partyService;
        this.dungeonService = dungeonService;
        this.friendService = friendService;
        this.socialTabService = socialTabService;
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
            case "sapphires", "sapphire_balance" -> String.valueOf(sapphireService.balance(player.getUniqueId()));
            case "duel_winstreak" -> String.valueOf(data.duelWinStreak());
            case "duel_best_winstreak" -> String.valueOf(data.duelBestWinStreak());
            case "cosmetic" -> data.activeCosmetic();
            case "cosmetic_id" -> data.activeCosmetic();
            case "has_cosmetic" -> String.valueOf(!data.activeCosmetic().isBlank());
            case "party_size" -> partyService == null ? "0" : String.valueOf(partyService.partySize(player.getUniqueId()));
            case "party_members" -> partyService == null ? "" : partyService.partyMembers(player.getUniqueId()).stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).reduce((a, b) -> a + ", " + b).orElse("");
            case "dungeon_members" -> dungeonService == null ? "" : String.join(", ", dungeonService.activeMemberNames(player.getUniqueId()));
            case "friends_count" -> friendService == null ? "0" : String.valueOf(friendService.count(player.getUniqueId()));
            case "friends_list" -> friendService == null ? "" : friendService.friendList(player.getUniqueId());
            case "tab_mode" -> socialTabService == null ? "global" : socialTabService.modeName(player.getUniqueId());
            case "tab_title" -> socialTabService == null ? "Global View" : socialTabService.title(player.getUniqueId());
            case "tab_members" -> socialTabService == null ? "" : socialTabService.members(player.getUniqueId());
            default -> {
                if (params.startsWith("job_")) yield jobPlaceholder(data, params);
                yield "";
            }
        };
    }

    private String nameOf(java.util.UUID uuid) {
        var online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString() : offline.getName();
    }

    private String jobPlaceholder(net.dark.threecore.model.PlayerProgressionData data, String params) { return "0"; }
    private int priority(String tag) { return tag == null || tag.isBlank() ? 0 : 1; }
}
