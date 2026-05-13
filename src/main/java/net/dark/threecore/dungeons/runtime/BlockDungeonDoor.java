package net.dark.threecore.dungeons.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BlockDungeonDoor implements DungeonDoor {
    private final JavaPlugin plugin;
    private final Location origin;
    private final Material material;
    private final int width;
    private final int height;
    private final int slideDistance;
    private final int slideTicks;
    private final boolean particles;
    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private DungeonDoorState state = DungeonDoorState.CLOSED;

    public BlockDungeonDoor(JavaPlugin plugin, Location origin, Material material, int width, int height, int slideDistance, int slideTicks, boolean particles) {
        this.plugin = plugin;
        this.origin = origin;
        this.material = material == null ? Material.POLISHED_BLACKSTONE : material;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.slideDistance = Math.max(1, slideDistance);
        this.slideTicks = Math.max(1, slideTicks);
        this.particles = particles;
        close();
    }

    @Override
    public void open() {
        if (state == DungeonDoorState.LOCKED || state == DungeonDoorState.OPEN || state == DungeonDoorState.OPENING) return;
        state = DungeonDoorState.OPENING;
        play(Sound.BLOCK_PISTON_EXTEND);
        animate(true, () -> state = DungeonDoorState.OPEN);
    }

    @Override
    public void close() {
        if (origin.getWorld() == null) return;
        state = DungeonDoorState.CLOSING;
        restoreSnapshots();
        snapshots.clear();
        forEachDoorBlock(block -> {
            snapshots.add(new BlockSnapshot(block.getLocation(), block.getType()));
            block.setType(material, false);
        });
        play(Sound.BLOCK_PISTON_CONTRACT);
        state = DungeonDoorState.CLOSED;
    }

    @Override
    public void lock() {
        state = DungeonDoorState.LOCKED;
    }

    @Override
    public void unlock() {
        if (state == DungeonDoorState.LOCKED) state = DungeonDoorState.CLOSED;
    }

    @Override
    public boolean isOpen() {
        return state == DungeonDoorState.OPEN;
    }

    @Override
    public DungeonDoorState state() {
        return state;
    }

    @Override
    public Location location() {
        return origin.clone();
    }

    private void animate(boolean opening, Runnable done) {
        int steps = Math.max(1, slideTicks / 4);
        for (int i = 0; i <= steps; i++) {
            int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (origin.getWorld() == null) return;
                forEachDoorBlock(block -> block.setType(Material.AIR, false));
                int drop = (int) Math.round((double) slideDistance * ((double) step / (double) steps));
                if (opening) {
                    forEachDoorBlock(block -> {
                        Block below = block.getRelative(0, -drop, 0);
                        if (below.getY() >= origin.getWorld().getMinHeight()) below.setType(material, false);
                    });
                }
                if (particles) origin.getWorld().spawnParticle(Particle.DUST, origin, 12, 0.8, 0.8, 0.8, new Particle.DustOptions(org.bukkit.Color.fromRGB(250, 204, 21), 1.0F));
                if (step == steps) {
                    if (opening) forEachDoorBlock(block -> block.setType(Material.AIR, false));
                    done.run();
                }
            }, (long) step * 4L);
        }
    }

    private void forEachDoorBlock(java.util.function.Consumer<Block> consumer) {
        if (origin.getWorld() == null) return;
        int startX = origin.getBlockX() - width / 2;
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                consumer.accept(origin.getWorld().getBlockAt(startX + dx, origin.getBlockY() + dy, origin.getBlockZ()));
            }
        }
    }

    private void restoreSnapshots() {
        for (BlockSnapshot snapshot : snapshots) {
            if (snapshot.location().getWorld() != null) snapshot.location().getBlock().setType(snapshot.material(), false);
        }
    }

    private void play(Sound sound) {
        if (origin.getWorld() != null) origin.getWorld().playSound(origin, sound, 1.0F, 0.9F);
    }

    private record BlockSnapshot(Location location, Material material) {
    }
}
