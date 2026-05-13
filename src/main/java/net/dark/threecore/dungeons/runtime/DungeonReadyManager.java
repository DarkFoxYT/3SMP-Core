package net.dark.threecore.dungeons.runtime;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class DungeonReadyManager {
    private final JavaPlugin plugin;
    private final Map<UUID, ReadyGroup> groups = new HashMap<>();

    public DungeonReadyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void create(UUID groupId, Set<UUID> players, BiConsumer<UUID, Set<UUID>> starter) {
        groups.put(groupId, new ReadyGroup(groupId, players, starter));
    }

    public boolean toggle(UUID groupId, Player player) {
        ReadyGroup group = groups.get(groupId);
        if (group == null) return false;
        boolean ready = group.toggle(player.getUniqueId());
        check(group);
        return ready;
    }

    public int readyCount(UUID groupId) {
        ReadyGroup group = groups.get(groupId);
        return group == null ? 0 : group.ready.size();
    }

    public int total(UUID groupId) {
        ReadyGroup group = groups.get(groupId);
        return group == null ? 0 : group.players.size();
    }

    public int countdown(UUID groupId) {
        ReadyGroup group = groups.get(groupId);
        return group == null ? 0 : group.countdown;
    }

    public void removePlayer(Player player) {
        for (ReadyGroup group : groups.values()) {
            if (group.players.remove(player.getUniqueId())) {
                group.ready.remove(player.getUniqueId());
                cancel(group, "Countdown cancelled because a player left.");
            }
        }
    }

    private void check(ReadyGroup group) {
        if (!group.players.isEmpty() && group.ready.containsAll(group.players)) {
            if (group.task == null) startCountdown(group);
        } else cancel(group, "Countdown cancelled.");
    }

    private void startCountdown(ReadyGroup group) {
        group.countdown = 5;
        group.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!group.ready.containsAll(group.players)) {
                cancel(group, "Countdown cancelled.");
                return;
            }
            for (UUID uuid : group.players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) Text.actionBar(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Dungeon starts in</gradient> <white>" + group.countdown + "</white>");
            }
            if (group.countdown-- <= 0) {
                BukkitTask task = group.task;
                group.task = null;
                if (task != null) task.cancel();
                group.starter.accept(group.groupId, Set.copyOf(group.players));
                groups.remove(group.groupId);
            }
        }, 0L, 20L);
    }

    private void cancel(ReadyGroup group, String message) {
        if (group.task != null) group.task.cancel();
        group.task = null;
        group.countdown = 0;
        for (UUID uuid : group.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) Text.actionBar(player, "<yellow>" + message + "</yellow>");
        }
    }

    private static final class ReadyGroup {
        private final UUID groupId;
        private final Set<UUID> players;
        private final Set<UUID> ready = new HashSet<>();
        private final BiConsumer<UUID, Set<UUID>> starter;
        private BukkitTask task;
        private int countdown;

        private ReadyGroup(UUID groupId, Set<UUID> players, BiConsumer<UUID, Set<UUID>> starter) {
            this.groupId = groupId;
            this.players = new HashSet<>(players);
            this.starter = starter;
        }

        private boolean toggle(UUID uuid) {
            if (!ready.add(uuid)) {
                ready.remove(uuid);
                return false;
            }
            return true;
        }
    }
}
