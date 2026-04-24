package net.dark.threecore.fishing;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.ArrayList;

public final class FishingGui {
    private static final int[] TRACK = {10, 11, 12, 13, 14, 15, 16};
    private final FishingRewardManager rewardManager;

    public FishingGui(FishingRewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    public Inventory build(FishingSession session) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.FISHING_MAIN, "session"), 27, rewardManager.title());
        fill(inv, rewardManager.frameItem());
        renderBar(inv, session, false);
        inv.setItem(22, button(Material.BARRIER, "<red>Exit</red>", List.of("<gray>Close the minigame.</gray>")));
        return inv;
    }

    public void renderWaiting(FishingSession session) {
        var player = Bukkit.getPlayer(session.playerId());
        if (player == null) return;
        Inventory inv = player.getOpenInventory().getTopInventory();
        fill(inv, rewardManager.frameItem());
        session.animationTick(session.animationTick() + 1);
        renderBar(inv, session, false);
        inv.setItem(22, button(Material.BARRIER, "<red>Exit</red>", List.of("<gray>Close the minigame.</gray>")));
        player.updateInventory();
    }

    public void renderFishing(FishingSession session) {
        var player = Bukkit.getPlayer(session.playerId());
        if (player == null) return;
        Inventory inv = player.getOpenInventory().getTopInventory();
        fill(inv, rewardManager.frameItem());
        session.animationTick(session.animationTick() + 1);
        renderBar(inv, session, true);
        inv.setItem(22, button(Material.BARRIER, "<red>Exit</red>", List.of("<gray>Close the minigame.</gray>")));
        player.updateInventory();
    }

    public void renderCaught(FishingSession session) {
        var player = Bukkit.getPlayer(session.playerId());
        if (player == null) return;
        Inventory inv = player.getOpenInventory().getTopInventory();
        fill(inv, rewardManager.frameItem());
        int frame = Math.floorMod(session.animationFrame(), 4);
        Material accent = switch (frame) {
            case 0 -> Material.GOLD_BLOCK;
            case 1 -> Material.YELLOW_STAINED_GLASS_PANE;
            case 2 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            default -> Material.LIME_STAINED_GLASS_PANE;
        };
        fill(inv, tinted(accent, " ", List.of()));
        inv.setItem(9, tinted(Material.YELLOW_STAINED_GLASS_PANE, "<yellow>* * *", List.of("<gray>Catch successful</gray>")));
        inv.setItem(13, button(Material.FISHING_ROD, "<green>Caught!</green>", List.of("<gray>You caught the fish.</gray>", "<gray>Rewards are being granted.</gray>")));
        inv.setItem(17, tinted(Material.YELLOW_STAINED_GLASS_PANE, "<yellow>* * *</yellow>", List.of("<gray>Catch successful</gray>")));
        inv.setItem(22, button(Material.BARRIER, "<red>Exit</red>", List.of("<gray>Close the minigame.</gray>")));
        player.updateInventory();
    }

    public void renderEscaped(FishingSession session) {
        var player = Bukkit.getPlayer(session.playerId());
        if (player == null) return;
        Inventory inv = player.getOpenInventory().getTopInventory();
        fill(inv, rewardManager.frameItem());
        inv.setItem(13, button(Material.BONE, "<red>The fish escaped</red>", List.of("<gray>Better luck next time.</gray>")));
        inv.setItem(22, button(Material.BARRIER, "<red>Exit</red>", List.of("<gray>Close the minigame.</gray>")));
        player.updateInventory();
    }

    private ItemStack fishItem(FishingSession session, String name, List<String> lore) {
        ItemStack item = session.fishIcon();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
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

    private void fill(Inventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item);
    }

    private void renderBar(Inventory inv, FishingSession session, boolean active) {
        int progressSlots = TRACK.length;
        int completed = active ? Math.min(progressSlots, session.clicks()) : 0;
        int fishPos = Math.floorMod(session.animationTick(), progressSlots);
        for (int idx = 0; idx < TRACK.length; idx++) {
            int i = TRACK[idx];
            Material material = idx < completed ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            if (idx == fishPos) material = Material.ORANGE_STAINED_GLASS_PANE;
            inv.setItem(i, tinted(material, idx == fishPos ? "<yellow>Click here</yellow>" : idx < completed ? "<green>Progress</green>" : "<gray>Wait</gray>", idx == fishPos ? List.of("<gray>Click 3 times before it escapes.</gray>", "<gray>Time left: " + rewardManager.remainingCatchSeconds(session) + "s</gray>") : List.of()));
        }
        session.currentSlot(TRACK[fishPos]);
        int readyTicks = active ? 3 - session.clicks() : 3;
        inv.setItem(13, fishItem(session, active ? "<yellow>Click the fish!</yellow>" : "<gray>Waiting...</gray>", List.of(
                "<gray>Progress:</gray> <white>" + session.clicks() + "/3</white>",
                "<gray>Time left:</gray> <white>" + rewardManager.remainingCatchSeconds(session) + "s</white>",
                "<gray>Hits left:</gray> <white>" + Math.max(0, readyTicks) + "</white>"
        )));
    }

    private ItemStack tinted(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
