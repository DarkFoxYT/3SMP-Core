package net.dark.threecore.market.shop;

import java.util.UUID;

public record ShopChestData(
        String world,
        int x,
        int y,
        int z,
        UUID owner,
        double price,
        int quantity,
        boolean enabled,
        String itemType,
        String itemName
) {
    public ShopChestData withOwner(UUID owner) { return new ShopChestData(world, x, y, z, owner, price, quantity, enabled, itemType, itemName); }
    public ShopChestData withPrice(double price) { return new ShopChestData(world, x, y, z, owner, price, quantity, enabled, itemType, itemName); }
    public ShopChestData withQuantity(int quantity) { return new ShopChestData(world, x, y, z, owner, price, quantity, enabled, itemType, itemName); }
    public ShopChestData withEnabled(boolean enabled) { return new ShopChestData(world, x, y, z, owner, price, quantity, enabled, itemType, itemName); }
    public ShopChestData withItem(String itemType, String itemName) { return new ShopChestData(world, x, y, z, owner, price, quantity, enabled, itemType, itemName); }
}
