package net.dark.threecore.visual;

import net.dark.threecore.duels.DuelService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardService implements Listener {
    private final JavaPlugin plugin;
    private final VisualConfig config;
    private final GradientRenderer renderer;
    private final RankStyleService ranks;
    private final Set<UUID> disabled = new HashSet<>();
    private BukkitTask task;

    public ScoreboardService(JavaPlugin plugin, VisualConfig config, GradientRenderer renderer, RankStyleService ranks) {
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
        this.ranks = ranks;
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, config.scoreboardRefreshTicks());
    }

    public void reload() {
        start();
        refreshAll();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public boolean toggle(Player player) {
        if (DuelService.isDuelPlayer(player)) return true;
        if (!disabled.add(player.getUniqueId())) {
            disabled.remove(player.getUniqueId());
            refresh(player);
            return true;
        }
        if (Bukkit.getScoreboardManager() != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        return false;
    }

    public void refreshAll() {
        if (!config.scoreboardEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    public void refresh(Player player) {
        if (!config.scoreboardEnabled() || disabled.contains(player.getUniqueId()) || Bukkit.getScoreboardManager() == null) return;
        if (DuelService.isDuelPlayer(player)) return;
        RankStyle rank = ranks.style(player);
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) board = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = config.centerScoreboardTitle() ? centered(player, config.boardTitle(), rank) : config.boardTitle();
        Objective objective = board.getObjective("3smpvis");
        if (objective == null) objective = board.registerNewObjective("3smpvis", "dummy", renderer.render(player, title, rank, config.shadow("scoreboard", rank)));
        objective.displayName(renderer.render(player, title, rank, config.shadow("scoreboard", rank)));
        applyBlankNumberFormat(objective);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (String entry : new HashSet<>(board.getEntries())) {
            if (entry.startsWith("§")) board.resetScores(entry);
        }
        List<String> lines = visibleLines(config.boardLines());
        int score = lines.size();
        int index = 0;
        Set<String> activeTeams = new HashSet<>();
        for (String raw : lines) {
            String entry = uniqueEntry(index++);
            String teamName = "3smp_l" + index;
            activeTeams.add(teamName);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam("3smp_l" + index);
            if (!team.hasEntry(entry)) team.addEntry(entry);
            String line = config.centerScoreboardLines() ? centered(player, raw, rank) : raw;
            team.prefix(renderer.render(player, line, rank, config.shadow("scoreboard", rank)));
            objective.getScore(entry).setScore(score--);
        }
        for (Team team : new HashSet<>(board.getTeams())) {
            if (team.getName().startsWith("3smp_l") && !activeTeams.contains(team.getName())) team.unregister();
        }
        player.setScoreboard(board);
    }

    private List<String> visibleLines(List<String> rawLines) {
        return rawLines;
    }

    private String centered(Player player, String raw, RankStyle rank) {
        if (raw == null || raw.isBlank()) return raw;
        int width = config.scoreboardCenterWidth();
        int visible = visibleWidth(renderer.renderLegacyForWidth(player, raw, rank));
        if (visible >= width) return raw;
        return " ".repeat(Math.max(0, (width - visible) / 2)) + raw;
    }

    private int visibleWidth(String legacy) {
        int width = 0;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                if (legacy.charAt(i + 1) == 'x' && i + 13 < legacy.length()) i += 13;
                else i++;
                continue;
            }
            if (c == '%' && legacy.startsWith("%img_", i)) {
                int end = legacy.indexOf('%', i + 5);
                if (end > i) {
                    String id = legacy.substring(i + 5, end);
                    width += config.scoreboardImageWidth(id);
                    i = end;
                    continue;
                }
            }
            width += c == ' ' ? 1 : 1;
        }
        return width;
    }

    private String uniqueEntry(int index) {
        String colors = "0123456789abcdef";
        return "§" + colors.charAt(index % colors.length()) + "§" + colors.charAt((index / colors.length()) % colors.length());
    }

    private void applyBlankNumberFormat(Objective objective) {
        if (!config.hideLineNumbers()) return;
        try {
            Class<?> numberFormatClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = numberFormatClass.getMethod("blank").invoke(null);
            objective.getClass().getMethod("numberFormat", numberFormatClass).invoke(objective, blank);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 5L); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { disabled.remove(event.getPlayer().getUniqueId()); }
}
