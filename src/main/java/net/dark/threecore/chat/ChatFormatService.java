package net.dark.threecore.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class ChatFormatService implements Listener {
    private final ConfigFiles configs;
    private final PerkService perkService;

    public ChatFormatService(JavaPlugin plugin, ConfigFiles configs, PerkService perkService) {
        this.configs = configs;
        this.perkService = perkService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        var data = perkService.data(player.getUniqueId());
        Component badge = display("badges.yml", "badges", data.activeBadge());
        Component prefix = display("prefixes.yml", "prefixes", data.activePrefix());
        Component tag = display("tags.yml", "tags", data.activeTag());
        Component name = Component.text(player.getName(), TextColor.fromHexString("#FFFFFF"));
        Component message = colorize(data.activeMessageColor(), event.message());
        event.renderer((source, sourceDisplayName, chatMessage, viewer) -> badge
                .append(badge.equals(Component.empty()) ? Component.empty() : Component.space())
                .append(prefix)
                .append(prefix.equals(Component.empty()) ? Component.empty() : Component.space())
                .append(name)
                .append(tag.equals(Component.empty()) ? Component.empty() : Component.space().append(tag))
                .append(Component.text(": "))
                .append(message));
    }

    private Component colorize(String id, Component message) {
        if (id == null || id.isBlank()) return message;
        ConfigurationSection sec = configs.get("colors.yml").getConfigurationSection("colors." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return message;
        String type = sec.getString("type", "hex").toLowerCase(Locale.ROOT);
        String content = PlainTextComponentSerializer.plainText().serialize(message);
        try {
            if ("gradient".equals(type)) {
                String from = sec.getString("from", sec.getString("hex", "#FFFFFF"));
                String to = sec.getString("to", sec.getString("hex", "#FFFFFF"));
                return Text.mm("<gradient:" + from + ":" + to + ">" + escapeMini(content) + "</gradient>");
            }
            String hex = sec.getString("hex", null);
            if (hex == null || hex.isBlank()) return message;
            return Text.mm("<" + hex + ">" + escapeMini(content) + "</" + hex + ">");
        } catch (Exception ignored) {
            return message;
        }
    }

    private Component display(String file, String root, String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) return Component.empty();
        ConfigurationSection sec = configs.get(file).getConfigurationSection(root + "." + id.toLowerCase(Locale.ROOT));
        if (sec == null) return Component.empty();
        String value = sec.getString("preview", sec.getString("display-name", ""));
        if (value == null || value.isBlank()) return Component.empty();
        try {
            return Text.mm(value);
        } catch (Exception ignored) {
            return Component.empty();
        }
    }

    private String escapeMini(String input) {
        if (input == null) return "";
        return input.replace("<", "&lt;").replace(">", "&gt;");
    }
}
