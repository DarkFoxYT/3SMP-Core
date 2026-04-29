package net.dark.threecore.npc;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CitizensNpcPresetManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public CitizensNpcPresetManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "<yellow>/3smpcore npc place <id> | remove | list</yellow>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> Text.send(sender, "<gray>NPC presets:</gray> <white>" + String.join(", ", ids()) + "</white>");
            case "place" -> {
                if (!(sender instanceof Player player)) {
                    Text.send(sender, "<red>Players only.</red>");
                    return;
                }
                if (args.length < 2) {
                    Text.send(sender, "<red>Usage: /3smpcore npc place <id></red>");
                    return;
                }
                place(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "remove", "delete" -> removeSelected(sender);
            default -> Text.send(sender, "<yellow>/3smpcore npc place <id> | remove | list</yellow>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("place", "remove", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) return ids();
        return List.of();
    }

    private void place(Player player, String id) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            Text.send(player, "<red>Citizens is not installed/enabled.</red>");
            return;
        }
        ConfigurationSection section = configs.get("world/npcs.yml").getConfigurationSection("presets." + id);
        if (section == null) {
            Text.send(player, "<red>Unknown NPC preset.</red>");
            return;
        }
        boolean wasOp = player.isOp();
        try {
            if (section.getBoolean("temporary-op", true) && !wasOp) player.setOp(true);
            run(player, "npc create " + quote(section.getString("name", id)) + " --type " + section.getString("type", "PLAYER"));
            if (section.getBoolean("lookclose", true)) run(player, "npc lookclose");
            if (section.getBoolean("protected", true)) run(player, "npc vulnerable false");
            String skin = section.getString("skin", "");
            if (skin != null && !skin.isBlank()) run(player, "npc skin " + skin);
            for (String line : section.getStringList("hologram")) run(player, "npc hologram add " + line);
            for (String command : section.getStringList("commands")) {
                if (!command.isBlank()) run(player, "npc command add -p " + command.replace("{player}", "<p>"));
            }
            Text.send(player, "<green>Created Citizens NPC preset:</green> <white>" + id + "</white>");
        } finally {
            if (!wasOp && player.isOp()) player.setOp(false);
        }
    }

    private void removeSelected(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only. Select an NPC first, then run this.</red>");
            return;
        }
        boolean wasOp = player.isOp();
        try {
            if (!wasOp) player.setOp(true);
            run(player, "npc remove");
            Text.send(player, "<green>Removed selected Citizens NPC.</green>");
        } finally {
            if (!wasOp && player.isOp()) player.setOp(false);
        }
    }

    private void run(Player player, String command) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    private String quote(String value) {
        String safe = value == null ? "" : value.replace("\"", "'");
        return "\"" + safe + "\"";
    }

    private List<String> ids() {
        ConfigurationSection section = configs.get("world/npcs.yml").getConfigurationSection("presets");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }
}
