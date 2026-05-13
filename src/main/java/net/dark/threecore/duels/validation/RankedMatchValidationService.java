package net.dark.threecore.duels.validation;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.model.DuelMatch;
import net.dark.threecore.duels.stats.DuelMatchStatsService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RankedMatchValidationService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<String, Deque<Long>> pairMatches = new HashMap<>();
    private final Map<String, Deque<Long>> pairWins = new HashMap<>();
    private final Map<UUID, Deque<Long>> forfeits = new HashMap<>();
    private final Map<UUID, Long> queueDodgeUntil = new HashMap<>();

    public RankedMatchValidationService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean canJoinRanked(Player player) {
        if (player == null) return false;
        long until = queueDodgeUntil.getOrDefault(player.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) return true;
        long seconds = Math.max(1L, (until - System.currentTimeMillis() + 999L) / 1000L);
        Text.send(player, configs.get("duels/duels.yml").getString("duels.messages.ranked-dodge-cooldown", "<red>You must wait <white>{seconds}s</white> before joining ranked again.</red>").replace("{seconds}", String.valueOf(seconds)));
        return false;
    }

    public void recordQueueDodge(Collection<UUID> players) {
        int cooldownSeconds = Math.max(0, configs.get("duels/duels.yml").getInt("duels.ranked.validation.queue-dodge-cooldown-seconds", 30));
        long until = System.currentTimeMillis() + cooldownSeconds * 1000L;
        for (UUID uuid : players) queueDodgeUntil.put(uuid, until);
        alert("Ranked queue dodge detected for " + players);
    }

    public RankedMatchValidationResult validate(DuelMatch match, Set<UUID> winners, String reason, DuelMatchStatsService.DuelMatchStats stats) {
        if (match == null || !match.ranked()) return RankedMatchValidationResult.unranked();
        List<String> reasons = new ArrayList<>();
        long duration = stats == null ? System.currentTimeMillis() - match.startedAt() : stats.durationMillis();
        long minDuration = Math.max(0, configs.get("duels/duels.yml").getLong("duels.ranked.validation.minimum-duration-seconds", 20)) * 1000L;
        if (duration < minDuration) reasons.add("match too short");
        if (winners == null || winners.isEmpty()) reasons.add("no winner");
        Set<UUID> all = new LinkedHashSet<>(match.teamOne());
        all.addAll(match.teamTwo());
        if (sameIpBlocked(all)) reasons.add("same IP players");
        String pairKey = pairKey(match);
        long windowMs = Math.max(60, configs.get("duels/duels.yml").getInt("duels.ranked.validation.same-player-window-minutes", 60)) * 60_000L;
        int maxPairs = Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.validation.max-matches-vs-same-player", 4));
        if (countRecent(pairMatches.get(pairKey), windowMs) >= maxPairs) reasons.add("too many recent matches vs same opponent");
        int maxRepeatedWins = Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.validation.repeated-win-threshold", 3));
        if (countRecent(pairWins.get(pairOutcomeKey(match, winners)), windowMs) >= maxRepeatedWins) reasons.add("suspicious repeated result");
        if (isForfeitReason(reason)) {
            int threshold = Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.validation.repeated-forfeit-threshold", 3));
            for (UUID loser : losers(match, winners)) {
                if (countRecent(forfeits.get(loser), windowMs) + 1 >= threshold) reasons.add("repeated forfeit or disconnect");
            }
        }
        boolean valid = reasons.isEmpty();
        if (!valid) alert("Ranked MMR blocked for match " + match.id() + ": " + String.join(", ", reasons));
        return new RankedMatchValidationResult(true, valid, List.copyOf(reasons));
    }

    public void recordCompleted(DuelMatch match, Set<UUID> winners, String reason) {
        if (match == null || !match.ranked()) return;
        long now = System.currentTimeMillis();
        pairMatches.computeIfAbsent(pairKey(match), ignored -> new ArrayDeque<>()).addLast(now);
        if (winners != null && !winners.isEmpty()) pairWins.computeIfAbsent(pairOutcomeKey(match, winners), ignored -> new ArrayDeque<>()).addLast(now);
        if (isForfeitReason(reason)) {
            for (UUID loser : losers(match, winners)) forfeits.computeIfAbsent(loser, ignored -> new ArrayDeque<>()).addLast(now);
        }
        trim(pairMatches);
        trim(pairWins);
        trim(forfeits);
    }

    public void clear() {
        pairMatches.clear();
        pairWins.clear();
        forfeits.clear();
        queueDodgeUntil.clear();
    }

    private boolean sameIpBlocked(Set<UUID> players) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.ranked.validation.block-same-ip-mmr", true)) return false;
        Set<String> seen = new java.util.HashSet<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null) continue;
            String host = address.getAddress().getHostAddress();
            if (host == null || host.isBlank()) continue;
            if (!seen.add(host)) return true;
        }
        return false;
    }

    private boolean isForfeitReason(String reason) {
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return lower.contains("forfeit") || lower.contains("quit") || lower.contains("disconnect") || lower.contains("leave");
    }

    private List<UUID> losers(DuelMatch match, Set<UUID> winners) {
        Set<UUID> losers = new LinkedHashSet<>(match.teamOne());
        losers.addAll(match.teamTwo());
        if (winners != null) losers.removeAll(winners);
        return new ArrayList<>(losers);
    }

    private String pairKey(DuelMatch match) {
        List<String> ids = new ArrayList<>();
        for (UUID uuid : match.teamOne()) ids.add(uuid.toString());
        for (UUID uuid : match.teamTwo()) ids.add(uuid.toString());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return match.kitId().toLowerCase(Locale.ROOT) + ":" + String.join(":", ids);
    }

    private String pairOutcomeKey(DuelMatch match, Set<UUID> winners) {
        String winnerKey = winners == null || winners.isEmpty() ? "none" : winners.stream().map(UUID::toString).sorted().reduce((a, b) -> a + "," + b).orElse("none");
        return pairKey(match) + ":winner:" + winnerKey;
    }

    private int countRecent(Deque<Long> deque, long windowMs) {
        if (deque == null) return 0;
        long cutoff = System.currentTimeMillis() - windowMs;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) deque.removeFirst();
        return deque.size();
    }

    private void trim(Map<?, Deque<Long>> map) {
        long windowMs = Math.max(60, configs.get("duels/duels.yml").getInt("duels.ranked.validation.same-player-window-minutes", 60)) * 60_000L;
        long cutoff = System.currentTimeMillis() - Math.max(windowMs, 24L * 60L * 60L * 1000L);
        for (Deque<Long> deque : map.values()) {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) deque.removeFirst();
        }
    }

    private void alert(String message) {
        plugin.getLogger().warning(message);
        String formatted = "<red><bold>Ranked Alert</bold></red> <gray>" + message + "</gray>";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("duels.admin.alerts") || player.hasPermission("3smpcore.duel.admin")) Text.send(player, formatted);
        }
    }
}
