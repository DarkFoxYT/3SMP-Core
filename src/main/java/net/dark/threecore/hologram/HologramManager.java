package net.dark.threecore.hologram;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class HologramManager {
    private final JavaPlugin plugin; private final List<ArmorStand> stands = new ArrayList<>();
    public HologramManager(JavaPlugin plugin) { this.plugin = plugin; }
    public ArmorStand spawn(Location location, String text) { ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, s -> { s.setInvisible(true); s.setMarker(true); s.setGravity(false); s.customName(Text.mm(text)); s.setCustomNameVisible(true); }); stands.add(stand); return stand; }
    public void removeAll() { for (Entity entity : new ArrayList<>(stands)) entity.remove(); stands.clear(); }
}
