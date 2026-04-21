package net.dark.threecore.glow;

import org.bukkit.DyeColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GlowManager {
    private final Map<UUID, DyeColor> glow = new HashMap<>();
    public void set(Player player, DyeColor color) { glow.put(player.getUniqueId(), color); player.setGlowing(true); }
    public DyeColor get(Player player) { return glow.get(player.getUniqueId()); }
    public void clear(Player player) { glow.remove(player.getUniqueId()); player.setGlowing(false); }
}
