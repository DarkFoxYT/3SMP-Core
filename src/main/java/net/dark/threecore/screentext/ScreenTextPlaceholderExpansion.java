package net.dark.threecore.screentext;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScreenTextPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final ScreenTextManager manager;

    public ScreenTextPlaceholderExpansion(JavaPlugin plugin, ScreenTextManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "3smpcore_screentext";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "dark" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (manager == null || params == null) return "";
        String key = params.toLowerCase(java.util.Locale.ROOT);
        if (key.equals("templates")) return String.valueOf(manager.registry().templateCount());
        if (key.equals("template_list")) return String.join(", ", manager.registry().templateIds());
        if (key.equals("active")) return player != null && player.isOnline() ? String.valueOf(manager.activeCount(player.getPlayer())) : "0";
        if (key.startsWith("has_template_")) return String.valueOf(manager.registry().template(key.substring("has_template_".length())) != null);
        return "";
    }
}
