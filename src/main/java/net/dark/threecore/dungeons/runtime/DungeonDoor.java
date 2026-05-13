package net.dark.threecore.dungeons.runtime;

import org.bukkit.Location;

public interface DungeonDoor {
    void open();
    void close();
    void lock();
    void unlock();
    boolean isOpen();
    DungeonDoorState state();
    Location location();
}
