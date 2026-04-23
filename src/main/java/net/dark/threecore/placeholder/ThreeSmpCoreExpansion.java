package net.dark.threecore.placeholder;

import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.social.SocialTabService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.warp.WarpManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ThreeSmpCoreExpansion extends SmpCoreExpansion {
    public ThreeSmpCoreExpansion(JavaPlugin plugin, PerkService perkService, WarpManager warpManager, SpawnService spawnService, MoneyService moneyService, SapphireService sapphireService, PartyService partyService, DungeonService dungeonService, FriendService friendService, SocialTabService socialTabService) {
        super(plugin, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService);
    }

    @Override public String getIdentifier() { return "3smpcore"; }
}
