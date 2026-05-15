package net.dark.threecore.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class CrateHolder implements InventoryHolder {
    enum Mode {
        PREVIEW,
        OPENING
    }

    private final String crateId;
    private final Mode mode;

    CrateHolder(String crateId, Mode mode) {
        this.crateId = crateId;
        this.mode = mode;
    }

    String crateId() {
        return crateId;
    }

    Mode mode() {
        return mode;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
