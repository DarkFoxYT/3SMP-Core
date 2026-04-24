package net.dark.threecore.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public final class FishingSession {
    private final UUID playerId;
    private final long startedAt;
    private final long readyAt;
    private final long catchDeadline;
    private final Deque<Integer> targetSlots = new ArrayDeque<>();
    private final FishingRewardManager rewardManager;
    private int clicks;
    private boolean caught;
    private int fishCount;
    private String rarity;
    private int currentSlot = -1;
    private int fishDisplayCycle;
    private int animationTick;
    private int animationFrame;

    public FishingSession(UUID playerId, long startedAt, long readyAt, long catchDeadline, int fishCount, String rarity, FishingRewardManager rewardManager) {
        this.playerId = playerId;
        this.startedAt = startedAt;
        this.readyAt = readyAt;
        this.catchDeadline = catchDeadline;
        this.fishCount = fishCount;
        this.rarity = rarity;
        this.rewardManager = rewardManager;
        this.currentSlot = rewardManager.defaultFishSlot();
    }

    public UUID playerId() { return playerId; }
    public long startedAt() { return startedAt; }
    public long readyAt() { return readyAt; }
    public long catchDeadline() { return catchDeadline; }
    public int clicks() { return clicks; }
    public boolean caught() { return caught; }
    public int fishCount() { return fishCount; }
    public String rarity() { return rarity; }
    public int currentSlot() { return currentSlot; }
    public int fishDisplayCycle() { return fishDisplayCycle; }
    public int animationTick() { return animationTick; }
    public int animationFrame() { return animationFrame; }

    public void fishDisplayCycle(int fishDisplayCycle) { this.fishDisplayCycle = fishDisplayCycle; }
    public void animationTick(int animationTick) { this.animationTick = animationTick; }
    public void animationFrame(int animationFrame) { this.animationFrame = animationFrame; }
    public void currentSlot(int currentSlot) { this.currentSlot = currentSlot; }

    public void startCatch(Player player, FishingGui gui) {
        gui.renderWaiting(this);
    }

    public boolean ready(long now) { return now >= readyAt && !caught; }
    public boolean expired(long now) { return now > catchDeadline && !caught; }

    public void click(Player player, FishingGui gui) {
        if (caught || expired(System.currentTimeMillis())) return;
        clicks++;
        if (clicks >= 3) {
            caught = true;
            gui.renderCaught(this);
        } else {
            currentSlot = rewardManager.randomFishSlot(currentSlot);
            gui.renderFishing(this);
        }
    }

    public int rewardCount() { return fishCount; }

    public void reduceFishCount() { fishCount = Math.max(0, fishCount - 1); }

    public ItemStack fishIcon() { return rewardManager.fishIcon(rarity); }
    public Material frameMaterial() { return rewardManager.frameMaterial(); }
}
