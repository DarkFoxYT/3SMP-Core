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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SOULS_MAIN, "souls"), 45, title());
        fill(inv, frame());
        inv.setItem(4, button(Material.SOUL_LANTERN, "<gradient:#6b7280:#f3f4f6>Soul Forge</gradient>", List.of("<gray>Dungeon souls become power here.</gray>", "<gray>Current souls:</gray> <white>" + manager.balance(player.getUniqueId()) + "</white>")));
        inv.setItem(20, button(Material.ENCHANTING_TABLE, "<gradient:#8b5cf6:#f3f4f6>Random Dungeon Enchant</gradient>", List.of("<gray>Put an item in the forge slot.</gray>", "<gray>Roll a random dungeon enchant.</gray>")));
        inv.setItem(22, button(Material.EMERALD, "<gradient:#22c55e:#f3f4f6>Convert to Cash</gradient>", List.of("<gray>Convert all souls into money.</gray>")));
        inv.setItem(24, button(Material.CHEST, "<gradient:#6b7280:#f3f4f6>Reward Shop</gradient>", List.of("<gray>Spend souls on rewards.</gray>")));
        inv.setItem(40, button(Material.ARROW, "<gray>Close</gray>", List.of("<gray>Close this menu.</gray>")));
        return inv;
    }

    public Inventory buildForge(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SOULS_MAIN, "souls-forge"), 45, "<gradient:#8b5cf6:#f3f4f6>Soul Enchant Forge</gradient>");
        fill(inv, frame());
        inv.setItem(4, button(Material.SOUL_LANTERN, "<gradient:#8b5cf6:#f3f4f6>Soul Enchant Forge</gradient>", List.of("<gray>Balance:</gray> <white>" + manager.balance(player.getUniqueId()) + "</white>", "<gray>Only opens at dungeon spawn.</gray>")));
        inv.setItem(20, button(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>Item Slot</light_purple>", List.of("<gray>Place your target item here.</gray>")));
        inv.setItem(24, button(Material.ENCHANTED_BOOK, "<gradient:#8b5cf6:#f3f4f6>Roll Enchant</gradient>", List.of("<gray>Cost:</gray> <white>" + manager.enchantCost() + " souls</white>", "<gray>Applies a random dungeon enchant.</gray>")));
        inv.setItem(40, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to souls menu.</gray>")));
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
