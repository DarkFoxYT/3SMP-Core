package net.dark.threecore.dungeons.api;

import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.dungeons.engine.DungeonRoomDefinition;
import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;
import net.dark.threecore.dungeons.runtime.DungeonSession;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class DungeonAPI {
    private static DungeonService service;

    private DungeonAPI() {
    }

    public static void install(DungeonService dungeonService) {
        service = dungeonService;
    }

    public static UUID startDungeon(Collection<Player> players, String dungeonId) {
        return service == null ? null : service.startDungeonApi(players, dungeonId);
    }

    public static boolean endDungeon(UUID sessionId) {
        return service != null && service.endDungeonApi(sessionId);
    }

    public static DungeonSession getSession(Player player) {
        return service == null ? null : service.getDungeonSession(player);
    }

    public static boolean isInDungeon(Player player) {
        return service != null && service.isInDungeonApi(player);
    }

    public static PlacedDungeonRoom getCurrentRoom(Player player) {
        DungeonSession session = getSession(player);
        return session == null ? null : session.currentRoom();
    }

    public static void registerRoomType(DungeonRoomDefinition definition) {
        if (service != null) service.registerRoomDefinition(definition);
    }

    public static void registerRoomTrigger(String id, Runnable trigger) {
        if (service != null) service.registerRoomTrigger(id, trigger);
    }

    public static void registerLootProvider(String id, Object provider) {
        if (service != null) service.registerLootProvider(id, provider);
    }

    public static void registerMobProvider(String id, Object provider) {
        if (service != null) service.registerMobProvider(id, provider);
    }
}
