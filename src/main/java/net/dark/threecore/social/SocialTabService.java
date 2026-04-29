package net.dark.threecore.social;

import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

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

    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final ChatFormatService chatFormatService;
    private final Map<UUID, TabViewMode> modes = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> scoreboards = new ConcurrentHashMap<>();
    private PartyService partyService;
    private DungeonService dungeonService;
    private BukkitTask refreshTask;

    public SocialTabService(JavaPlugin plugin, ConfigFiles configs, ChatFormatService chatFormatService) {
        this.plugin = plugin;
        this.configs = configs;
        this.chatFormatService = chatFormatService;
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
        if (shouldApplyVisuals()) {
            applyHeaderFooter(viewer);
            applySidebar(viewer);
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (shouldShow(viewer, target)) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }
        if (shouldApplyVisuals()) updateNames();
    }

    public void refreshAll() {
        startVisualTask();
        if (shouldApplyVisuals()) updateNames();
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
        if (!config().getBoolean("friends.tab.sneak-cycle-enabled", true)) return;
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
        scoreboards.remove(event.getPlayer().getUniqueId());
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

    private void applyHeaderFooter(Player viewer) {
        viewer.sendPlayerListHeaderAndFooter(
                componentFromLines(viewer, config().getStringList("friends.tab.header"), config().getString("friends.tab.shadow", "")),
                componentFromLines(viewer, config().getStringList("friends.tab.footer"), config().getString("friends.tab.shadow", ""))
        );
    }

    private void updateNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String format = rankFormat(player, "player-format", config().getString("friends.tab.player-format", "<rank_image> <tab_display>"));
            player.playerListName(deserialize(player, format));
            player.displayName(deserialize(player, format));
            player.customName(deserialize(player, rankFormat(player, "nametag-display", format)));
            player.setCustomNameVisible(false);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = scoreboards.get(viewer.getUniqueId());
            if (board != null) applyNametagTeams(viewer, board);
        }
    }

    private void applySidebar(Player player) {
        if (!config().getBoolean("friends.scoreboards.enabled", true)) return;
        String boardId = config().getString("friends.scoreboards.active", "default");
        String path = "friends.scoreboards.boards." + boardId;
        List<String> lines = config().getStringList(path + ".lines");
        if (lines.isEmpty()) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = scoreboards.computeIfAbsent(player.getUniqueId(), ignored -> manager.getNewScoreboard());
        Objective old = board.getObjective("smpcore");
        if (old != null) old.unregister();
        Objective objective = board.registerNewObjective("smpcore", "dummy", deserialize(player, config().getString(path + ".title", "&6&l3SMP")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = Math.min(15, lines.size());
        for (String raw : lines.stream().limit(15).toList()) {
            String line = safeScoreboardLine(board, plainLegacy(player, raw));
            objective.getScore(line).setScore(score--);
        }
        applyNametagTeams(player, board);
        player.setScoreboard(board);
    }

    private void applyNametagTeams(Player viewer, Scoreboard board) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            String teamName = "smp_" + target.getUniqueId().toString().replace("-", "").substring(0, 12);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.prefix(deserialize(target, rankFormat(target, "nametag-prefix", config().getString("friends.tab.nametag-prefix", "<rank_image> <tab_prefix> "))));
            team.suffix(deserialize(target, rankFormat(target, "nametag-suffix", config().getString("friends.tab.nametag-suffix", " <tab_tag>"))));
            if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
        }
    }

    private String uniqueScoreboardLine(Scoreboard board, String line) {
        String out = line.length() > 40 ? line.substring(0, 40) : line;
        while (board.getEntries().contains(out) && out.length() < 40) out += "§r";
        return out;
    }

    private String plainLegacy(Player player, String input) {
        String replaced = replaceTokens(player, applyPlaceholders(player, input));
        return cleanColorizeLegacy(replaced);
    }

    private String safeScoreboardLine(Scoreboard board, String line) {
        String out = line.length() > 40 ? line.substring(0, 40) : line;
        while (board.getEntries().contains(out) && out.length() < 40) out += "\u00A7r";
        return out;
    }

    private Component componentFromLines(Player player, List<String> lines, String shadow) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out = out.append(Component.newline());
            out = out.append(deserialize(player, lines.get(i), shadow));
        }
        return out;
    }

    private Component deserialize(Player player, String input) {
        return deserialize(player, input, null);
    }

    private Component deserialize(Player player, String input, String overrideShadow) {
        if (input == null || input.isBlank()) return Component.empty();
        String replaced = applyPlaceholders(player, replaceTokens(player, applyPlaceholders(player, input)));
        ShadowColor shadow = parseShadow(overrideShadow == null || overrideShadow.isBlank() ? rankFormat(player, "shadow", config().getString("friends.tab.shadow", "")) : overrideShadow);
        replaced = stripShadowTags(replaced);
        try {
            return applyShadow(LEGACY_AMP.deserialize(cleanColorizeLegacy(renderGradientMarkers(player, stripMiniMessageTags(replaced)))), shadow);
        } catch (Exception ignored) {
        }
        try {
            return applyShadow(LEGACY_AMP.deserialize(cleanColorizeLegacy(stripMiniMessageTags(replaced))), shadow);
        } catch (Exception ignored) {
            return applyShadow(Component.text(stripMiniMessageTags(replaced)), shadow);
        }
    }

    private String replaceTokens(Player player, String input) {
        if (input == null) return "";
        String display = chatFormatService == null ? player.getName() : chatFormatService.tabDisplay(player);
        String prefix = chatFormatService == null ? "" : chatFormatService.tabPrefix(player);
        String name = chatFormatService == null ? player.getName() : chatFormatService.tabName(player);
        String tag = chatFormatService == null ? "" : chatFormatService.tabTag(player);
        String group = primaryGroup(player).toLowerCase(Locale.ROOT);
        String rankImage = rankFormat(player, "image", "");
        String rankName = rankFormat(player, "name-format", "<tab_name>");
        return input
                .replace("<rank_dot>", rankDotMini(player))
                .replace("<rank_image>", rankImage)
                .replace("<rank>", escapeMini(group))
                .replace("<rank_name>", rankName.replace("<player>", player.getName()))
                .replace("<tab_display>", legacyToMini(display, display.contains("<")))
                .replace("<tab_prefix>", legacyToMini(prefix, prefix.contains("<")))
                .replace("<tab_name>", legacyToMini(name, name.contains("<")))
                .replace("<tab_tag>", legacyToMini(tag, tag.contains("<")))
                .replace("<player>", escapeMini(player.getName()))
                .replace("<online>", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("<max_players>", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("<tab_mode>", modeName(player.getUniqueId()))
                .replace("<tab_title>", title(player.getUniqueId()))
                .replace("<tab_members>", escapeMini(members(player.getUniqueId())));
    }

    private String rankDotMini(Player player) {
        String group = primaryGroup(player).toLowerCase(Locale.ROOT);
        String configured = config().getString("friends.tab.rank-dots." + group, "");
        if (configured != null && !configured.isBlank()) return sanitizeConfiguredRankDot(configured);
        return switch (group) {
            case "owner", "admin" -> "<#F87171>●</#F87171>";
            case "ultra" -> "<#F59E0B>●</#F59E0B>";
            case "mvp" -> "<#C084FC>●</#C084FC>";
            case "pro" -> "<#5B8DD9>●</#5B8DD9>";
            case "vip", "3rank", "3smp" -> "<#D6E8F7>●</#D6E8F7>";
            default -> "<#F8FBFF>●</#F8FBFF>";
        };
    }

    private String rankFormat(Player player, String key, String fallback) {
        String group = primaryGroup(player).toLowerCase(Locale.ROOT);
        String value = config().getString("friends.tab.ranks." + group + "." + key, null);
        if (value == null || value.isBlank()) value = config().getString("friends.tab.ranks.default." + key, fallback);
        return value == null ? "" : value;
    }

    private String primaryGroup(Player player) {
        try {
            org.bukkit.plugin.Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPerms == null || !luckPerms.isEnabled()) return "member";
            Object api = luckPerms.getClass().getMethod("getApi").invoke(luckPerms);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "member";
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? "member" : group.toString();
        } catch (Throwable ignored) {
            return "member";
        }
    }

    private boolean shouldApplyVisuals() {
        if (!config().getBoolean("friends.tab.visuals.enabled", true)) return false;
        if (!isTabPluginPresent()) return true;
        return config().getBoolean("friends.tab.visuals.override-tab-plugin", true) || config().getBoolean("friends.tab.visuals.force-over-tab-plugin", true);
    }

    private boolean isTabPluginPresent() {
        org.bukkit.plugin.Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        return tab != null && tab.isEnabled();
    }

    private String applyPlaceholders(Player player, String input) {
        if (input == null || input.isBlank()) return input;
        input = input
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%world%", player.getWorld() == null ? "" : player.getWorld().getName())
                .replace("%ping%", String.valueOf(player.getPing()))
                .replace("%player_name%", player.getName());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return input;
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) placeholderApi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class).invoke(null, player, input);
        } catch (Throwable ignored) {
            return input;
        }
    }

    private String legacyToMini(String input) {
        return legacyToMini(input, false);
    }

    private String legacyToMini(String input, boolean preserveMiniTags) {
        if (input == null || input.isBlank()) return "";
        input = normalizeLegacyHex(input).replace('\u00A7', '&').replaceAll("(?i)&?#([A-F0-9]{6})", "<#$1>");
        if (preserveMiniTags) {
            return input.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                    .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                    .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                    .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                    .replace("&f", "<white>").replace("&l", "<bold>").replace("&o", "<italic>")
                    .replace("&n", "<underlined>").replace("&m", "<strikethrough>").replace("&r", "<reset>");
        }
        return input.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&o", "<italic>")
                .replace("&n", "<underlined>").replace("&m", "<strikethrough>").replace("&r", "<reset>");
    }

    private String fixMiniMessage(String input) {
        if (input == null || input.isBlank()) return "";
        return input.replaceAll("(?i)</#([A-F0-9]{6})>", "</color>");
    }

    private Component applyShadow(Component component, ShadowColor shadow) {
        return shadow == null ? component : component.shadowColor(shadow);
    }

    private ShadowColor parseShadow(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("(?i)<shadow:?#?([A-F0-9]{6})(?::([0-9.]+))?>").matcher(value);
        if (tag.find()) return shadowColor(tag.group(1), tag.group(2));
        java.util.regex.Matcher shortTag = java.util.regex.Pattern.compile("(?i)#?([A-F0-9]{6})(?::([0-9.]+))?").matcher(value);
        return shortTag.find() ? shadowColor(shortTag.group(1), shortTag.group(2)) : null;
    }

    private ShadowColor shadowColor(String hex, String alphaRaw) {
        int red = Integer.parseInt(hex.substring(0, 2), 16);
        int green = Integer.parseInt(hex.substring(2, 4), 16);
        int blue = Integer.parseInt(hex.substring(4, 6), 16);
        int alpha = 255;
        if (alphaRaw != null && !alphaRaw.isBlank()) {
            try {
                double parsed = Double.parseDouble(alphaRaw);
                alpha = parsed <= 1.0 ? (int) Math.round(parsed * 255.0) : (int) Math.round(parsed);
            } catch (NumberFormatException ignored) {
                alpha = 255;
            }
        }
        return ShadowColor.shadowColor(red, green, blue, Math.max(0, Math.min(255, alpha)));
    }

    private String colorizeLegacy(String input) {
        if (input == null || input.isBlank()) return "";
        String out = input.replaceAll("(?i)&#([A-F0-9]{6})", "§x§$1");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("§x§([A-Fa-f0-9]{6})").matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    private void startVisualTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!shouldApplyVisuals()) return;
            updateNames();
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyHeaderFooter(player);
                applySidebar(player);
            }
        }, 20L, Math.max(10L, config().getLong("friends.tab.visuals.refresh-ticks", 40L)));
    }

    private String normalizeLegacyHex(String input) {
        if (input == null || input.isBlank()) return "";
        String out = input.replace("Â", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:&|\\u00A7)x((?:&|\\u00A7)[A-F0-9]){6}").matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group().replace("&x", "").replace("\u00A7x", "").replace("&", "").replace("\u00A7", "");
            matcher.appendReplacement(buffer, "&#" + hex);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String cleanColorizeLegacy(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = normalizeLegacyHex(input);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&#([A-F0-9]{6})").matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(buffer, legacyHex(matcher.group(1)));
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '\u00A7');
    }

    private String renderGradientMarkers(Player player, String input) {
        if (input == null || input.isBlank()) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{grad:([A-Za-z0-9_-]+)}(.*?)\\{/grad}", java.util.regex.Pattern.CASE_INSENSITIVE);
        String out = input;
        java.util.regex.Matcher matcher = pattern.matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String[] colors = gradientColors(player, matcher.group(1));
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(legacyGradient(matcher.group(2), colors[0], colors[1])));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String[] gradientColors(Player player, String id) {
        String key = id == null || id.isBlank() || id.equalsIgnoreCase("rank") ? primaryGroup(player).toLowerCase(Locale.ROOT) : id.toLowerCase(Locale.ROOT);
        String configured = config().getString("friends.tab.gradients." + key, config().getString("friends.tab.gradients.default", "#6aaed6:#1fb8e0"));
        String[] parts = configured.split(":", 2);
        return new String[] { parts.length > 0 ? parts[0] : "#6aaed6", parts.length > 1 ? parts[1] : "#1fb8e0" };
    }

    private String legacyGradient(String text, String fromHex, String toHex) {
        if (text == null || text.isEmpty()) return "";
        int[] from = rgb(fromHex);
        int[] to = rgb(toHex);
        StringBuilder out = new StringBuilder();
        int visible = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            double t = visible == 0 ? 0.0 : (double) i / visible;
            int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
            int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
            int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
            out.append(legacyHex(String.format("%02x%02x%02x", r, g, b))).append(text.charAt(i));
        }
        return out.toString();
    }

    private int[] rgb(String hex) {
        String cleaned = hex == null ? "ffffff" : hex.replace("#", "");
        if (cleaned.length() != 6) cleaned = "ffffff";
        return new int[] {
                Integer.parseInt(cleaned.substring(0, 2), 16),
                Integer.parseInt(cleaned.substring(2, 4), 16),
                Integer.parseInt(cleaned.substring(4, 6), 16)
        };
    }

    private String legacyHex(String hex) {
        StringBuilder out = new StringBuilder("\u00A7x");
        for (char c : hex.toCharArray()) out.append('\u00A7').append(c);
        return out.toString();
    }

    private String stripMiniMessageTags(String input) {
        return input == null ? "" : input.replaceAll("<[^>]+>", "");
    }

    private String stripShadowTags(String input) {
        return input == null ? "" : input.replaceAll("(?i)</?shadow(?::[^>]+)?>", "");
    }

    private String sanitizeConfiguredRankDot(String configured) {
        return configured.contains("â") || configured.contains("�") ? "<#F8FBFF>*</#F8FBFF>" : configured;
    }

    private String escapeMini(String input) {
        if (input == null) return "";
        return input.replace("<", "&lt;").replace(">", "&gt;");
    }

    private YamlConfiguration config() {
        return configs.get("social/friends.yml");
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString() : offline.getName();
    }
}
