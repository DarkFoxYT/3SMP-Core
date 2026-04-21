package net.dark.threecore.gui.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class CoreMenuHolder implements InventoryHolder {
    private final CoreMenuType type;
    private final String context;

    public CoreMenuHolder(CoreMenuType type, String context) {
        this.type = type;
        this.context = context;
    }

    public CoreMenuType type() { return type; }
    public String context() { return context; }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
