package net.dark.threecore.market;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MarketRentService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MarketStorage storage;
    private final MoneyService moneyService;
    private final MarketWorldManager worldManager;

    public MarketRentService(JavaPlugin plugin, ConfigFiles configs, MarketStorage storage, MoneyService moneyService, MarketWorldManager worldManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.moneyService = moneyService;
        this.worldManager = worldManager;
    }

    public void start() {
        long interval = Math.max(20L, configs.get("world/market.yml").getLong("rent.check-interval-seconds", 3600L) * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::process, interval, interval);
    }

    public void process() {
        long now = System.currentTimeMillis();
        long grace = Math.max(0L, configs.get("world/market.yml").getLong("rent.grace-period-hours", 48L)) * 60L * 60L * 1000L;
        for (MarketPlot plot : storage.loadAll()) {
            if (plot.owner() == null) continue;
            if (plot.rent() <= 0D) continue;
            if (plot.rentDueAt() <= 0L) continue;
            if (now <= plot.rentDueAt() + grace) continue;
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.owner());
            moneyService.take(plot.owner(), plot.rent());
            if (owner.isOnline() && owner.getPlayer() != null) {
                Text.send(owner.getPlayer(), "<red>Your market plot rent expired and the plot was reclaimed.</red>");
            }
            storage.save(plot.withOwner(null, 0L, plot.lastPaidAt()));
        }
    }

    public boolean payRent(Player player, String plotId) {
        MarketPlot plot = storage.load(plotId);
        if (plot == null || plot.owner() == null || !plot.owner().equals(player.getUniqueId())) {
            Text.send(player, "<red>You do not own that plot.</red>");
            return false;
        }
        if (plot.rent() <= 0D) {
            Text.send(player, "<yellow>This plot does not use rent.</yellow>");
            return false;
        }
        if (!moneyService.take(player.getUniqueId(), plot.rent())) {
            Text.send(player, "<red>You cannot afford the rent.</red>");
            return false;
        }
        long nextDue = System.currentTimeMillis() + Math.max(1L, configs.get("world/market.yml").getLong("rent.period-days", 7L)) * 24L * 60L * 60L * 1000L;
        storage.save(plot.withOwner(player.getUniqueId(), nextDue, System.currentTimeMillis()));
        Text.send(player, "<green>Rent paid for <white>" + plot.name() + "</white>.</green>");
        return true;
    }

    public String formatRent(double value) {
        return moneyService.format(value);
    }
}
