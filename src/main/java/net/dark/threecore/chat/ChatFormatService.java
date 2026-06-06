package net.dark.threecore.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.text.Text;
import net.dark.threecore.visual.VisualManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ChatFormatService implements Listener {
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final ConfigFiles configs;
    private final PerkService perkService;
    private VisualManager visualManager;
    private DuelService duelService;

    public ChatFormatService(JavaPlugin plugin, ConfigFiles configs, PerkService perkService) {
        this.configs = configs;
        this.perkService = perkService;
    }

    public void setVisualManager(VisualManager visualManager) {
        this.visualManager = visualManager;
    }

    public void setDuelService(DuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        isolateDuelChat(event, player);
        var data = perkService.data(player.getUniqueId());
        RankChatStyle style = resolveRankStyle(player);
        Component prefix = visualManager == null ? resolvePrefix(player) : visualManager.renderedPrefix(player);
        Component tag = Component.empty();
        Component name = visualManager == null ? styledPlayerName(player, style) : visualManager.renderedPlayerName(player);
        String messageColor = resolveMessageColor(player, data.activeMessageColor(), style);
        event.renderer((source, sourceDisplayName, chatMessage, viewer) -> joinChatParts(prefix, name, tag, chatMessage(player, chatMessage, messageColor)));
    }

    private void isolateDuelChat(AsyncChatEvent event, Player source) {
        if (source == null || source.getWorld() == null) return;
        boolean sourceDuelWorld = isDuelChatWorld(source.getWorld());
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer) || viewer.getWorld() == null) return false;
            boolean viewerDuelWorld = isDuelChatWorld(viewer.getWorld());
            if (viewerDuelWorld != sourceDuelWorld) return true;
            return sourceDuelWorld
                    && configs.get("social/friends.yml").getBoolean("friends.tab.duels.same-world-only", true)
                    && !viewer.getWorld().getName().equalsIgnoreCase(source.getWorld().getName());
        });
    }

    private boolean isDuelChatWorld(org.bukkit.World world) {
        if (world == null) return false;
        if (duelService != null && duelService.isDuelIsolatedWorld(world)) return true;
        String name = world.getName();
        return configs.get("social/friends.yml").getStringList("friends.tab.duels.worlds").stream().anyMatch(configured -> configured.equalsIgnoreCase(name));
    }

    private Component chatMessage(Player player, Component chatMessage, String messageColor) {
        if (messageColor != null && !messageColor.isBlank() && !messageColor.equalsIgnoreCase("default")) {
            return colorize(messageColor, chatMessage);
        }
        return visualManager == null ? chatMessage : visualManager.renderedChatMessage(player, chatMessage);
    }

    public String tabPrefix(Player player) {
        return serializeForPlaceholder(resolvePrefix(player));
    }

    public String tabName(Player player) {
        if (visualManager != null) return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(visualManager.renderedPlayerName(player));
        return serializeForPlaceholder(styledPlayerName(player, resolveRankStyle(player)));
    }

    public String tabTag(Player player) {
        return "";
    }

    public String tabDisplay(Player player) {
        String prefix = tabPrefix(player);
        String name = tabName(player);
        String tag = tabTag(player);
        StringBuilder builder = new StringBuilder();
        if (!prefix.isBlank()) builder.append(prefix);
        if (!name.isBlank()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append(name);
        }
        if (!tag.isBlank()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append(tag);
        }
        return builder.toString();
    }

    private Component joinChatParts(Component prefix, Component name, Component tag, Component message) {
        List<Component> leading = new ArrayList<>();
        if (!isBlank(prefix)) leading.add(prefix);
        if (!isBlank(name)) leading.add(name);
        if (!isBlank(tag)) leading.add(tag);

        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < leading.size(); i++) {
            if (i > 0) builder.append(Component.space());
            builder.append(leading.get(i));
        }
        return builder.append(Component.text(": ", NamedTextColor.GRAY)).append(message).build();
    }

    private boolean isBlank(Component component) {
        return component == null || component.equals(Component.empty()) || PLAIN.serialize(component).isBlank();
    }

    private Component colorize(String id, Component message) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) return message;
        ConfigurationSection sec = configs.get("cosmetics/colors.yml").getConfigurationSection("colors." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return message;
        String type = sec.getString("type", "hex").toLowerCase(Locale.ROOT);
        String content = PlainTextComponentSerializer.plainText().serialize(message);
        try {
            if ("gradient".equals(type)) {
                return Text.mm("<gradient:" + gradientStops(sec) + ">" + escapeMini(content) + "</gradient>");
            }
            if ("none".equals(type)) return message;
            String hex = sec.getString("hex", null);
            if (hex == null || hex.isBlank()) return message;
            TextColor color = TextColor.fromHexString(hex);
            return color == null ? message : Component.text(content, color);
        } catch (Exception ignored) {
            return message;
        }
    }

    private String resolveMessageColor(Player player, String selected, RankChatStyle style) {
        if (selected != null && !selected.isBlank() && !selected.equalsIgnoreCase("default") && canUseSelectedColor(player, selected)) {
            return selected;
        }
        if (configs.get("cosmetics/colors.yml").getBoolean("allow-rank-chat-colors", false) && style.messageColorId() != null && !style.messageColorId().isBlank()) {
            return style.messageColorId();
        }
        if (!configs.get("cosmetics/colors.yml").getBoolean("allow-rank-chat-colors", false)) return "default";
        ConfigurationSection ranks = configs.get("cosmetics/colors.yml").getConfigurationSection("rank-colors");
        if (ranks == null) return "default";
        for (String id : ranks.getKeys(false)) {
            String permission = ranks.getString(id + ".permission", "");
            String color = ranks.getString(id + ".color", "");
            String requiredRank = ranks.getString(id + ".required-rank", id);
            if (matchesRank(player, requiredRank) || (!permission.isBlank() && player.hasPermission(permission))) {
                if (!color.isBlank()) return color;
            }
        }
        return "default";
    }

    private boolean canUseSelectedColor(Player player, String colorId) {
        ConfigurationSection sec = configs.get("cosmetics/colors.yml").getConfigurationSection("colors." + colorId.toLowerCase(Locale.ROOT));
        if (sec == null) return false;
        if (colorId.equalsIgnoreCase("default")) return true;
        if (player.hasPermission("3smpcore.perks.admin") || player.hasPermission("3smpcore.perks.all") || player.hasPermission("3smpcore.admin") || player.isOp()) return true;
        if (!perkService.hasUnlocked(player.getUniqueId(), colorId)) return false;
        String permission = sec.getString("permission", "");
        String requiredRank = sec.getString("required-rank", "");
        boolean hasGate = !permission.isBlank() || !requiredRank.isBlank();
        if (!hasGate) return false;
        return (!permission.isBlank() && player.hasPermission(permission))
                || (!requiredRank.isBlank() && matchesRank(player, requiredRank));
    }

    private boolean matchesRank(Player player, String requiredRank) {
        if (requiredRank == null || requiredRank.isBlank()) return true;
        String primary = luckPermsPrimaryGroup(player.getUniqueId());
        if (primary == null) return false;
        if (primary.equalsIgnoreCase(requiredRank)) return true;
        int currentWeight = rankGateWeight(primary);
        int requiredWeight = rankGateWeight(requiredRank);
        return currentWeight > 0 && requiredWeight > 0 && currentWeight <= requiredWeight;
    }

    private int rankGateWeight(String rank) {
        if (rank == null) return -1;
        return switch (rank.toLowerCase(Locale.ROOT)) {
            case "owner" -> 1;
            case "dev" -> 2;
            case "admin" -> 3;
            case "sr-admin", "sradmin" -> 4;
            case "h318", "mod" -> 5;
            case "sr-mod", "srmod" -> 6;
            case "jr-mod", "jrmod" -> 7;
            case "builder" -> 8;
            case "ultra" -> 10;
            case "patron" -> 15;
            case "mvp" -> 20;
            case "pro" -> 35;
            case "3", "3smp" -> 40;
            case "member", "default" -> 100;
            default -> -1;
        };
    }

    private Component display(Player player, String file, String root, String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) return Component.empty();
        ConfigurationSection sec = configs.get(file).getConfigurationSection(root + "." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return Component.empty();
        String value = applyPlayerPlaceholders(player, sec.getString("preview", sec.getString("display-name", "")));
        if (value == null || value.isBlank() || value.contains("?")) return Component.empty();
        try {
            return deserializeDecoration(value);
        } catch (Exception ignored) {
            return Component.empty();
        }
    }

    private Component resolvePrefix(Player player) {
        String prefix = applyPlayerPlaceholders(player, luckPermsPrefixString(player.getUniqueId()));
        if (prefix != null && !prefix.isBlank()) {
            return deserializeDecoration(prefix);
        }
        String fallback = configs.get("core/config.yml").getString("chat.default-prefix", "<gray>[<white>Member</white>]</gray>");
        try {
            return Text.mm(fallback);
        } catch (Exception ignored) {
            return Component.text("[Member]", NamedTextColor.GRAY);
        }
    }

    private String luckPermsPrefixString(UUID uuid) {
        try {
            Object user = luckPermsUser(uuid);
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            return prefix == null ? "" : prefix.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String luckPermsPrimaryGroup(UUID uuid) {
        try {
            Object user = luckPermsUser(uuid);
            if (user == null) return null;
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? null : group.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object luckPermsUser(UUID uuid) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user != null) return user;
            try {
                return userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid)
                        .getClass().getMethod("join").invoke(userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid));
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Component deserializeDecoration(String input) {
        if (input == null || input.isBlank()) return Component.empty();
        input = input.replace('§', '&');
        try {
            if (input.contains("<") && input.contains(">")) return Text.mm(input);
        } catch (Exception ignored) {
        }
        try {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        } catch (Exception ignored) {
            return Component.text(input, NamedTextColor.WHITE);
        }
    }

    private String escapeMini(String input) {
        if (input == null) return "";
        return input.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String gradientStops(ConfigurationSection section) {
        String from = section.getString("from", section.getString("hex", "#FFFFFF"));
        String middle = section.getString("middle", "");
        String to = section.getString("to", section.getString("hex", "#FFFFFF"));
        return middle == null || middle.isBlank() ? from + ":" + to : from + ":" + middle + ":" + to;
    }

    private String applyPlayerPlaceholders(Player player, String input) {
        if (input == null || input.isBlank()) return input;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return input;
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) placeholderApi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class).invoke(null, player, input);
        } catch (Throwable ignored) {
            return input;
        }
    }

    private String serializeForPlaceholder(Component component) {
        if (component == null || component.equals(Component.empty())) return "";
        return LEGACY_AMP.serialize(component);
    }

    private Component styledPlayerName(Player player, RankChatStyle style) {
        String playerName = applyPlayerPlaceholders(player, player.getName());
        if (style.nameFormat() != null && !style.nameFormat().isBlank()) {
            return deserializeDecoration(style.nameFormat().replace("<player>", escapeMini(playerName)));
        }
        Component base = deserializeDecoration(applyPlayerPlaceholders(player, player.getName()));
        return colorComponent(base, style.nameColorId());
    }

    private Component styledTag(Player player, String activeTag, RankChatStyle style) {
        return Component.empty();
    }

    private Component colorComponent(Component component, String colorId) {
        if (component == null || component.equals(Component.empty())) return Component.empty();
        if (colorId == null || colorId.isBlank() || colorId.equalsIgnoreCase("default")) return component;
        ConfigurationSection sec = configs.get("cosmetics/colors.yml").getConfigurationSection("colors." + colorId.toLowerCase(Locale.ROOT));
        if (sec == null) return component;
        String type = sec.getString("type", "hex").toLowerCase(Locale.ROOT);
        String content = PlainTextComponentSerializer.plainText().serialize(component);
        if (content.isBlank()) return component;
        try {
            if ("gradient".equals(type)) {
                return Text.mm("<gradient:" + gradientStops(sec) + ">" + escapeMini(content) + "</gradient>");
            }
            if ("none".equals(type)) return component;
            String hex = sec.getString("hex", null);
            if (hex == null || hex.isBlank()) return component;
            TextColor color = TextColor.fromHexString(hex);
            return color == null ? component : Component.text(content, color);
        } catch (Exception ignored) {
            return component;
        }
    }

    private RankChatStyle resolveRankStyle(Player player) {
        ConfigurationSection styles = configs.get("core/config.yml").getConfigurationSection("chat.rank-styles");
        if (styles == null) return RankChatStyle.none();
        String primaryGroup = luckPermsPrimaryGroup(player.getUniqueId());
        return styles.getKeys(false).stream()
                .map(key -> createStyle(styles.getConfigurationSection(key), key))
                .filter(style -> style != null && style.matches(player, primaryGroup))
                .max(Comparator.comparingInt(RankChatStyle::priority))
                .orElse(RankChatStyle.none());
    }

    private RankChatStyle createStyle(ConfigurationSection section, String key) {
        if (section == null) return null;
        return new RankChatStyle(
                key,
                section.getInt("priority", 0),
                section.getString("primary-group", section.getString("group", "")),
                section.getString("permission", ""),
                section.getString("name-format", ""),
                section.getString("name-color", ""),
                section.getString("tag-format", ""),
                section.getString("tag-color", ""),
                section.getString("message-color", "")
        );
    }

    private record RankChatStyle(String key, int priority, String primaryGroup, String permission, String nameFormat,
                                 String nameColorId, String tagFormat, String tagColorId, String messageColorId) {
        static RankChatStyle none() {
            return new RankChatStyle("default", Integer.MIN_VALUE, "", "", "", "", "", "", "");
        }

        boolean matches(Player player, String activePrimaryGroup) {
            boolean groupMatches = primaryGroup == null || primaryGroup.isBlank()
                    || (activePrimaryGroup != null && activePrimaryGroup.equalsIgnoreCase(primaryGroup));
            boolean permissionMatches = permission == null || permission.isBlank() || player.hasPermission(permission);
            return groupMatches && permissionMatches;
        }
    }
}
