package net.dark.threecore.market.shop;

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class ShopStockService {
    public int stock(Inventory inventory, ShopChestData data) {
        if (inventory == null || data == null) return 0;
        int count = 0;
        Material type = parse(data.itemType());
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (stack.getType() != type) continue;
            count += stack.getAmount();
        }
        return count;
    }

    public boolean empty(Inventory inventory, ShopChestData data) {
        return stock(inventory, data) <= 0;
    }

    public int take(Inventory inventory, ShopChestData data, int amount) {
        int need = Math.max(1, amount);
        int removed = 0;
        Material type = parse(data.itemType());
        for (int slot = 0; slot < inventory.getSize() && removed < need; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != type) continue;
            int take = Math.min(stack.getAmount(), need - removed);
            stack.setAmount(stack.getAmount() - take);
            removed += take;
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
        }
        return removed;
    }

    public String describe(ItemStack stack) {
        return stack == null ? "AIR" : stack.getType().name();
    }

    private Material parse(String input) {
        try { return Material.valueOf(input.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return Material.STONE; }
    }
}
