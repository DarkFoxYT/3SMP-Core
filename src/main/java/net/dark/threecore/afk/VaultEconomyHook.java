package net.dark.threecore.afk;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class VaultEconomyHook {
    private final JavaPlugin plugin;
    private Object economy;
    private Method depositPlayer;

    public VaultEconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        economy = null;
        depositPlayer = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(econClass);
            if (provider == null) return;
            economy = provider.getProvider();
            depositPlayer = economy.getClass().getMethod("depositPlayer", Player.class, double.class);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Vault economy hook unavailable: " + ex.getMessage());
            economy = null;
            depositPlayer = null;
        }
    }

    public boolean available() {
        return economy != null && depositPlayer != null;
    }

    public boolean deposit(Player player, long amount) {
        if (!available()) return false;
        try {
            Object response = depositPlayer.invoke(economy, player, (double) amount);
            return response == null || !response.toString().toLowerCase(java.util.Locale.ROOT).contains("error");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Vault deposit failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}
