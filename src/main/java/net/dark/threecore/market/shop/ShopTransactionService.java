package net.dark.threecore.market.shop;

import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ShopTransactionService {
    private final MoneyService moneyService;
    private final ShopStockService stockService;
    private final ShopChestStorage storage;

    public ShopTransactionService(MoneyService moneyService, ShopStockService stockService, ShopChestStorage storage) {
        this.moneyService = moneyService;
        this.stockService = stockService;
        this.storage = storage;
    }

    public boolean buy(Player buyer, ShopChestData data, Inventory chestInventory) {
        if (data == null || !data.enabled()) {
            Text.send(buyer, "<red>This shop is disabled.</red>");
            return false;
        }
        int amount = Math.max(1, data.quantity());
        double total = Math.max(0.01D, data.price() * amount);
        if (!moneyService.take(buyer.getUniqueId(), total)) {
            Text.send(buyer, "<red>You cannot afford this purchase.</red>");
            return false;
        }
        if (stockService.take(chestInventory, data, amount) < amount) {
            moneyService.give(buyer.getUniqueId(), total);
            Text.send(buyer, "<red>This shop is out of stock.</red>");
            return false;
        }
        Player owner = buyer.getServer().getPlayer(data.owner());
        if (owner != null) moneyService.give(owner.getUniqueId(), total);
        else moneyService.give(data.owner(), total);
        ItemStack stack = new ItemStack(org.bukkit.Material.valueOf(data.itemType()), amount);
        buyer.getInventory().addItem(stack);
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
        Text.send(buyer, "<green>Purchased <white>" + amount + "x " + data.itemName() + "</white> for <gold>" + moneyService.format(total) + "</gold>.</green>");
        if (stockService.empty(chestInventory, data)) {
            storage.save(data.withEnabled(false));
            if (owner != null) Text.send(owner, "<red>Your shop chest ran out of stock and was disabled.</red>");
        }
        return true;
    }
}
