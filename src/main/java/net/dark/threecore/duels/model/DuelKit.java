package net.dark.threecore.duels.model;

import org.bukkit.Material;

import java.util.List;

public record DuelKit(String id, String displayName, Material icon, String permission, int slot, boolean enabled, List<String> lore, List<String> contents, List<String> armor, List<String> offhand, boolean autoApplyPotions, List<String> autoPotions, int rounds, boolean healthIndicator) {
}


