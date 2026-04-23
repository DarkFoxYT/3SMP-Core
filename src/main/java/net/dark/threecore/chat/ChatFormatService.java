package net.dark.threecore.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.text.Text;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ChatFormatService implements Listener {
    private final ConfigFiles configs;
    private final PerkService perkService;

    public ChatFormatService(JavaPlugin plugin, ConfigFiles configs, PerkService perkService) {
        this.configs = configs;
        this.perkService = perkService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var data = perkService.data(player.getUniqueId());
        Component badge = display("cosmetics/badges.yml", "badges", data.activeBadge());
        Component luckPermsPrefix = luckPermsPrefix(player);
        Component tag = display("cosmetics/tags.yml", "tags", data.activeTag());
        Component name = Component.text(player.getName(), TextColor.fromHexString("#FFFFFF"));
        Component message = colorize(resolveMessageColor(player, data.activeMessageColor()), event.message());
        event.renderer((source, sourceDisplayName, chatMessage, viewer) -> joinChatParts(badge, luckPermsPrefix, name, tag, message));
    }

    private Component joinChatParts(Component badge, Component prefix, Component name, Component tag, Component message) {
        List<Component> leading = new ArrayList<>();
        if (!badge.equals(Component.empty())) leading.add(badge);
        if (!prefix.equals(Component.empty())) leading.add(prefix);
        leading.add(name);
        if (!tag.equals(Component.empty())) leading.add(tag);

        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < leading.size(); i++) {
            if (i > 0) builder.append(Component.space());
            builder.append(leading.get(i));
        }
        return builder.append(Component.text(": ", NamedTextColor.GRAY)).append(message).build();
    }

    private Component colorize(String id, Component message) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) return message;
        ConfigurationSection sec = configs.get("cosmetics/colors.yml").getConfigurationSection("colors." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return message;
        String type = sec.getString("type", "hex").toLowerCase(Locale.ROOT);
        String content = PlainTextComponentSerializer.plainText().serialize(message);
        try {
            if ("gradient".equals(type)) {
                String from = sec.getString("from", sec.getString("hex", "#FFFFFF"));
                String to = sec.getString("to", sec.getString("hex", "#FFFFFF"));
                return Text.mm("<gradient:" + from + ":" + to + ">" + escapeMini(content) + "</gradient>");
            }
            if ("none".equals(type)) return message;
            String hex = sec.getString("hex", null);
            if (hex == null || hex.isBlank()) return message;
            return Text.mm("<" + hex + ">" + escapeMini(content) + "</" + hex + ">");
        } catch (Exception ignored) {
            return message;
        }
    }

    private String resolveMessageColor(Player player, String selected) {
        if (selected != null && !selected.isBlank() && !selected.equalsIgnoreCase("default") && canUseSelectedColor(player, selected)) {
            return selected;
        }
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
        String requiredPermission = sec.getString("required-permission", sec.getString("permission", ""));
        String requiredRank = sec.getString("required-rank", "");
        boolean permissionOk = requiredPermission.isBlank() || player.hasPermission(requiredPermission);
        boolean rankOk = requiredRank.isBlank() || matchesRank(player, requiredRank);
        return permissionOk && rankOk;
    }

    private boolean matchesRank(Player player, String requiredRank) {
        if (requiredRank == null || requiredRank.isBlank()) return true;
        String primary = luckPermsPrimaryGroup(player.getUniqueId());
        return primary != null && primary.equalsIgnoreCase(requiredRank);
    }

    private Component display(String file, String root, String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) return Component.empty();
        ConfigurationSection sec = configs.get(file).getConfigurationSection(root + "." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return Component.empty();
        String value = sec.getString("preview", sec.getString("display-name", ""));
        if (value == null || value.isBlank() || value.contains("?")) return Component.empty();
        try {
            return Text.mm(value);
        } catch (Exception ignored) {
            return Component.empty();
        }
    }

    private Component luckPermsPrefix(Player player) {
        String prefix = luckPermsPrefixString(player.getUniqueId());
        if (prefix == null || prefix.isBlank()) return Component.empty();
        return deserializeDecoration(prefix);
    }

    private String luckPermsPrefixString(UUID uuid) {
        try {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (plugin == null || !plugin.isEnabled()) return "";
            Object api = plugin.getClass().getMethod("getApi").invoke(plugin);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
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
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (plugin == null || !plugin.isEnabled()) return null;
            Object api = plugin.getClass().getMethod("getApi").invoke(plugin);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user == null) return null;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object group = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            return group == null ? null : group.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Component deserializeDecoration(String input) {
        if (input == null || input.isBlank()) return Component.empty();
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
}
