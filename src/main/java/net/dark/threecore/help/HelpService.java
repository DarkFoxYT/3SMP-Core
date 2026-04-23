package net.dark.threecore.help;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HelpService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public HelpService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void handle(CommandSender sender, String[] args) {
        String pageId = args.length == 0 ? configs.get("core/help.yml").getString("settings.default-page", "main") : args[0].toLowerCase(Locale.ROOT);
        ConfigurationSection page = configs.get("core/help.yml").getConfigurationSection("pages." + pageId);
        if (page == null) {
            Text.send(sender, configs.get("core/help.yml").getString("messages.unknown-page", "<red>That help page does not exist.</red>"));
            return;
        }
        String permission = page.getString("permission", "");
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            Text.send(sender, configs.get("core/help.yml").getString("messages.no-permission", "<red>You cannot view that help page.</red>"));
            return;
        }
        for (String line : page.getStringList("lines")) {
            Text.raw(sender, replace(line, sender));
        }
    }

    public List<String> complete(String[] args) {
        if (args.length > 1) return List.of();
        ConfigurationSection pages = configs.get("core/help.yml").getConfigurationSection("pages");
        if (pages == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (String id : pages.getKeys(false)) ids.add(id.toLowerCase(Locale.ROOT));
        return ids;
    }

    private String replace(String input, CommandSender sender) {
        return input
                .replace("{player}", sender.getName())
                .replace("{server}", configs.get("core/help.yml").getString("settings.server-name", "3SMP"))
                .replace("{version}", plugin.getPluginMeta().getVersion());
    }
}
