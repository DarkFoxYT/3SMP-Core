package net.dark.threecore.afk;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AfkRewardService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final VaultEconomyHook economyHook;

    public AfkRewardService(JavaPlugin plugin, ConfigFiles configs, VaultEconomyHook economyHook) {
        this.plugin = plugin;
        this.configs = configs;
        this.economyHook = economyHook;
    }

    public void reward(Player player, long amount) {
        if (amount <= 0L) return;
        if (economyHook.deposit(player, amount)) {
            player.sendActionBar(Text.mm(configs.get("world/afk.yml").getString("messages.reward", "<green>AFK reward received.</green>")));
        } else {
            plugin.getLogger().warning("Vault reward failed for " + player.getName() + "; no economy provider was available.");
        }
    }
}
