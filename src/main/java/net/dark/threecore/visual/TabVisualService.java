package net.dark.threecore.visual;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TabVisualService {
    private final JavaPlugin plugin;
    private final VisualConfig config;
    private final GradientRenderer renderer;
    private final RankStyleService ranks;
    private BukkitTask task;

    public TabVisualService(JavaPlugin plugin, VisualConfig config, GradientRenderer renderer, RankStyleService ranks) {
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
        this.ranks = ranks;
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, config.tabRefreshTicks());
    }

    public void reload() {
        start();
        refreshAll();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public void refreshAll() {
        if (!config.visualsEnabled()) return;
        if (Bukkit.getPluginManager().getPlugin("TAB") != null && !config.overrideTabPlugin() && !config.forceOverTabPlugin()) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    public void refresh(Player player) {
        RankStyle rank = ranks.style(player);
        String header = String.join("\n", config.header());
        String footer = String.join("\n", config.footer());
        player.sendPlayerListHeaderAndFooter(renderer.render(player, header, rank, config.shadow("tab", rank)), renderer.render(player, footer, rank, config.shadow("tab", rank)));
        player.playerListName(renderer.render(player, config.playerFormat(), rank, config.shadow("tab", rank)));
    }
}
