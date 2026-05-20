package net.dark.threecore.visual;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.dark.threecore.duels.DuelService;

import java.lang.reflect.Method;
import java.util.Locale;

public final class RankStyleService {
    private final JavaPlugin plugin;
    private final VisualConfig config;
    private VisualCosmeticService cosmetics;
    private DuelService duelService;

    public RankStyleService(JavaPlugin plugin, VisualConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public RankStyle style(Player player) {
        RankStyle base = config.rank(rankId(player));
        String prefix = base.prefix();
        String tabPrefix = base.tabPrefix();
        String gradient = base.gradient();
        String shadow = "";
        if (cosmetics == null || !cosmetics.enabled()) {
            String duelGradient = duelGradient(player);
            if (config.duelTeamNameColors() && !duelGradient.isBlank()) gradient = duelGradient;
            return new RankStyle(base.id(), base.image(), prefix, tabPrefix, gradient, base.sortWeight(), shadow);
        }
        VisualCosmeticService.Cosmetic selectedPrefix = cosmetics.selected(player, VisualCosmeticService.Type.PREFIX);
        VisualCosmeticService.Cosmetic selectedShadow = cosmetics.selected(player, VisualCosmeticService.Type.SHADOW);
        if (selectedPrefix != null && cosmetics.allowPrefixOverride()) {
            prefix = selectedPrefix.value();
            tabPrefix = selectedPrefix.value() + " &8┃ ";
        }
        if (selectedShadow != null && cosmetics.allowShadowOverride()) shadow = selectedShadow.value();
        VisualCosmeticService.Cosmetic selectedGradient = cosmetics.selected(player, VisualCosmeticService.Type.NAME_GRADIENT);
        VisualCosmeticService.Cosmetic selectedColor = cosmetics.selected(player, VisualCosmeticService.Type.NAME_COLOR);
        if (cosmetics.allowNameOverride()) {
            if (selectedGradient != null) gradient = selectedGradient.id().equals("custom") ? "__gradient_literal_" + selectedGradient.value() : "__cosmetic_" + selectedGradient.id();
            else if (selectedColor != null) gradient = selectedColor.id().equals("custom") ? "__color_literal_" + selectedColor.value() : "__color_" + selectedColor.id();
        }
        String duelGradient = duelGradient(player);
        if (config.duelTeamNameColors() && !duelGradient.isBlank()) gradient = duelGradient;
        return new RankStyle(base.id(), base.image(), prefix, tabPrefix, gradient, base.sortWeight(), shadow);
    }

    public void cosmetics(VisualCosmeticService cosmetics) {
        this.cosmetics = cosmetics;
    }

    public void duelService(DuelService duelService) {
        this.duelService = duelService;
    }

    private String duelGradient(Player player) {
        String team = duelTeam(player);
        if (team.equals("red")) return "__duel_red";
        if (team.equals("blue")) return "__duel_blue";
        if (team.equals("ffa")) return "__duel_ffa";
        return "";
    }

    public String duelTeam(Player player) {
        if (duelService == null || player == null || !duelService.isInMatch(player.getUniqueId())) return "";
        return duelService.teamColorId(player.getUniqueId());
    }

    public String rankId(Player player) {
        String lp = luckPermsGroup(player);
        if (!lp.isBlank()) return normalize(lp);
        String vault = vaultGroup(player);
        if (!vault.isBlank()) return normalize(vault);
        for (String rank : config.rankOrder()) {
            if (player.hasPermission("group." + rank) || player.hasPermission("3smpcore.rank." + rank)) return normalize(rank);
        }
        return "default";
    }

    public String luckPermsGroup(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return "";
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPerms");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(providerClass);
            if (registration == null) return "";
            Object api = registration.getProvider();
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "";
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? "" : group.toString();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("LuckPerms rank lookup failed: " + ex.getMessage());
            return "";
        }
    }

    public String vaultGroup(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return "";
        try {
            Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(permissionClass);
            if (registration == null) return "";
            Object provider = registration.getProvider();
            Method method = provider.getClass().getMethod("getPrimaryGroup", Player.class);
            Object group = method.invoke(provider, player);
            return group == null ? "" : group.toString();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("Vault rank lookup failed: " + ex.getMessage());
            return "";
        }
    }

    private String normalize(String rank) {
        return rank == null || rank.isBlank() ? "default" : rank.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
