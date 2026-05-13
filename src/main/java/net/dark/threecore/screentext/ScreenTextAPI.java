package net.dark.threecore.screentext;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class ScreenTextAPI {
    private static ScreenTextManager manager;

    private ScreenTextAPI() {
    }

    static void install(ScreenTextManager installed) {
        manager = installed;
    }

    public static ScreenTextManager manager() {
        return manager;
    }

    public static void show(Player player, ScreenText text) {
        if (manager != null) manager.show(player, text);
    }

    public static boolean show(Player player, String templateId) {
        return manager != null && manager.show(player, templateId);
    }

    public static void update(Player player, ScreenText text) {
        if (manager != null) manager.update(player, text);
    }

    public static void remove(Player player, String id) {
        if (manager != null) manager.remove(player, id);
    }

    public static void clear(Player player) {
        if (manager != null) manager.clear(player);
    }

    public static void broadcast(ScreenText text) {
        if (manager != null) manager.broadcast(text);
    }

    public static void broadcast(String templateId) {
        if (manager != null) manager.broadcast(templateId);
    }

    public static void showToGroup(Collection<? extends Player> players, ScreenText text) {
        if (manager != null) manager.showToGroup(players, text);
    }

    public static void showToGroup(Predicate<Player> predicate, ScreenText text) {
        if (manager != null) manager.showToGroup(predicate, text);
    }

    public static void showToPermission(String permission, ScreenText text) {
        if (manager != null) manager.showToPermission(permission, text);
    }

    public static void showFloating(Location location, ScreenText text) {
        if (manager != null) manager.showFloating(location, text);
    }

    public static void showFloating(Player placeholderViewer, Location location, ScreenText text) {
        if (manager != null) manager.showFloating(placeholderViewer, location, text);
    }

    public static void registerPlaceholder(String id, BiFunction<Player, ScreenText, String> resolver) {
        if (manager != null) manager.registerPlaceholder(id, resolver);
    }

    public static void showToOnline(ScreenText text) {
        showToGroup(Bukkit.getOnlinePlayers(), text);
    }
}
