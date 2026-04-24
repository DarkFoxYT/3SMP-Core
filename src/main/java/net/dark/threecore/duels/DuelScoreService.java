package net.dark.threecore.duels;

import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.model.PlayerProgressionData;

import java.util.UUID;

public final class DuelScoreService {
    private final PlayerDataRepository repository;

    public DuelScoreService(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public void recordKill(UUID uuid) {
        PlayerProgressionData data = repository.load(uuid);
        data.duelKills(data.duelKills() + 1);
        repository.save(data);
    }

    public void recordDeath(UUID uuid) {
        PlayerProgressionData data = repository.load(uuid);
        data.duelDeaths(data.duelDeaths() + 1);
        repository.save(data);
    }

    public void recordWin(UUID uuid) {
        PlayerProgressionData data = repository.load(uuid);
        data.recordDuel(true);
        repository.save(data);
    }

    public void recordLoss(UUID uuid) {
        PlayerProgressionData data = repository.load(uuid);
        data.recordDuel(false);
        repository.save(data);
    }
}
