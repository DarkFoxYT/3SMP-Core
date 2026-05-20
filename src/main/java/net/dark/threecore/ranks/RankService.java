package net.dark.threecore.ranks;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RankService implements Listener {
    private static final String CONFIG = "admin/ranks.yml";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private boolean listenerRegistered;

    public RankService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void start() {
        registerListener();
        ensurePluginRankConfig();
        cleanupRetiredPluginRankConfig();
        ensureServerRanksLater();
        removeRetiredLuckPermsGroupsLater();
    }

    public void reload() {
        configs.reload(CONFIG);
        registerListener();
        ensurePluginRankConfig();
        cleanupRetiredPluginRankConfig();
        ensureServerRanksLater();
        removeRetiredLuckPermsGroupsLater();
    }

    public List<String> rankIds() {
        ConfigurationSection section = config().getConfigurationSection("ranks");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public List<String> packageIds(String mode) {
        String key = packageMode(mode);
        Set<String> ids = new LinkedHashSet<>();
        ConfigurationSection rankSection = config().getConfigurationSection("ranks");
        if (rankSection != null) {
            for (String rank : rankSection.getKeys(false)) {
                if (config().isConfigurationSection("ranks." + rank + ".delivery." + key)) ids.add(rank);
            }
        }
        ConfigurationSection legacy = configs.get("admin/permissions.yml").getConfigurationSection("rank-packages." + key);
        if (legacy != null) ids.addAll(legacy.getKeys(false));
        return new ArrayList<>(ids);
    }

    public boolean applyPreset(CommandSender sender, String rank) {
        RankDefinition def = rank(rank);
        if (def == null) return applyLegacyPreset(sender, rank);
        ensureLuckPermsGroup(def);
        int permissions = 0;
        for (String permission : config().getStringList("ranks." + def.id() + ".permissions")) {
            if (permission == null || permission.isBlank()) continue;
            dispatch(sender, "lp group " + def.group() + " permission set " + permission + " true");
            permissions++;
        }
        for (String parent : config().getStringList("ranks." + def.id() + ".inherits")) {
            if (parent == null || parent.isBlank()) continue;
            dispatch(sender, "lp group " + def.group() + " parent add " + parent);
        }
        for (String command : config().getStringList("ranks." + def.id() + ".setup-commands")) {
            dispatch(sender, replaceRankTokens(command, def));
        }
        message(sender, "<green>Applied rank setup:</green> <white>" + def.id() + "</white> <dark_gray>(" + permissions + " permissions)</dark_gray>");
        return true;
    }

    public boolean deliver(CommandSender sender, String mode, String playerName, String rankId) {
        String key = packageMode(mode);
        RankDefinition def = rank(rankId);
        if (def == null || !config().isConfigurationSection("ranks." + def.id() + ".delivery." + key)) {
            return deliverLegacy(sender, key, playerName, rankId);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        ensureLuckPermsGroup(def);
        applyPreset(sender, def.id());
        String parentCommand = key.equals("subscriptions")
                ? config().getString("settings.subscription-parent-command", "lp user {player} parent addtemp {group} {duration} accumulate")
                : config().getString("settings.permanent-parent-command", "lp user {player} parent add {group}");
        String duration = config().getString("ranks." + def.id() + ".delivery." + key + ".duration", config().getString("settings.default-subscription-duration", "30d"));
        dispatch(sender, replaceDeliveryTokens(parentCommand, def, target, playerName, duration));
        int ran = 1;
        for (String command : config().getStringList("ranks." + def.id() + ".delivery." + key + ".commands")) {
            if (command == null || command.isBlank()) continue;
            dispatch(sender, replaceDeliveryTokens(command, def, target, playerName, duration));
            ran++;
        }
        message(sender, "<green>Applied " + (key.equals("subscriptions") ? "subscription" : "rank") + " package:</green> <white>" + def.id() + "</white> <gray>to</gray> <white>" + playerName + "</white> <dark_gray>(" + ran + " commands)</dark_gray>");
        announcePurchase(playerName, def.displayName(), key);
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) message(onlineTarget, config().getString("messages.delivery-received", "<gradient:#f4cd2a:#eda323:#d28d0d>Store delivery received:</gradient> <white>{rank}</white>").replace("{rank}", def.displayName()));
        return true;
    }

    public boolean deliverStore(CommandSender sender, String mode, String playerName, String rankId) {
        String key = packageMode(mode);
        if (hasPackage(mode, rankId)) return deliver(sender, mode, playerName, rankId);
        RankDefinition def = rank(rankId);
        if (def == null) return deliverLegacyDirect(sender, key, playerName, rankId);
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        ensureLuckPermsGroup(def);
        applyPreset(sender, def.id());
        String parentCommand = key.equals("subscriptions")
                ? config().getString("settings.subscription-parent-command", "lp user {player} parent addtemp {group} {duration} accumulate")
                : config().getString("settings.permanent-parent-command", "lp user {player} parent add {group}");
        String duration = config().getString("ranks." + def.id() + ".delivery." + key + ".duration", config().getString("settings.default-subscription-duration", "30d"));
        dispatch(sender, replaceDeliveryTokens(parentCommand, def, target, playerName, duration));
        message(sender, "<green>Store rank delivered:</green> <white>" + def.id() + "</white> <gray>to</gray> <white>" + playerName + "</white>");
        announcePurchase(playerName, def.displayName(), key);
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) message(onlineTarget, config().getString("messages.delivery-received", "<gradient:#f4cd2a:#eda323:#d28d0d>Store delivery received:</gradient> <white>{rank}</white>").replace("{rank}", def.displayName()));
        return true;
    }

    public boolean remove(CommandSender sender, String playerName, String rankId) {
        RankDefinition def = rank(rankId);
        if (def == null) return removeLegacy(sender, playerName, rankId);
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String parentCommand = config().getString("settings.remove-parent-command", "lp user {player} parent remove {group}");
        dispatch(sender, replaceDeliveryTokens(parentCommand, def, target, playerName, ""));
        int ran = 1;
        for (String command : config().getStringList("ranks." + def.id() + ".removal.commands")) {
            if (command == null || command.isBlank()) continue;
            dispatch(sender, replaceDeliveryTokens(command, def, target, playerName, ""));
            ran++;
        }
        message(sender, "<green>Removed rank:</green> <white>" + def.id() + "</white> <gray>from</gray> <white>" + playerName + "</white> <dark_gray>(" + ran + " commands)</dark_gray>");
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) message(onlineTarget, config().getString("messages.rank-removed", "<gray>Your {rank} rank was removed.</gray>").replace("{rank}", def.displayName()));
        return true;
    }

    public boolean hasPackage(String mode, String rankId) {
        String key = packageMode(mode);
        RankDefinition def = rank(rankId);
        if (def != null && config().isConfigurationSection("ranks." + def.id() + ".delivery." + key)) return true;
        return configs.get("admin/permissions.yml").isConfigurationSection("rank-packages." + key + "." + normalize(rankId));
    }

    private void ensureServerRanksLater() {
        if (!config().getBoolean("settings.ensure-luckperms-groups", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String id : rankIds()) {
                RankDefinition def = rank(id);
                if (def != null && def.enabled()) ensureLuckPermsGroup(def);
            }
        }, Math.max(1L, config().getLong("settings.ensure-delay-ticks", 20L)));
    }

    private void removeRetiredLuckPermsGroupsLater() {
        if (!config().getBoolean("settings.remove-retired-luckperms-groups", true)) return;
        List<String> retired = retiredGroups();
        if (retired.isEmpty() || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        long delay = Math.max(1L, config().getLong("settings.ensure-delay-ticks", 20L) + 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> retired.forEach(this::deleteLuckPermsGroup), delay);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        assignMemberIfRanklessLater(event.getPlayer());
    }

    private void registerListener() {
        if (listenerRegistered) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listenerRegistered = true;
    }

    private void assignMemberIfRanklessLater(Player player) {
        if (player == null) return;
        if (!config().getBoolean("settings.assign-member-on-join", true)) return;
        long delay = Math.max(1L, config().getLong("settings.member-assignment-delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String current = primaryGroup(player.getUniqueId());
            if (!current.isBlank() && !current.equalsIgnoreCase("default")) return;
            RankDefinition member = rank(config().getString("settings.member-rank", "member"));
            if (member == null || member.group().isBlank() || member.group().equalsIgnoreCase("default")) return;
            ensureLuckPermsGroup(member);
            dispatch(Bukkit.getConsoleSender(), "lp user " + player.getName() + " parent set " + member.group());
        }, delay);
    }

    private void ensurePluginRankConfig() {
        if (config().getBoolean("settings.sync-chat-styles", true)) syncChatStyles();
        if (config().getBoolean("settings.sync-visual-ranks", true)) syncVisualRanks();
    }

    private void cleanupRetiredPluginRankConfig() {
        Set<String> retired = new HashSet<>(retiredGroups());
        if (retired.isEmpty()) return;
        YamlConfiguration core = configs.get("core/config.yml");
        boolean coreChanged = false;
        for (String id : retired) coreChanged |= removePath(core, "chat.rank-styles." + id);
        if (coreChanged) save(core, "core/config.yml");

        YamlConfiguration visual = configs.get("social/friends.yml");
        boolean visualChanged = false;
        for (String id : retired) {
            visualChanged |= removePath(visual, "friends.tab.visuals.shadows.ranks." + id);
            visualChanged |= removePath(visual, "friends.tab.gradients." + id);
            visualChanged |= removePath(visual, "friends.tab.ranks." + id);
        }
        visualChanged |= removeListValues(visual, "friends.tab.sorting.order", retired);
        if (visualChanged) save(visual, "social/friends.yml");
    }

    private void syncChatStyles() {
        YamlConfiguration core = configs.get("core/config.yml");
        boolean changed = false;
        for (String id : rankIds()) {
            String base = "ranks." + id + ".chat";
            if (!config().isConfigurationSection(base)) continue;
            String target = "chat.rank-styles." + id;
            changed |= setMissing(core, target + ".priority", config().getInt(base + ".priority", config().getInt("ranks." + id + ".weight", 100)));
            changed |= setMissing(core, target + ".primary-group", config().getString("ranks." + id + ".luckperms-group", id));
            changed |= setMissing(core, target + ".permission", config().getString(base + ".permission", ""));
            changed |= setMissing(core, target + ".name-format", config().getString(base + ".name-format", "<gradient:#f4cd2a:#eda323:#d28d0d><player></gradient>"));
            changed |= setMissing(core, target + ".name-color", config().getString(base + ".name-color", "gold"));
            changed |= setMissing(core, target + ".tag-format", config().getString(base + ".tag-format", "<gradient:#f4cd2a:#eda323:#d28d0d><tag></gradient>"));
            changed |= setMissing(core, target + ".tag-color", config().getString(base + ".tag-color", "gold"));
            changed |= setMissing(core, target + ".message-color", config().getString(base + ".message-color", "white"));
        }
        if (changed) save(core, "core/config.yml");
    }

    private void syncVisualRanks() {
        YamlConfiguration visual = configs.get("social/friends.yml");
        boolean changed = false;
        for (String id : rankIds()) {
            String base = "ranks." + id + ".visual";
            String target = "friends.tab.ranks." + id;
            changed |= setMissing(visual, target + ".image", config().getString(base + ".image", id.equals("default") || id.equals("member") ? "" : "<img:" + id.replace("-", "_") + ">"));
            changed |= setMissing(visual, target + ".prefix", config().getString(base + ".prefix", ""));
            changed |= setMissing(visual, target + ".tab-prefix", config().getString(base + ".tab-prefix", ""));
            changed |= setMissing(visual, target + ".gradient", config().getString(base + ".gradient", id));
            changed |= setMissing(visual, target + ".sort-weight", config().getInt(base + ".sort-weight", config().getInt("ranks." + id + ".weight", 100)));
            String imageId = config().getString(base + ".image-id", id.replace("-", "_"));
            if (!imageId.isBlank()) changed |= setMissing(visual, "friends.tab.visuals.images.placeholders." + imageId, "%img_" + imageId + "%");
            String gradient = config().getString(base + ".gradient-colors", "");
            if (!gradient.isBlank()) changed |= setMissing(visual, "friends.tab.gradients." + id, gradient);
        }
        List<String> order = visual.getStringList("friends.tab.sorting.order");
        for (String id : rankIds()) {
            if (!order.contains(id)) {
                order.add(id);
                changed = true;
            }
        }
        if (!order.isEmpty()) visual.set("friends.tab.sorting.order", order);
        if (changed) save(visual, "social/friends.yml");
    }

    private boolean ensureLuckPermsGroup(RankDefinition def) {
        if (!def.enabled() || def.group().isBlank() || def.group().equalsIgnoreCase("default")) return false;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms is not installed; cannot ensure rank group " + def.group() + ".");
            return false;
        }
        try {
            Object api = luckPermsApi();
            if (api == null) return false;
            Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);
            Object future = groupManager.getClass().getMethod("loadGroup", String.class).invoke(groupManager, def.group());
            Object optional = ((CompletableFuture<?>) future).join();
            if (optional instanceof Optional<?> opt && opt.isPresent()) return true;
            groupManager.getClass().getMethod("createAndLoadGroup", String.class).invoke(groupManager, def.group());
            plugin.getLogger().info("Created missing LuckPerms rank group: " + def.group());
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not create LuckPerms group " + def.group() + ": " + ex.getMessage());
            dispatch(Bukkit.getConsoleSender(), "lp creategroup " + def.group());
            return false;
        }
    }

    private void deleteLuckPermsGroup(String group) {
        if (group == null || group.isBlank() || group.equalsIgnoreCase("default")) return;
        try {
            Object api = luckPermsApi();
            if (api == null) return;
            Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);
            Object future = groupManager.getClass().getMethod("loadGroup", String.class).invoke(groupManager, group);
            Object optional = ((CompletableFuture<?>) future).join();
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return;
            Object luckPermsGroup = opt.get();
            for (Method method : groupManager.getClass().getMethods()) {
                if (!method.getName().equals("deleteGroup") || method.getParameterCount() != 1) continue;
                Object result = method.invoke(groupManager, luckPermsGroup);
                if (result instanceof CompletableFuture<?> deleteFuture) deleteFuture.join();
                plugin.getLogger().info("Deleted retired LuckPerms rank group: " + group);
                return;
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not delete retired LuckPerms group " + group + " through API: " + ex.getMessage());
        }
        dispatch(Bukkit.getConsoleSender(), "lp deletegroup " + group);
    }

    private Object luckPermsApi() throws ClassNotFoundException {
        Class<?> providerClass = Class.forName("net.luckperms.api.LuckPerms");
        RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(providerClass);
        return registration == null ? null : registration.getProvider();
    }

    private String primaryGroup(UUID uuid) {
        if (uuid == null || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return "";
        try {
            Object api = luckPermsApi();
            if (api == null) return "";
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object future = userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid);
            Object user = future instanceof CompletableFuture<?> completable ? completable.join() : null;
            if (user == null) return "";
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? "" : group.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private RankDefinition rank(String id) {
        String key = normalize(id);
        String path = "ranks." + key;
        if (!config().isConfigurationSection(path)) return null;
        return new RankDefinition(
                key,
                config().getString(path + ".luckperms-group", key),
                config().getString(path + ".display-name", key.toUpperCase(Locale.ROOT)),
                config().getBoolean(path + ".enabled", true)
        );
    }

    private boolean deliverLegacy(CommandSender sender, String key, String playerName, String rankId) {
        String rank = normalize(rankId);
        YamlConfiguration legacy = configs.get("admin/permissions.yml");
        String path = "rank-packages." + key + "." + rank;
        if (!legacy.isConfigurationSection(path)) return false;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String group = legacy.getString(path + ".luckperms-group", rank);
        int ran = 0;
        for (String configured : legacy.getStringList(path + ".commands")) {
            String parsed = configured
                    .replace("{player}", playerName)
                    .replace("{uuid}", target.getUniqueId().toString())
                    .replace("{rank}", rank)
                    .replace("{group}", group);
            if (!parsed.isBlank()) {
                dispatch(sender, parsed);
                ran++;
            }
        }
        message(sender, "<green>Applied legacy " + (key.equals("subscriptions") ? "subscription" : "rank") + " package:</green> <white>" + rank + "</white> <gray>to</gray> <white>" + playerName + "</white> <dark_gray>(" + ran + " commands)</dark_gray>");
        announcePurchase(playerName, rank.toUpperCase(Locale.ROOT), key);
        return true;
    }

    private boolean deliverLegacyDirect(CommandSender sender, String key, String playerName, String rankId) {
        String rank = normalize(rankId);
        YamlConfiguration legacy = configs.get("admin/permissions.yml");
        String group = null;
        for (String packageKey : List.of(key, "permanent", "subscriptions")) {
            String path = "rank-packages." + packageKey + "." + rank;
            if (legacy.isConfigurationSection(path)) {
                group = legacy.getString(path + ".luckperms-group", rank);
                break;
            }
        }
        if (group == null && legacy.isConfigurationSection("rank-presets." + rank)) {
            group = legacy.getString("rank-presets." + rank + ".luckperms-group", rank);
        }
        if (group == null) return false;
        if (key.equals("subscriptions")) dispatch(sender, "lp user " + playerName + " parent addtemp " + group + " " + config().getString("settings.default-subscription-duration", "30d") + " accumulate");
        else dispatch(sender, "lp user " + playerName + " parent add " + group);
        message(sender, "<green>Store legacy rank delivered:</green> <white>" + rank + "</white> <gray>to</gray> <white>" + playerName + "</white>");
        announcePurchase(playerName, rank.toUpperCase(Locale.ROOT), key);
        return true;
    }

    private boolean removeLegacy(CommandSender sender, String playerName, String rankId) {
        String rank = normalize(rankId);
        YamlConfiguration legacy = configs.get("admin/permissions.yml");
        String group = null;
        for (String key : List.of("permanent", "subscriptions")) {
            String path = "rank-packages." + key + "." + rank;
            if (legacy.isConfigurationSection(path)) {
                group = legacy.getString(path + ".luckperms-group", rank);
                break;
            }
        }
        if (group == null && legacy.isConfigurationSection("rank-presets." + rank)) {
            group = legacy.getString("rank-presets." + rank + ".luckperms-group", rank);
        }
        if (group == null && !rankIds().contains(rank)) return false;
        if (group == null) group = rank;
        dispatch(sender, "lp user " + playerName + " parent remove " + group);
        message(sender, "<green>Removed legacy rank:</green> <white>" + rank + "</white> <gray>from</gray> <white>" + playerName + "</white>");
        return true;
    }

    private void announcePurchase(String playerName, String displayName, String key) {
        if (!config().getBoolean("settings.broadcast-deliveries", true)) return;
        String message = config().getString("messages.delivery-broadcast", "<gradient:#f4cd2a:#eda323:#d28d0d>{player}</gradient> <gray>bought</gray> <white>{rank}</white> <gray>in-game!</gray>");
        String rendered = message
                .replace("{player}", playerName)
                .replace("{rank}", displayName)
                .replace("{type}", key.equals("subscriptions") ? "subscription" : "rank");
        for (Player player : Bukkit.getOnlinePlayers()) message(player, rendered);
        plugin.getLogger().info("[Ranks] " + playerName + " bought " + displayName + " (" + key + ")");
    }

    private boolean applyLegacyPreset(CommandSender sender, String rankId) {
        String rank = normalize(rankId);
        YamlConfiguration legacy = configs.get("admin/permissions.yml");
        String path = "rank-presets." + rank;
        if (!legacy.isConfigurationSection(path)) return false;
        String lpGroup = legacy.getString(path + ".luckperms-group", rank);
        for (String permission : legacy.getStringList(path + ".permissions")) {
            dispatch(sender, "lp group " + lpGroup + " permission set " + permission + " true");
        }
        for (String command : legacy.getStringList(path + ".commands")) {
            dispatch(sender, command.replace("{group}", lpGroup).replace("{rank}", rank));
        }
        message(sender, "<green>Applied legacy rank permission preset:</green> <white>" + rank + "</white>");
        return true;
    }

    private String replaceRankTokens(String command, RankDefinition def) {
        return command == null ? "" : command
                .replace("{rank}", def.id())
                .replace("{group}", def.group())
                .replace("{display}", def.displayName());
    }

    private String replaceDeliveryTokens(String command, RankDefinition def, OfflinePlayer target, String playerName, String duration) {
        UUID uuid = target.getUniqueId();
        return replaceRankTokens(command, def)
                .replace("{player}", playerName)
                .replace("{uuid}", uuid == null ? "" : uuid.toString())
                .replace("{duration}", duration == null || duration.isBlank() ? "30d" : duration);
    }

    private void dispatch(CommandSender sender, String command) {
        if (command == null || command.isBlank()) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void message(CommandSender sender, String message) {
        net.dark.threecore.text.Text.send(sender, message);
    }

    private boolean setMissing(YamlConfiguration yaml, String path, Object value) {
        if (yaml.contains(path)) return false;
        yaml.set(path, value);
        return true;
    }

    private boolean removePath(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) return false;
        yaml.set(path, null);
        return true;
    }

    private boolean removeListValues(YamlConfiguration yaml, String path, Set<String> values) {
        List<String> list = yaml.getStringList(path);
        if (list.isEmpty()) return false;
        boolean changed = list.removeIf(value -> values.contains(normalize(value)));
        if (changed) yaml.set(path, list);
        return changed;
    }

    private void save(YamlConfiguration yaml, String path) {
        try {
            File file = new File(plugin.getDataFolder(), path);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save " + path + ": " + ex.getMessage());
        }
    }

    private String packageMode(String mode) {
        return mode != null && (mode.equalsIgnoreCase("sub") || mode.equalsIgnoreCase("subscription") || mode.equalsIgnoreCase("subscriptions")) ? "subscriptions" : "permanent";
    }

    private String normalize(String id) {
        return id == null || id.isBlank() ? "default" : id.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private List<String> retiredGroups() {
        List<String> configured = config().getStringList("settings.retired-luckperms-groups");
        if (configured.isEmpty()) configured = List.of("vip", "elite", "3rank");
        return configured.stream().map(this::normalize).filter(value -> !value.isBlank()).distinct().toList();
    }

    private YamlConfiguration config() {
        return configs.get(CONFIG);
    }

    private record RankDefinition(String id, String group, String displayName, boolean enabled) {}
}
