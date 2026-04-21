# 3SMPCore Patch Log

## 2026-04-21

### Fixed
- Fixed duel countdown task repeating `beginFight()` and resetting players every second.
- Fixed duel result messages so winners are shown only to match participants.
- Fixed duplicated party manager ownership by keeping party hotbar items inside PartyService.
- Fixed protected hotbar item clicks opening menus without moving the item.
- Fixed sapphire command help and tab completion to remove player-to-player sapphire transfers.
- Added alternate PlaceholderAPI identifier `3smpcore` for TAB configs while keeping `smpcore` compatibility.
- Reworked launchpad target velocity calculation to solve toward configured coordinates using flight ticks, gravity, and velocity caps.
- Added disable buttons to cosmetic category menus.
- Sanitized badge previews so invalid ItemsAdder placeholder text does not render as question marks in chat.
- Added rank-based chat color fallback from `colors.yml`.

### Added
- Added `/leave` to leave a duel queue or forfeit an active duel.
- Added online player name autocomplete for `/duel <player>`.
- Added duel map editor selection and deletion commands.
- Added temporary arena editor worlds named `arena_<id>_edit`.
- Saved marker-edited maps against their arena editor world instead of the generic `world`.
- Added ItemsAdder-ready welcome message system with MiniMessage lines, title, subtitle, and join sound.
- Added dungeon module with `/dungeon` and `/dungeons` commands.
- Added dungeon hotbar item next to the duel item.
- Added configurable dungeon levels using Y-level room bands.
- Added dungeon room reservations in `dungeon_rooms.yml` to prevent room overlap.
- Added shulker-themed configurable room generation framework.
- Added configurable spawn protection radius with unbreakable/unplaceable protection.
- Added infinite Speed II and Saturation in protected spawn radius.
- Added ZonePvP module with `/zonepvp pos1`, `/zonepvp pos2`, `/zonepvp respawn`, `/zonepvp toggle`, and `/zonepvp reload`.
- Added ZonePvP kit snapshots, no-fall, death cleanup, respawn point, and kill-streak kit upgrades.
- Added polished dungeon menu layout and dungeon trap persistence commands.
- Added `dungeon_traps.yml` for saved trap locations by room.
- Added armor stand based duel map editor markers for lobby, spawn A, spawn B, spectator, min bound, and max bound.
- Added `/duel map marker <type>` and `/duel map savemarkers <mapId>`.
- Added duel win streak and best win streak tracking.
- Added PlaceholderAPI values for duel streaks.
- Added streak hologram configuration placeholders.
- Added solo-fill support for 2v2 queues by combining four solo queued players into two teams.
- Added optional duel world instance service for temporary per-match worlds.
- Added Cosmetics hub hotbar item and reorganized cosmetics categories.
- Added PlaceholderAPI values for money and sapphires for TAB configs.

- Updated duel queue sword lore with live 1v1 and 2v2 queue counts.

### Changed
- Added duel world restrictions so duel commands/items can be enabled for `spawn` and disabled for `world`.
- Added gem disabled-world support so gem logic can be blocked in configured worlds.
- Restores duel queue item shortly after match cleanup so hotbar tools survive duel completion.
- Party remains universal and is not world-restricted.
- ZonePvP removes spawn speed/saturation while players are inside the PvP zone.
- ZonePvP kits are removed on death and when leaving the zone.
- Reworked dungeons from generated box rooms into saved room templates.
- Dungeon room saving now uses two white shulker boxes as automatic pos1/pos2 bounds.
- Dungeon templates save up to 64x64x64 and store non-air blocks plus marker shulkers.
- Yellow shulker boxes are saved as entrances with facing direction.
- Orange shulker boxes are saved as room connectors.
- Light blue shulker boxes are saved as enemy spawns.
- Red shulker boxes are saved as exits.
- Purple shulker boxes are saved as trigger zones.
- Green shulker boxes are saved as player spawn markers.
- Black shulker boxes are saved as boss markers.
- Dungeon levels are now Jungle, Desert, Volcanic, Icy, and Ancient, with levels 2-5 marked coming soon.
- Added `/d` alias for dungeon commands.
- Party disband now restores the goat horn/create-party item after clearing party state.
- Added first Party Duel setup UI for red/blue teams, kit, mode, and rounds.
- Updated Cosmetics UI and hotbar item to use a gold-focused palette.
- Updated dungeon item/menu identity to use a purple dungeon gradient and Sculk Shrieker icon instead of Ender Chest.
- ZonePvP now explicitly suppresses spawn Speed/Saturation while a player is active in the PvP zone.
- Spawn protection no longer cancels damage or PvP for active ZonePvP players.
- ZonePvP kit refresh no longer grants saturation as a hidden immortality-style buff.
- Sapphires are now command-delivered only, non-tradeable, and non-exchangeable by config.
- `/pay` remains money-only through the survival money system.
- Added configurable launchpad physics values to `launchpads.yml`.
- Reworked party menu to remove stats, 2v2 queue, queue info, and disband buttons.
- Reworked party hotbar flow: goat horn creates a party, lectern opens party manager, barrier disbands for leaders.
- Reworked duel main menu to only show 1v1 and 2v2 modes.
- Reworked duel kit menu spacing so kits are separated by glass panes.
- Replaced default duel kits with Sword, Axe, NethPot, Bow, Crystal PvP, SMP, and UHC.