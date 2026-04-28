package net.dark.threecore.social;

import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SocialTabService implements Listener {
    public enum TabViewMode {
        GLOBAL,
        PARTY,
        DUNGEON;

        public TabViewMode next() {
            return switch (this) {
                case GLOBAL -> PARTY;
                case PARTY -> DUNGEON;
                case DUNGEON -> GLOBAL;
            };
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, TabViewMode> modes = new ConcurrentHashMap<>();
    private PartyService partyService;
    private DungeonService dungeonService;

    public SocialTabService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(PartyService partyService, DungeonService dungeonService) {
        this.partyService = partyService;
        this.dungeonService = dungeonService;
    }

    public TabViewMode mode(UUID uuid) {
        return modes.getOrDefault(uuid, TabViewMode.GLOBAL);
    }

    public void cycle(Player player) {
        modes.put(player.getUniqueId(), mode(player.getUniqueId()).next());
        refresh(player);
        refreshAll();
    }

    public void refresh(Player viewer) {
        if (!isTabPluginPresent()) {
            viewer.sendPlayerListHeaderAndFooter(
                    Text.mm("<gradient:#5B8DD9:#F8FBFF><bold>3SMP</bold></gradient>\n<gray>" + title(viewer.getUniqueId()) + "</gray>"),
                    Text.mm("<#D6E8F7>Discord</#D6E8F7> <dark_gray>|</dark_gray> <#F8FBFF>Store</#F8FBFF> <dark_gray>|</dark_gray> <#D6E8F7>/help</#D6E8F7>")
            );
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (shouldShow(viewer, target)) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }
        if (!isTabPluginPresent()) updateNames();
    }

    public void refreshAll() {
        if (!isTabPluginPresent()) updateNames();
        for (Player viewer : Bukkit.getOnlinePlayers()) refresh(viewer);
    }

    public void refreshPair(UUID uuid) {
        Player viewer = Bukkit.getPlayer(uuid);
        if (viewer != null) refresh(viewer);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(uuid)) refresh(online);
        }
    }

    public String modeName(UUID uuid) {
        return mode(uuid).name().toLowerCase(Locale.ROOT);
    }

    public String title(UUID uuid) {
        return switch (mode(uuid)) {
            case PARTY -> "Party View";
            case DUNGEON -> "Dungeon View";
            case GLOBAL -> "Global View";
        };
    }

    public String members(UUID uuid) {
        return switch (mode(uuid)) {
            case PARTY -> partyMembers(uuid);
            case DUNGEON -> dungeonMembers(uuid);
            case GLOBAL -> globalMembers();
        };
    }

    public String partyMembers(UUID uuid) {
        if (partyService == null || !partyService.isInParty(uuid)) return "No party";
        return partyService.partyMembers(uuid).stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    public String dungeonMembers(UUID uuid) {
        if (dungeonService == null) return "No dungeon team";
        List<String> names = dungeonService.activeMemberNames(uuid);
        return names.isEmpty() ? "No dungeon team" : String.join(", ", names);
    }

    public String globalMembers() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        cycle(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(event.getPlayer().getUniqueId())) refresh(online);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        modes.remove(event.getPlayer().getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(event.getPlayer().getUniqueId())) refresh(online);
        }
    }

    public boolean shouldShow(Player viewer, Player target) {
        if (viewer.hasPermission("3smpcore.tab.bypass")) return true;
        return switch (mode(viewer.getUniqueId())) {
            case GLOBAL -> true;
            case PARTY -> partyService != null && partyService.isInParty(viewer.getUniqueId()) && partyService.partyMembers(viewer.getUniqueId()).contains(target.getUniqueId());
            case DUNGEON -> dungeonService != null && dungeonService.isInActiveDungeon(viewer.getUniqueId()) && dungeonService.isInActiveDungeon(target.getUniqueId());
        };
    }

    private void updateNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(Component.text()
                    .append(rankDot(player))
                    .append(Component.text(" " + player.getName()))
                    .build());
        }
    }

    private Component rankDot(Player player) {
        String group = primaryGroup(player);
        return switch (group.toLowerCase(Locale.ROOT)) {
            case "owner", "admin" -> Text.mm("<#F87171>●</#F87171>");
            case "ultra" -> Text.mm("<#F59E0B>●</#F59E0B>");
            case "mvp" -> Text.mm("<#C084FC>●</#C084FC>");
            case "pro" -> Text.mm("<#5B8DD9>●</#5B8DD9>");
            case "vip", "3rank" -> Text.mm("<#D6E8F7>●</#D6E8F7>");
            default -> Text.mm("<#F8FBFF>●</#F8FBFF>");
        };
    }

    private String primaryGroup(Player player) {
        try {
            org.bukkit.plugin.Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPerms == null || !luckPerms.isEnabled()) return "member";
            Object api = luckPerms.getClass().getMethod("getApi").invoke(luckPerms);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "member";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object group = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            return group == null ? "member" : group.toString();
        } catch (Throwable ignored) {
            return "member";
        }
    }

    private boolean isTabPluginPresent() {
        org.bukkit.plugin.Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        return tab != null && tab.isEnabled();
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString() : offline.getName();
    }
}
