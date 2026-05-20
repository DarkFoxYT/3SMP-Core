package net.dark.threecore.visual;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.data.PlayerDataRepository;
import net.kyori.adventure.text.Component;

public final class VisualManager {
    private final VisualConfig config;
    private final GradientRenderer renderer;
    private final RankStyleService ranks;
    private final VisualCosmeticService cosmetics;
    private final VisualGuiService gui;
    private final TabVisualService tab;
    private final NameTagService nameTags;
    private final ScoreboardService scoreboards;

    public VisualManager(JavaPlugin plugin, net.dark.threecore.config.ConfigFiles configs, PlayerDataRepository repository) {
        this.config = new VisualConfig(configs);
        this.renderer = new GradientRenderer(config);
        this.ranks = new RankStyleService(plugin, config);
        this.cosmetics = new VisualCosmeticService(plugin, configs);
        this.ranks.cosmetics(cosmetics);
        this.tab = new TabVisualService(plugin, config, renderer, ranks);
        this.nameTags = new NameTagService(plugin, config, renderer, ranks);
        this.scoreboards = new ScoreboardService(plugin, config, renderer, ranks);
        this.gui = new VisualGuiService(plugin, configs, cosmetics, this, repository);
        Bukkit.getPluginManager().registerEvents(nameTags, plugin);
        Bukkit.getPluginManager().registerEvents(scoreboards, plugin);
    }

    public void start() {
        tab.start();
        nameTags.start();
        scoreboards.start();
        refreshAll();
    }

    public void reload() {
        config.reload();
        cosmetics.reload();
        tab.reload();
        scoreboards.reload();
        refreshAll();
    }

    public void shutdown() {
        tab.shutdown();
        nameTags.shutdown();
        scoreboards.shutdown();
        nameTags.cleanup();
    }

    public void refreshAll() {
        tab.refreshAll();
        nameTags.refreshAll();
        scoreboards.refreshAll();
    }

    public boolean toggleScoreboard(Player player) {
        return scoreboards.toggle(player);
    }

    public RankStyle style(Player player) {
        return ranks.style(player);
    }

    public String tabName(Player player) {
        RankStyle rank = ranks.style(player);
        return renderer.plainDebug(player, config.playerFormat(), rank);
    }

    public String scoreboardRank(Player player) {
        RankStyle rank = ranks.style(player);
        return renderer.plainDebug(player, "<rank_image>", rank);
    }

    public void debugRank(CommandSender sender, Player player) {
        RankStyle rank = ranks.style(player);
        Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Rank debug for</gradient> <white>" + player.getName() + "</white>");
        Text.send(sender, "<gray>Detected:</gray> <white>" + rank.id() + "</white>");
        Text.send(sender, "<gray>LuckPerms:</gray> <white>" + ranks.luckPermsGroup(player) + "</white>");
        Text.send(sender, "<gray>Vault:</gray> <white>" + ranks.vaultGroup(player) + "</white>");
        Text.send(sender, "<gray>Gradient:</gray> <white>" + rank.gradient() + "</white>");
        Text.send(sender, "<gray>Prefix:</gray> <white>" + renderer.plainDebug(player, rank.prefix(), rank) + "</white>");
        Text.send(sender, "<gray>Sort:</gray> <white>" + rank.sortWeight() + "</white>");
        Text.send(sender, "<gray>Tab:</gray> <white>" + renderer.plainDebug(player, config.playerFormat(), rank) + "</white>");
    }

    public void open(Player player) {
        gui.open(player);
    }

    public void duelService(DuelService duelService) {
        ranks.duelService(duelService);
    }

    public Component renderedPlayerName(Player player) {
        RankStyle rank = ranks.style(player);
        return renderer.render(player, "{grad:rank}<player>{/grad}", rank, config.shadow("tab", rank));
    }

    public Component renderedPrefix(Player player) {
        RankStyle rank = ranks.style(player);
        return renderer.render(player, "<rank_image>", rank, config.shadow("tab", rank));
    }

    public Component renderedChatMessage(Player player, Component message) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
        RankStyle rank = ranks.style(player);
        return renderer.render(player, "&#" + config.chatMessageColor().replace("#", "") + plain, rank);
    }

    public void preview(Player player) {
        RankStyle rank = ranks.style(player);
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Tab:</gradient> <white>" + renderer.plainDebug(player, config.playerFormat(), rank) + "</white>");
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Nametag:</gradient> <white>" + renderer.plainDebug(player, config.nametagDisplay(), rank) + "</white>");
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Scoreboard:</gradient> <white>" + scoreboardRank(player) + "</white>");
    }
}
