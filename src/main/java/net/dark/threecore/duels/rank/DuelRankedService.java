package net.dark.threecore.duels.rank;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.model.DuelMatch;
import net.dark.threecore.duels.model.DuelMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DuelRankedService {
    private final PlayerDataRepository repository;
    private final ConfigFiles configs;

    public DuelRankedService(PlayerDataRepository repository, ConfigFiles configs) {
        this.repository = repository;
        this.configs = configs;
    }

    public boolean enabled() {
        return configs.get("duels/duels.yml").getBoolean("duels.ranked.enabled", true);
    }

    public int defaultMmr() {
        return Math.max(0, configs.get("duels/duels.yml").getInt("duels.ranked.default-mmr", 1000));
    }

    public int mmr(UUID uuid, String kitId) {
        return repository.loadDuelKitStats(uuid, normalizeKit(kitId), defaultMmr()).mmr();
    }

    public PlayerDataRepository.DuelKitStats stats(UUID uuid, String kitId) {
        return repository.loadDuelKitStats(uuid, normalizeKit(kitId), defaultMmr());
    }

    public String rankName(int mmr) {
        return rankFor(mmr).name();
    }

    public String rankDisplay(int mmr) {
        return rankFor(mmr).display();
    }

    public Map<UUID, DuelRankedUpdate> recordMatch(DuelMatch match, Set<UUID> winners, boolean valid, List<String> invalidReasons) {
        Map<UUID, DuelRankedUpdate> updates = new LinkedHashMap<>();
        if (match == null) return updates;
        String kitId = normalizeKit(match.kitId());
        boolean ranked = match.ranked();
        if (ranked && match.mode() == DuelMode.SOLO && match.teamOne().size() == 1 && match.teamTwo().size() == 1 && !winners.isEmpty()) {
            UUID first = match.teamOne().iterator().next();
            UUID second = match.teamTwo().iterator().next();
            UUID winner = winners.contains(first) ? first : winners.contains(second) ? second : null;
            if (winner != null) {
                UUID loser = winner.equals(first) ? second : first;
                int winnerOld = mmr(winner, kitId);
                int loserOld = mmr(loser, kitId);
                int winnerChange = valid ? eloChange(winnerOld, loserOld, true) : 0;
                int loserChange = valid ? eloChange(loserOld, winnerOld, false) : 0;
                updates.put(winner, saveResult(winner, kitId, true, true, valid, winnerOld, winnerOld + winnerChange, winnerChange, invalidReasons));
                updates.put(loser, saveResult(loser, kitId, true, false, valid, loserOld, loserOld + loserChange, loserChange, invalidReasons));
            }
        }
        for (UUID uuid : match.teamOne()) {
            if (updates.containsKey(uuid)) continue;
            boolean win = winners.contains(uuid);
            updates.put(uuid, saveResult(uuid, kitId, ranked, win, false, mmr(uuid, kitId), mmr(uuid, kitId), 0, invalidReasons));
        }
        for (UUID uuid : match.teamTwo()) {
            if (updates.containsKey(uuid)) continue;
            boolean win = winners.contains(uuid);
            updates.put(uuid, saveResult(uuid, kitId, ranked, win, false, mmr(uuid, kitId), mmr(uuid, kitId), 0, invalidReasons));
        }
        return updates;
    }

    private DuelRankedUpdate saveResult(UUID uuid, String kitId, boolean ranked, boolean win, boolean rankedValid, int oldMmr, int requestedNewMmr, int change, List<String> invalidReasons) {
        int newMmr = Math.max(0, requestedNewMmr);
        PlayerDataRepository.DuelKitStats oldStats = repository.loadDuelKitStats(uuid, kitId, defaultMmr());
        repository.saveDuelKitStats(oldStats.recordResult(win, ranked, newMmr));
        return new DuelRankedUpdate(uuid, kitId, ranked, rankedValid, oldMmr, newMmr, change, rankName(oldMmr), rankName(newMmr), rankDisplay(oldMmr), rankDisplay(newMmr), invalidReasons == null ? List.of() : List.copyOf(invalidReasons));
    }

    private int eloChange(int own, int opponent, boolean win) {
        double expected = 1.0D / (1.0D + Math.pow(10.0D, (opponent - own) / 400.0D));
        double score = win ? 1.0D : 0.0D;
        int raw = (int) Math.round(kFactor() * (score - expected));
        int abs = Math.abs(raw);
        if (abs == 0) abs = minChange();
        abs = Math.max(minChange(), Math.min(maxChange(), abs));
        return win ? abs : -abs;
    }

    private int kFactor() {
        return Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.k-factor", 32));
    }

    private int minChange() {
        return Math.max(0, configs.get("duels/duels.yml").getInt("duels.ranked.min-change", 5));
    }

    private int maxChange() {
        return Math.max(minChange(), configs.get("duels/duels.yml").getInt("duels.ranked.max-change", 32));
    }

    private RankedDivision rankFor(int mmr) {
        List<RankedDivision> ranks = loadRanks();
        if (ranks.isEmpty()) return new RankedDivision("Bronze", "<#CD7F32>Bronze</#CD7F32>", 0);
        return ranks.stream().filter(rank -> mmr >= rank.minimum()).max(Comparator.comparingInt(RankedDivision::minimum)).orElse(ranks.get(0));
    }

    private List<RankedDivision> loadRanks() {
        ConfigurationSection section = configs.get("duels/duels.yml").getConfigurationSection("duels.ranked.ranks");
        if (section == null) return List.of(
                new RankedDivision("Bronze", "<#CD7F32>Bronze</#CD7F32>", 0),
                new RankedDivision("Silver", "<#C0C0C0>Silver</#C0C0C0>", 1100),
                new RankedDivision("Gold", "<gradient:#f4cd2a:#eda323:#d28d0d>Gold</gradient>", 1250),
                new RankedDivision("Platinum", "<#67E8F9>Platinum</#67E8F9>", 1400),
                new RankedDivision("Diamond", "<#60A5FA>Diamond</#60A5FA>", 1600),
                new RankedDivision("Master", "<#A855F7>Master</#A855F7>", 1800),
                new RankedDivision("Grandmaster", "<gradient:#f4cd2a:#eda323:#d28d0d>Grandmaster</gradient>", 2100)
        );
        List<RankedDivision> ranks = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(key);
            if (rank == null) continue;
            ranks.add(new RankedDivision(
                    rank.getString("name", key),
                    rank.getString("display", rank.getString("name", key)),
                    Math.max(0, rank.getInt("min", 0))
            ));
        }
        ranks.sort(Comparator.comparingInt(RankedDivision::minimum));
        return ranks;
    }

    private String normalizeKit(String kitId) {
        return kitId == null || kitId.isBlank() ? "default" : kitId.toLowerCase(Locale.ROOT);
    }

    private record RankedDivision(String name, String display, int minimum) {}
}
