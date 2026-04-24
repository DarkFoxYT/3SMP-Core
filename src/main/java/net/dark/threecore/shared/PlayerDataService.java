package net.dark.threecore.shared;

import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.model.PlayerProgressionData;

import java.util.UUID;

public final class PlayerDataService {
    private final PlayerDataRepository repository;

    public PlayerDataService(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public PlayerProgressionData load(UUID uuid) {
        return repository.load(uuid);
    }

    public void save(PlayerProgressionData data) {
        repository.save(data);
    }

    public double money(UUID uuid) {
        return repository.getMoneyBalance(uuid);
    }
}
