package net.dark.threecore.shared;

import net.dark.threecore.afk.VaultEconomyHook;
import net.dark.threecore.money.MoneyService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class EconomyService {
    private final VaultEconomyHook vaultHook;
    private final MoneyService moneyService;

    public EconomyService(JavaPlugin plugin, MoneyService moneyService) {
        this.vaultHook = new VaultEconomyHook(plugin);
        this.moneyService = moneyService;
    }

    public double balance(UUID uuid) { return moneyService.balance(uuid); }
    public String format(double amount) { return moneyService.format(amount); }
    public void give(UUID uuid, double amount) { moneyService.give(uuid, amount); }
    public boolean take(UUID uuid, double amount) { return moneyService.take(uuid, amount); }
    public boolean deposit(Player player, double amount) { return vaultHook.available() && vaultHook.deposit(player, Math.max(0L, Math.round(amount))); }
}
