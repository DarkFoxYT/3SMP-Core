package net.dark.threecore.visual;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.Locale;

public final class NameTagService implements Listener {
    private final JavaPlugin plugin;
    private final VisualConfig config;
    private final GradientRenderer renderer;
    private final RankStyleService ranks;
    private BukkitTask task;

    public NameTagService(JavaPlugin plugin, VisualConfig config, GradientRenderer renderer, RankStyleService ranks) {
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
        this.ranks = ranks;
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, Math.max(10L, config.tabRefreshTicks()));
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            for (Player target : Bukkit.getOnlinePlayers()) refresh(board, target);
        }
    }

    public void cleanup() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Team team : viewer.getScoreboard().getTeams()) {
                if (team.getName().startsWith("3smp_")) team.unregister();
            }
        }
    }

    private void refresh(Scoreboard board, Player target) {
        if (!config.visualsEnabled()) return;
        RankStyle rank = ranks.style(target);
        target.customName(null);
        target.setCustomNameVisible(false);
        target.displayName(renderer.render(target, "{grad:rank}<player>{/grad}", rank, config.shadow("nametag", rank)));
        String duelTeam = ranks.duelTeam(target);
        removeFromVisualTeams(board, target);
        if (!duelTeam.isBlank()) {
            Team team = duelTeam(board, target);
            if (team != null) {
                team.prefix(neutralNametagPart(target, config.nametagPrefix(), rank));
                team.suffix(neutralNametagPart(target, config.nametagSuffix(), rank));
                team.setColor(switch (duelTeam) {
                    case "red" -> ChatColor.RED;
                    case "ffa" -> ChatColor.GOLD;
                    default -> ChatColor.BLUE;
                });
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            }
            return;
        }
        String teamName = teamName(rank, target);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(neutralNametagPart(target, config.nametagPrefix(), rank));
        team.suffix(neutralNametagPart(target, config.nametagSuffix(), rank));
        team.setColor(closestChatColor(config.gradient(rank.gradient())));
        if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
    }

    private net.kyori.adventure.text.Component neutralNametagPart(Player target, String format, RankStyle rank) {
        RankStyle neutral = new RankStyle(rank.id(), rank.image(), stripColors(rank.prefix()), stripColors(rank.tabPrefix()), "default", rank.sortWeight(), rank.shadow());
        String input = stripGradientTags(format);
        return renderer.render(target, "&#dbeafe" + input, neutral, config.shadow("nametag", rank));
    }

    private String stripGradientTags(String input) {
        if (input == null || input.isBlank()) return "";
        return input.replaceAll("\\{grad:[^}]+}", "").replace("{/grad}", "");
    }

    private String stripColors(String input) {
        if (input == null || input.isBlank()) return "";
        return input.replaceAll("(?i)&#[0-9a-f]{6}", "").replaceAll("(?i)&[0-9a-fk-or]", "").replaceAll("(?i)§x(§[0-9a-f]){6}", "").replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    private void removeFromVisualTeams(Scoreboard board, Player target) {
        if (board == null || target == null) return;
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith("3smp_") && team.hasEntry(target.getName())) {
                team.removeEntry(target.getName());
            }
        }
    }

    private Team duelTeam(Scoreboard board, Player target) {
        if (board == null || target == null) return null;
        for (Team team : board.getTeams()) {
            if ((team.getName().startsWith("duel_r_") || team.getName().startsWith("duel_b_") || team.getName().startsWith("duel_f_")) && team.hasEntry(target.getName())) {
                return team;
            }
        }
        return null;
    }

    private ChatColor closestChatColor(String gradient) {
        Color color = firstColor(gradient);
        if (color == null) return ChatColor.WHITE;
        ChatColor best = ChatColor.WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (ChatColor candidate : new ChatColor[] {
                ChatColor.DARK_BLUE, ChatColor.BLUE, ChatColor.AQUA, ChatColor.WHITE, ChatColor.GRAY,
                ChatColor.GOLD, ChatColor.YELLOW, ChatColor.RED, ChatColor.DARK_RED, ChatColor.LIGHT_PURPLE
        }) {
            Color mapped = chatColor(candidate);
            double distance = Math.pow(color.getRed() - mapped.getRed(), 2)
                    + Math.pow(color.getGreen() - mapped.getGreen(), 2)
                    + Math.pow(color.getBlue() - mapped.getBlue(), 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private Color firstColor(String gradient) {
        if (gradient == null || gradient.isBlank()) return null;
        for (String part : gradient.split(":")) {
            String hex = part.trim().toLowerCase(Locale.ROOT);
            if (!hex.matches("#[0-9a-f]{6}")) continue;
            return new Color(Integer.parseInt(hex.substring(1), 16));
        }
        return null;
    }

    private Color chatColor(ChatColor color) {
        return switch (color) {
            case DARK_BLUE -> new Color(0x0000AA);
            case BLUE -> new Color(0x5555FF);
            case AQUA -> new Color(0x55FFFF);
            case GRAY -> new Color(0xAAAAAA);
            case GOLD -> new Color(0xFFAA00);
            case YELLOW -> new Color(0xFFFF55);
            case RED -> new Color(0xFF5555);
            case DARK_RED -> new Color(0xAA0000);
            case LIGHT_PURPLE -> new Color(0xFF55FF);
            default -> new Color(0xFFFFFF);
        };
    }

    private String teamName(RankStyle rank, Player player) {
        return ("3smp_" + String.format("%03d", Math.max(0, Math.min(999, rank.sortWeight()))) + "_" + safe(rank.id()) + "_" + player.getName()).substring(0, Math.min(16, ("3smp_" + String.format("%03d", Math.max(0, Math.min(999, rank.sortWeight()))) + "_" + safe(rank.id()) + "_" + player.getName()).length()));
    }

    private String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9]", "");
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 2L); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { refreshAll(); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { refreshAll(); }
}
