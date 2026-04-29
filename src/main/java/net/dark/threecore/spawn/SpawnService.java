package net.dark.threecore.spawn;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.survival.SurvivalService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SpawnService implements Listener {
    private net.dark.threecore.welcome.WelcomeService welcomeService;
    private SurvivalService survivalService;
    private PerkService perkService;
    private DuelService duelService;
    private PartyService partyService;
    private DungeonService dungeonService;
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public SpawnService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void setWelcomeService(net.dark.threecore.welcome.WelcomeService welcomeService) { this.welcomeService = welcomeService; }
    public void setSurvivalService(SurvivalService survivalService) { this.survivalService = survivalService; }
    public void setPerkService(PerkService perkService) { this.perkService = perkService; }
    public void setDuelService(DuelService duelService) { this.duelService = duelService; }
    public void setPartyService(PartyService partyService) { this.partyService = partyService; }
    public void setDungeonService(DungeonService dungeonService) { this.dungeonService = dungeonService; }

    public void setSpawnLocation(CommandSender sender, Location location) {
        var yaml = configs.get("core/config.yml");
        String worldName = location.getWorld() == null ? "spawn" : location.getWorld().getName();
        yaml.set("spawn.world", worldName);
        yaml.set("spawn.location.world", worldName);
        yaml.set("spawn.location.x", location.getX());
        yaml.set("spawn.location.y", location.getY());
        yaml.set("spawn.location.z", location.getZ());
        yaml.set("spawn.location.yaw", location.getYaw());
        yaml.set("spawn.location.pitch", location.getPitch());
        try {
            yaml.save(new File(plugin.getDataFolder(), "core/config.yml"));
        } catch (Exception ignored) {
        }
        Text.send(sender, "<green>Spawn location saved.</green>");
    }

    public void sendToSpawn(Player player) {
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        Location spawn = getSpawnLocation();
        if (spawn == null) {
            Text.send(player, "<red>Spawn is not configured.</red>");
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawn);
        if (survivalService != null) survivalService.loadProfile(player, "spawn");
        applySpawnEffects(player);
        refreshSpawnHubItems(player);
        clearSpawnEquipment(player);
        if (welcomeService != null) welcomeService.send(player);
        Text.send(player, "<green>Teleported to spawn.</green>");
    }

    public void applySpawnEffects(Player player) {
        if (!isSpawnWorld(player.getWorld())) return;
        int amplifier = Math.max(0, configs.get("core/config.yml").getInt("spawn.effects.speed.level", configs.get("core/config.yml").getInt("spawn.speed-level", 2)) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, amplifier, true, false, false));
    }

    public boolean isSpawnWorld(World world) {
        if (world == null) return false;
        String configured = configs.get("core/config.yml").getString("spawn.world", "spawn");
        return world.getName().equalsIgnoreCase(configured) || world.getName().equalsIgnoreCase("spawn");
    }

    public Location getSpawnLocation() {
        var yaml = configs.get("core/config.yml");
        String worldName = yaml.getString("spawn.world", "spawn");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().getFirst();
        if (world == null) return null;
        if (yaml.isConfigurationSection("spawn.location")) {
            var sec = yaml.getConfigurationSection("spawn.location");
            if (sec != null) {
                World explicit = Bukkit.getWorld(sec.getString("world", world.getName()));
                if (explicit != null) {
                    return new Location(explicit, sec.getDouble("x", explicit.getSpawnLocation().getX()), sec.getDouble("y", explicit.getSpawnLocation().getY()), sec.getDouble("z", explicit.getSpawnLocation().getZ()), (float) sec.getDouble("yaw", 0.0), (float) sec.getDouble("pitch", 0.0));
                }
            }
        }
        return world.getSpawnLocation();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (survivalService != null && isSpawnWorld(event.getPlayer().getWorld())) {
            survivalService.loadProfile(event.getPlayer(), "spawn");
        }
        applySpawnEffects(event.getPlayer());
        refreshSpawnHubItems(event.getPlayer());
        clearSpawnEquipment(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (survivalService != null && isSpawnWorld(event.getPlayer().getWorld())) {
            survivalService.loadProfile(event.getPlayer(), "spawn");
        }
        applySpawnEffects(event.getPlayer());
        refreshSpawnHubItems(event.getPlayer());
        clearSpawnEquipment(event.getPlayer());
    }

    public void refreshSpawnHubItems(Player player) {
        if (!isSpawnWorld(player.getWorld())) return;
        player.setItemOnCursor(null);
        for (int slot = 0; slot <= 8; slot++) {
            player.getInventory().setItem(slot, null);
        }
        if (perkService != null) perkService.giveCosmeticsItem(player);
        if (duelService != null) duelService.reloadItems(player);
        if (partyService != null) partyService.givePartyItems(player);
        if (dungeonService != null) dungeonService.giveItem(player);
        player.updateInventory();
    }

    private void clearSpawnEquipment(Player player) {
        if (!isSpawnWorld(player.getWorld())) return;
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }
}


