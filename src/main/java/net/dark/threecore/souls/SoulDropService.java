package net.dark.threecore.souls;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.zonepvp.ZonePvpService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulDropService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final SoulManager soulManager;
    private final DuelService duelService;
    private final DungeonService dungeonService;
    private final ZonePvpService zonePvpService;
    private final Map<String, Long> victimCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> pairCooldowns = new ConcurrentHashMap<>();

    public SoulDropService(JavaPlugin plugin, ConfigFiles configs, SoulManager soulManager, DuelService duelService, DungeonService dungeonService, ZonePvpService zonePvpService) {
        this.plugin = plugin;
        this.configs = configs;
        this.soulManager = soulManager;
        this.duelService = duelService;
        this.dungeonService = dungeonService;
        this.zonePvpService = zonePvpService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        if (!enabledInWorld(victim.getWorld().getName())) return;
        if (duelService.isPlayerInDuel(victim.getUniqueId()) || DuelService.isDuelPlayer(victim)) {
            if (!config().getBoolean("drop.duels.enabled", false)) return;
        }
        if (dungeonService != null && DungeonService.isDungeonPlayer(victim)) return;
        if (zonePvpService != null && !zonePvpService.inZone(victim.getLocation())) return;
        if (!rollChance(victim, killer)) return;
        long amount = rewardAmount(victim, killer);
        if (amount <= 0L) return;
        soulManager.give(killer.getUniqueId(), amount);
        killer.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gradient:#6b7280:#f3f4f6>+ " + amount + " souls</gradient>"));
    }

    public boolean canDrop(Player victim, Player killer) {
        return rollChance(victim, killer);
    }

    public long rewardAmount(Player victim, Player killer) {
        long base = Math.max(0L, config().getLong("drop.amount", 1L));
        if (victim == null || killer == null) return 0L;
        return base;
    }

    private boolean rollChance(Player victim, Player killer) {
        if (pairCooldowns(victim.getUniqueId(), killer.getUniqueId())) return false;
        double chance = Math.max(0.0D, config().getDouble("drop.chance", 0.25D));
        if (ThreadLocalRandom.current().nextDouble() > chance) return false;
        markCooldown(victim.getUniqueId(), killer.getUniqueId());
        return true;
    }

    private boolean pairCooldowns(UUID victim, UUID killer) {
        long now = System.currentTimeMillis();
        long victimUntil = victimCooldowns.getOrDefault(victim.toString(), 0L);
        long pairUntil = pairCooldowns.getOrDefault(victim + ":" + killer, 0L);
        return victimUntil > now || pairUntil > now;
    }

    private void markCooldown(UUID victim, UUID killer) {
        long until = System.currentTimeMillis() + Math.max(1000L, config().getLong("drop.cooldown-seconds", 600L) * 1000L);
        victimCooldowns.put(victim.toString(), until);
        pairCooldowns.put(victim + ":" + killer, until);
    }

    private boolean enabledInWorld(String worldName) {
        String candidate = worldName == null ? "" : worldName.toLowerCase(java.util.Locale.ROOT);
        return config().getStringList("drop.disabled-worlds").stream().map(s -> s.toLowerCase(java.util.Locale.ROOT)).noneMatch(candidate::equals);
    }

    private YamlConfiguration config() {
        return configs.get("economy/souls.yml");
    }
}
