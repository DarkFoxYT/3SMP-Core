package net.dark.threecore.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.social.SocialTabService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.visual.VisualManager;
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
    private final ChatFormatService chatFormatService;
    private final DuelService duelService;
    private final VisualManager visualManager;

    public SmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService, SapphireService sapphireService, PartyService partyService, DungeonService dungeonService, FriendService friendService, SocialTabService socialTabService, ChatFormatService chatFormatService) {
        this(plugin, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService, null);
    }

    public SmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService, SapphireService sapphireService, PartyService partyService, DungeonService dungeonService, FriendService friendService, SocialTabService socialTabService, ChatFormatService chatFormatService, DuelService duelService) {
        this(plugin, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService, duelService, null);
    }

    public SmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService, SapphireService sapphireService, PartyService partyService, DungeonService dungeonService, FriendService friendService, SocialTabService socialTabService, ChatFormatService chatFormatService, DuelService duelService, VisualManager visualManager) {
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
        this.chatFormatService = chatFormatService;
        this.duelService = duelService;
        this.visualManager = visualManager;
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
            case "duel_enabled" -> duelService == null ? "false" : "true";
            case "duel_solo_queue" -> duelService == null ? "0" : String.valueOf(duelService.soloQueueCount());
            case "duel_party_queue" -> duelService == null ? "0" : String.valueOf(duelService.partyQueueCount());
            case "duel_queue_mode" -> duelService == null ? "none" : duelService.queueModeName(player.getUniqueId());
            case "duel_queue_kit" -> duelService == null ? "none" : duelService.queueKitName(player.getUniqueId());
            case "duel_queue_summary" -> duelService == null ? "none" : duelService.queueSummary(player.getUniqueId());
            case "duel_mmr" -> duelService == null ? "0" : String.valueOf(duelService.rankedMmr(player.getUniqueId(), ""));
            case "duel_rank" -> duelService == null ? "" : duelService.rankedRank(player.getUniqueId(), "");
            case "duel_rank_display" -> duelService == null ? "" : duelService.rankedRankDisplay(player.getUniqueId(), "");
            case "cosmetic" -> data.activeCosmetic();
            case "cosmetic_id" -> data.activeCosmetic();
            case "has_cosmetic" -> String.valueOf(!data.activeCosmetic().isBlank());
            case "party_size" -> partyService == null ? "0" : String.valueOf(partyService.partySize(player.getUniqueId()));
            case "party_members" -> partyService == null ? "" : partyService.partyMembers(player.getUniqueId()).stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).reduce((a, b) -> a + ", " + b).orElse("");
            case "dungeon_members" -> dungeonService == null ? "" : String.join(", ", dungeonService.activeMemberNames(player.getUniqueId()));
            case "dungeon_ready_count" -> player.isOnline() && dungeonService != null ? dungeonService.dungeonPlaceholder(player.getPlayer(), "ready_count") : "0";
            case "dungeon_total_players" -> player.isOnline() && dungeonService != null ? dungeonService.dungeonPlaceholder(player.getPlayer(), "total_players") : "0";
            case "dungeon_countdown" -> player.isOnline() && dungeonService != null ? dungeonService.dungeonPlaceholder(player.getPlayer(), "countdown") : "0";
            case "dungeon_room" -> player.isOnline() && dungeonService != null ? dungeonService.dungeonPlaceholder(player.getPlayer(), "room") : "";
            case "dungeon_floor" -> player.isOnline() && dungeonService != null ? dungeonService.dungeonPlaceholder(player.getPlayer(), "floor") : "0";
            case "friends_count" -> friendService == null ? "0" : String.valueOf(friendService.count(player.getUniqueId()));
            case "friends_list" -> friendService == null ? "" : friendService.friendList(player.getUniqueId());
            case "tab_mode" -> socialTabService == null ? "global" : socialTabService.modeName(player.getUniqueId());
            case "tab_title" -> socialTabService == null ? "Global View" : socialTabService.title(player.getUniqueId());
            case "tab_members" -> socialTabService == null ? "" : socialTabService.members(player.getUniqueId());
            case "chat_prefix", "tab_prefix", "luckperms_prefix" -> player.isOnline() && chatFormatService != null ? chatFormatService.tabPrefix(player.getPlayer()) : "";
            case "chat_name" -> player.isOnline() && chatFormatService != null ? chatFormatService.tabName(player.getPlayer()) : player.getName() == null ? "" : player.getName();
            case "tab_name" -> player.isOnline() && visualManager != null ? visualManager.tabName(player.getPlayer()) : player.isOnline() && chatFormatService != null ? chatFormatService.tabName(player.getPlayer()) : player.getName() == null ? "" : player.getName();
            case "chat_tag", "tab_tag" -> player.isOnline() && chatFormatService != null ? chatFormatService.tabTag(player.getPlayer()) : "";
            case "chat_display", "tab_display" -> player.isOnline() && chatFormatService != null ? chatFormatService.tabDisplay(player.getPlayer()) : player.getName() == null ? "" : player.getName();
            case "rank_id" -> player.isOnline() && visualManager != null ? visualManager.style(player.getPlayer()).id() : "default";
            case "rank_image" -> player.isOnline() && visualManager != null ? visualManager.style(player.getPlayer()).image() : "";
            case "rank_prefix" -> player.isOnline() && visualManager != null ? visualManager.style(player.getPlayer()).prefix() : "";
            case "rank_tab_prefix" -> player.isOnline() && visualManager != null ? visualManager.style(player.getPlayer()).tabPrefix() : "";
            case "rank_gradient" -> player.isOnline() && visualManager != null ? visualManager.style(player.getPlayer()).gradient() : "default";
            case "scoreboard_rank" -> player.isOnline() && visualManager != null ? visualManager.scoreboardRank(player.getPlayer()) : "";
            default -> {
                if (params.startsWith("job_")) yield jobPlaceholder(data, params);
                if (params.startsWith("duel_kit_queue_")) yield duelService == null ? "0" : String.valueOf(duelService.queuedPlayersForKit(params.substring("duel_kit_queue_".length())));
                if (duelService != null) {
                    String lower = params.toLowerCase(java.util.Locale.ROOT);
                    if (lower.startsWith("duel_mmr_")) yield String.valueOf(duelService.rankedMmr(player.getUniqueId(), params.substring("duel_mmr_".length())));
                    if (lower.startsWith("duel_rank_display_")) yield duelService.rankedRankDisplay(player.getUniqueId(), params.substring("duel_rank_display_".length()));
                    if (lower.startsWith("duel_rank_")) yield duelService.rankedRank(player.getUniqueId(), params.substring("duel_rank_".length()));
                    if (lower.startsWith("duel_ranked_wins_")) yield String.valueOf(duelService.rankedWins(player.getUniqueId(), params.substring("duel_ranked_wins_".length())));
                    if (lower.startsWith("duel_ranked_losses_")) yield String.valueOf(duelService.rankedLosses(player.getUniqueId(), params.substring("duel_ranked_losses_".length())));
                    if (lower.startsWith("duel_kit_wins_")) yield String.valueOf(duelService.duelKitWins(player.getUniqueId(), params.substring("duel_kit_wins_".length())));
                    if (lower.startsWith("duel_kit_losses_")) yield String.valueOf(duelService.duelKitLosses(player.getUniqueId(), params.substring("duel_kit_losses_".length())));
                    if (lower.startsWith("duel_kit_streak_")) yield String.valueOf(duelService.duelKitStreak(player.getUniqueId(), params.substring("duel_kit_streak_".length())));
                    if (lower.startsWith("duel_kit_best_streak_")) yield String.valueOf(duelService.duelKitBestStreak(player.getUniqueId(), params.substring("duel_kit_best_streak_".length())));
                }
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
