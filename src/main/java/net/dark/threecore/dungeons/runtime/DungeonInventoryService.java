package net.dark.threecore.dungeons.runtime;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DungeonInventoryService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public DungeonInventoryService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void save(UUID sessionId, Player player) {
        YamlConfiguration yaml = data();
        String path = "sessions." + sessionId + ".players." + player.getUniqueId();
        yaml.set(path + ".name", player.getName());
        yaml.set(path + ".inventory", player.getInventory().getContents());
        yaml.set(path + ".armor", player.getInventory().getArmorContents());
        yaml.set(path + ".offhand", player.getInventory().getItemInOffHand());
        yaml.set(path + ".level", player.getLevel());
        yaml.set(path + ".exp", player.getExp());
        yaml.set(path + ".health", player.getHealth());
        yaml.set(path + ".food", player.getFoodLevel());
        yaml.set(path + ".saturation", player.getSaturation());
        if (config().getBoolean("inventory.save-potion-effects", true)) yaml.set(path + ".effects", player.getActivePotionEffects().stream().toList());
        if (config().getBoolean("inventory.save-gamemode", true)) yaml.set(path + ".gamemode", player.getGameMode().name());
        yaml.set(path + ".restored", false);
        save(dataFile(), yaml);
    }

    @SuppressWarnings("unchecked")
    public boolean restore(Player player) {
        YamlConfiguration yaml = data();
        String found = findPlayerPath(yaml, player.getUniqueId());
        if (found == null) return false;
        if (yaml.getBoolean(found + ".restored", false)) return false;
        List<?> inv = yaml.getList(found + ".inventory", List.of());
        List<?> armor = yaml.getList(found + ".armor", List.of());
        player.getInventory().setContents(inv.toArray(new ItemStack[0]));
        player.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));
        Object offhand = yaml.get(found + ".offhand");
        if (offhand instanceof ItemStack item) player.getInventory().setItemInOffHand(item);
        player.setLevel(yaml.getInt(found + ".level", 0));
        player.setExp((float) yaml.getDouble(found + ".exp", 0.0D));
        double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.max(1.0D, Math.min(max, yaml.getDouble(found + ".health", max))));
        player.setFoodLevel(yaml.getInt(found + ".food", 20));
        player.setSaturation((float) yaml.getDouble(found + ".saturation", 5.0D));
        if (config().getBoolean("inventory.save-potion-effects", true)) {
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            Collection<?> effects = yaml.getList(found + ".effects", List.of());
            for (Object effect : effects) if (effect instanceof PotionEffect potionEffect) player.addPotionEffect(potionEffect);
        }
        if (config().getBoolean("inventory.save-gamemode", true)) {
            try { player.setGameMode(GameMode.valueOf(yaml.getString(found + ".gamemode", "SURVIVAL"))); } catch (IllegalArgumentException ignored) {}
        }
        yaml.set(found + ".restored", true);
        save(dataFile(), yaml);
        return true;
    }

    public String debug(UUID uuid) {
        YamlConfiguration yaml = data();
        String found = findPlayerPath(yaml, uuid);
        if (found == null) return "No saved dungeon inventory.";
        return "Saved inventory at " + found + " restored=" + yaml.getBoolean(found + ".restored", false);
    }

    public void emergencyRestore(Player player) {
        if (config().getBoolean("inventory.emergency-restore-on-join", true)) restore(player);
    }

    private String findPlayerPath(YamlConfiguration yaml, UUID uuid) {
        var sessions = yaml.getConfigurationSection("sessions");
        if (sessions == null) return null;
        for (String session : sessions.getKeys(false)) {
            String path = "sessions." + session + ".players." + uuid;
            if (yaml.isConfigurationSection(path) && !yaml.getBoolean(path + ".restored", false)) return path;
        }
        return null;
    }

    private YamlConfiguration config() {
        return configs.get("dungeons/dungeons.yml");
    }

    private YamlConfiguration data() {
        return YamlConfiguration.loadConfiguration(dataFile());
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "dungeons/inventory-saves.yml");
    }

    private void save(File file, YamlConfiguration yaml) {
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save dungeon inventory data: " + ex.getMessage());
        }
    }
}
