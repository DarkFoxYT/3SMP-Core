package net.dark.threecore.souls;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class SoulGui {
    private final SoulManager manager;

    public SoulGui(SoulManager manager) {
        this.manager = manager;
    }

    public Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SOULS_MAIN, "souls"), 27, title());
        fill(inv, frame());
        inv.setItem(11, button(Material.ENDER_PEARL, "<gradient:#6b7280:#f3f4f6>Balance</gradient>", List.of("<gray>Current souls:</gray> <white>" + manager.balance(player.getUniqueId()) + "</white>")));
        inv.setItem(13, button(Material.EMERALD, "<gradient:#6b7280:#f3f4f6>Sell Souls</gradient>", List.of("<gray>Convert souls into money.</gray>")));
        inv.setItem(15, button(Material.CHEST, "<gradient:#6b7280:#f3f4f6>Reward Shop</gradient>", List.of("<gray>Spend souls on rewards.</gray>")));
        inv.setItem(22, button(Material.ARROW, "<gray>Close</gray>", List.of("<gray>Close this menu.</gray>")));
        return inv;
    }

    public Inventory buildRewards(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SOULS_MAIN, "souls-rewards"), 54, "<gradient:#6b7280:#f3f4f6>Souls Rewards</gradient>");
        fill(inv, frame());
        var section = manager.config().getConfigurationSection("rewards");
        int slot = 10;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var reward = section.getConfigurationSection(id);
                if (reward == null) continue;
                inv.setItem(slot, button(rewardIcon(reward.getString("icon", "PAPER")), reward.getString("display-name", id), List.of(
                        "<gray>Cost:</gray> <white>" + reward.getLong("cost", 0L) + "</white>",
                        "<gray>Coins:</gray> <white>" + reward.getLong("coins", 0L) + "</white>",
                        "<gray>Click to buy.</gray>"
                )));
                slot++;
                if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
                if (slot >= 45) break;
            }
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to souls menu.</gray>")));
        return inv;
    }

    private Material rewardIcon(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(java.util.Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack frame() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fill(Inventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item);
    }

    private String title() {
        return "<gradient:#6b7280:#f3f4f6>Souls</gradient>";
    }
}
