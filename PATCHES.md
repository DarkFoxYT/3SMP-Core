# 3SMPCore Patch Log

## 2026-04-21

### Fixed
- Added `admin/ranks.yml` as the canonical rank setup/delivery config, auto-creating missing LuckPerms groups and syncing missing chat/visual rank entries for existing servers.
- Routed `/rank give`, `/rank sub`, and `/3smpcore rankperms apply` through the new rank service while keeping legacy `admin/permissions.yml` rank packages as fallback.
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

### Fixed
- Fixed Party Duel setup menu clicks being routed into normal party menu actions.
- Red/blue wool buttons no longer create, leave, or disband parties.
- Added dedicated Party Duel setup click handler with button feedback.
- Added a Back button to the Party Duel setup menu.
- Reworked Party invite UI into a paged online-player head picker.
- Party invite picker excludes the inviter and existing party members.
- Reworked Party members button into a member-head list panel.
- Removed create-party and disband-party actions from the Party GUI; those stay on the protected hotbar items.
- Rebuilt the 3SMP dev panel into a 54-slot admin hub with duel testing, duel toggles, map editor, reload, launchpads, and tool refresh actions.
- Added duel map editor mode with protected marker tools for lobby, spawn A, spawn B, spectator, min bound, and max bound.
- Duel editor marker tools now place armor stands by right-clicking air or blocks and preserve the player yaw/pitch on the marker.
- Duel map selection now teleports devs into an editor world and automatically enables/gives editor tools.
- Added Multiverse-Core softdepend and Multiverse registration/import hooks for duel editor and temporary match worlds.
- ZonePvP exit no longer teleports players back to their entry location.
- ZonePvP exit now restores the pre-zone inventory/hotbar loadout in place.
- ZonePvP exit reapplies spawn Speed/Saturation when the player exits back into the protected spawn radius.
- Added Party Duel member picker integration for red and blue team setup.
- Party Duel red/blue wool buttons now open party member head pickers.
- Party Duel member picker toggles members between teams and updates the setup menu preview.
- Added `/spectate <player>` and `/spec <player>` for duel spectator mode.
- `/leave` now exits duel spectator mode before queue/duel leave handling.
- Duel queue join/leave feedback now uses actionbar instead of chat spam.
- Queue item/menu refresh now runs after leaving queue so queued icons revert faster.
- Party Duel rounds now cycle directly from the rounds button and the unused mode button was removed.
- Cleaned default duel kit config toward Sword, Axe, NethPot, SMP, and UHC themed presets.
- Reworked dungeon menu into a level/options/start flow with solo/party toggle when in a party.
- Added dungeon base kit with wooden sword, leather armor, and food on entry.
- Added dungeon active-run money rewards per mob kill with configurable easy/normal/hard values.
- Added dungeon void/Multiverse generation config placeholders.
- Added dungeon room roles for entrance, normal, boss, and exit templates.
- Dungeon template saving now detects room role from markers and stores a size category.
- Dungeon generation now builds a room plan with entrance first, randomized pooled rooms, boss room, then exit room.
- Dungeon room selection avoids duplicates until the room pool is exhausted and respects configured room counts by difficulty.
- Added dungeon completion/reward config placeholders for early exits and full clears.
- Added config-driven `/shop` with category and item purchase GUIs using the Money economy.
- Added `shop.yml` with configurable categories, materials, amounts, prices, and display names.
- Added AFK void-zone support that sends inactive players to `afk_void` and returns them to spawn on input.
- Improved ClearLag with disabled worlds, max removals per run, named-item protection, and persistent-entity protection.
- Added organized config placeholders for shop, AFK zone, and safer ClearLag behavior.
- Spawn protection now supports protecting the entire configured spawn world instead of only the spawn radius.
- Spawn protection now blocks bucket use, interactable block modification, explosions, fire, fluid flow, block fade/grow, leaves decay, and entity block changes.
- Added `spawn.protection.entire-world`, `spawn.protection.bypass-permission`, and `spawn.protection.cancel-physics` config options.
- Hard-migrated config resources into grouped folders: core, economy, duels, dungeons, world, cosmetics, gems, social, admin, and menus.
- Updated all Java config lookups and save targets to the new grouped paths.
- Moved party config into `social/party.yml` and main plugin config into `core/config.yml`.
- Added `CONFIG_LAYOUT.md` documenting the new config folder layout.
- Removed reliance on the old flat config resource layout for new installs.
- Fixed startup crash after config reorg by removing `saveDefaultConfig()` root `config.yml` usage.
- Grouped config saving now owns `core/config.yml` generation.
- Added join queue module with void queue world, frozen movement, actionbar queue position, release-to-spawn, and delayed welcome message.
- Added LuckPerms-compatible queue bypass and priority permission support.
- Added `core/join-queue.yml` for queue world, release, and priority configuration.
- Added duel arena selector GUI for choosing an existing arena to edit.
- Duel editor mode now snapshots/restores the developer hotbar/inventory instead of mixing editor tools with normal items.
- Added save-arena editor tool and `/savearena` command to save arena marker settings from the temp editor world.
- Changed the dungeon hotbar item icon from Sculk Shrieker to Compass.
- Reworked dungeon UI into compact main, level, difficulty, and party selection panels.
- Fixed protected hotbar item cursor ghosting by clearing the cursor and refreshing inventory after menu-opening clicks.
- Duel match start now explicitly strips hub/spawn hotbar items, cursor item, armor, and offhand before applying the duel kit.
- Duel match cleanup now refreshes duel, party, cosmetics, and dungeon hotbar items after restoring the player snapshot.
- Post-duel hotbar refresh now updates the client inventory to prevent stale or broken spawn items.


## 2026-04-22

### Fixed
- Disabled duel map editor mode automatically when a player changes worlds or quits, restoring the saved inventory instead of leaving editor tools stuck on the player.
- Reworked duel lethal damage handling so duel losses do not open the vanilla death or respawn flow.
- Moved eliminated duel players into spectator mode for the post-fight result window, then returns both sides to spawn after 5 seconds.
- Cleared duel armor, offhand items, fire, spectator target, and temporary duel state during match cleanup.
- Refreshed spawn/hub items after duel cleanup so protected hotbar tools work again after a match.

### Changed
- Enabled per-match duel world instances by default so fights run in temporary copied arena worlds.
- Temporary duel match worlds are unloaded, unregistered from Multiverse when applicable, and deleted after the fight ends.
- Duel editor worlds can now be created as clean configurable generator worlds instead of requiring an already-loaded source world.
- Added configurable duel editor world settings for copy-source, generator, and auto-save.
- Arena saves now force-save the editor world so built arena changes persist for future duel instances.

### Fixed
- Spawn PvP protection now allows combat inside the configured ZonePvP region while still blocking PvP in protected spawn outside that region.
- Projectile PvP checks now resolve the player shooter so bows/crossbows follow the same spawn-vs-zone rules.

### Changed
- ZonePvP now forces PvP enabled on its configured world when the module loads or reloads, while spawn protection continues to deny normal spawn combat through plugin rules.
- Added `pvp.enabled` to `world/zonepvp.yml` for explicit ZonePvP world PvP control.

### Added
- Added persistent dungeon completion tracking by player UUID, dungeon level, and difficulty.
- Added Nightmare difficulty to the active dungeon difficulty menu and generation config.
- Added Nightmare unlock gating requiring configured lower difficulty clears before entry.
- Added boss clear rewards for dungeons: 25 sapphires and $10,000 money by default.

### Fixed
- Dungeon boss kills now mark the cleared difficulty for unlock progression.
- Nightmare dungeon death now fails the active run and sends a specific failure message.
- Dungeon world creation now uses the configured generator and registers with Multiverse when available.
- Duel map editor armor stand markers are removed automatically after saving an arena so they do not remain visible in playable maps.

### Fixed
- Dungeon room generation now clears the saved template volume before pasting, resetting reused dungeon room space back to its saved state each run.
- Duel editor and temporary match worlds now disable natural mob, patrol, trader, warden spawning, and weather cycle by default.
- Dungeon worlds now disable natural mob, patrol, trader, warden spawning, and weather cycle by default.
- Multiverse registration for dungeon worlds now also disables monster and animal spawning where Multiverse-Core is installed.

### Changed
- Added config toggles for duel instance/editor world spawning and weather rules.
- Added `generation.reset-before-paste` and dungeon world spawning/weather toggles.

### Added
- Added in-game duel kit editor accessible from the dev panel or `/duel kiteditor <kit>`.
- Kit editor supports editing the 36-slot inventory contents, four armor slots, offhand slot, save, and cancel.
- Added essential admin commands for speed, fly, gamemode/gm, gms, gmc, gma, gmsp, time, and gamerule.

### Fixed
- Duel cleanup now clears all inventories after snapshot restore before sending players to spawn, preventing duel kit/armor/offhand transfer.
- Duel queue sword is no longer given while a player is inside an active duel or ZonePvP kit.
- Party, dungeon, and cosmetics hotbar items are suppressed while a player is inside ZonePvP.
- ZonePvP active state is now exposed centrally so hub item refreshers can respect PvP kit boundaries.

### Added
- Added WorldGuard/WorldEdit soft integration for ZonePvP.
- ZonePvP now creates or updates a configurable WorldGuard cuboid region from `zone.pos1` and `zone.pos2`.
- Synced ZonePvP WorldGuard regions set PvP to allow, deny natural mob spawning, and use configurable priority.
- Added `worldguard` settings to `world/zonepvp.yml` for region id, priority, passthrough, mob spawning, and region-query behavior.

### Changed
- ZonePvP reload and position commands now resync the WorldGuard region automatically when WorldGuard is installed.
- Added EngineHub Maven and WorldGuard compile-only dependency for typed API integration.
- Added WorldGuard and WorldEdit to plugin soft dependencies.

### Fixed
- Fixed duel cleanup spectator-target crashes by only clearing spectator targets while the player is actually in spectator mode.
- Duel cleanup now respawns dead players before restoring/teleporting them, so `/kill` or vanilla death states do not trap players after a match.
- Duel active-player tracking now self-heals stale flags instead of command-locking players after a broken cleanup.
- Duel queue swords are now stripped while a player is flagged as actively dueling, not only while a match lookup exists.
- Spectator `/leave` now safely clears spectator targets without throwing if Bukkit already changed the player's mode.
- Removed invalid Multiverse monster/animal spawning commands from dungeon world creation; natural spawning is controlled through gamerules instead.
- OP players now bypass spawn build protection while normal players remain blocked.

### Changed
- Duel kit editor saves full serialized ItemStack data with slot indexes, preserving stack amounts, potion meta, enchants, item names, durability, armor/offhand, and layout.
- New/no-row money balances now default to $1,000,000.
- Chat formatting now restores selected prefix and tag rendering from the cosmetics prefix/tag configs while still applying message color cosmetics.
- Hub hotbar items are suppressed during active duels and ZonePvP, then refreshed cleanly after duel cleanup.

### Fixed
- `/spawn`, `/survival`, RTP, and duel post-match returns now force players back into Survival mode and safely clear spectator targets first.
- Duel post-match cleanup now always removes frozen countdown state before restoring players and refreshing hub items.
- Duel map editor save now behaves as a complete save-and-exit flow: removes editor markers, saves the arena, restores the developer inventory, returns the developer to spawn, and closes editor state.
- Removed the free-floating map editor toggle from the dev panel; editor tools are now tied to selecting an arena and saving that arena.
- RTP now resolves to the configured survival world by default instead of accidentally using the player's current spawn world.
- RTP candidate generation is async, while Bukkit world safety checks and teleporting happen back on the main thread.

### Changed
- Duel countdown now teleports and kits players before the countdown, freezes movement while still allowing hotbar organization, plays a tick sound each second, and shows a large START title with a level-up sound when the fight begins.
- Duel dev panel labels now explain the arena selection/save flow instead of exposing a manual editor toggle.
- Added `default-world` and `defaults` sections to `world/rtp.yml` for easier future RTP expansion.

### Added
- Added duel kit metadata for `auto-apply-potions` and `rounds`, loaded from `duels/kits.yml` and editable from the kit editor.
- Added auto splash-potion visuals at duel start for kits with `auto-apply-potions: true`.
- Added `/duel <player>` challenge kit selection flow; the challenger now picks the kit from the duel kit UI before the challenge is sent.
- Added randomized duel map selection that avoids repeating the previously selected map when multiple enabled maps exist.
- Added ZonePvP staged upgrades every configured kill interval, randomizing armor piece upgrades before sword upgrade.
- Added ZonePvP upgrade money rewards and kill ding sounds.
- Added ZonePvP kill streak display through the XP level/progress and actionbar.
- Added dungeon spawn commands: `/d spawn` and `/d setspawn`.
- Added dungeon dev save tool via `/d dev`, with shift-right-click marker toolbox and right-click room saving.
- Added config-driven hologram displays for duel leaderboard, survival money leaderboard, and dungeon display placeholders.

### Fixed
- Duel entry now clears saturation, exhaustion, fire, fall distance, and all potion effects before applying the kit.
- Non-admin `/duel` usage is now restricted to direct player challenges plus accept/deny/leave support.
- Starter money default changed from $1,000,000 to $1,000 for new/no-row players.
- Dungeon compass item only opens the dungeon UI; dungeon teleporting is tied to explicit start/spawn actions.

### Changed
- `world/zonepvp.yml` now includes `upgrade.interval-kills`, `upgrade.money-reward`, and upgrade order.
- `world/holograms.yml` now uses named display definitions with location, yaw, type, and line spacing.

## 2026-04-22

### Fixed
- Fixed party duel setup buttons so red/blue selectors no longer trigger party create/disband item logic.
- Fixed party duel kit selection by routing the setup menu into the duel kit registry.
- Fixed duel totem handling by allowing vanilla totem resurrection before custom duel elimination runs.
- Fixed duel arena marker placement to save the admin's exact location, yaw, and pitch for spawn markers.
- Removed obsolete duel arena bounds marker tools from the editor flow.

### Added
- Added automatic red/blue assignment for two-player party duel setups.
- Added sign-based party duel round input with dashed guide lines.
- Added sign-based kit editor round input with dashed guide lines.
- Added four kit editor auto-potion slots that are saved to `duels/kits.yml` and splashed on duel start when enabled.
- Added kit-level health indicator configuration metadata for future duel HUD wiring.
- Added default duel kill effect feedback with enchant particles and a ding sound.
- Added kill effects as a cosmetics menu category with configurable entries.
- Added automatic durability tooltip lore for duel kit weapons and armor during kit application.
- Added arena publishing from edit worlds into clean `arena_<id>_live` playable templates.

### Changed
- Party duel setup now starts real configured party duels through the duel service using selected teams and kit.
- Duel arena saves now remove editor markers before publishing the playable arena template.
- Duel match world instances now copy from the latest published live arena after editor saves.
- Duel kit definitions now include `auto-potions`, `rounds`, and `health-indicator` fields.

## 2026-04-23

### Added
- Added persistent friend system with `/friend` and `/friends` commands for list, add, accept, deny, and remove actions.
- Added clickable friend request chat buttons for accept and deny flows.
- Added SQLite-backed `player_friends` storage and repository methods for persistent friend data.
- Added `social/friends.yml` for friend and tab-social configuration placeholders.
- Added social tab view service that cycles player-side TAB modes between Global, Party, and Dungeon when sneaking.
- Added PlaceholderAPI values for social and TAB integration: `%smpcore_party_members%`, `%smpcore_dungeon_members%`, `%smpcore_friends_count%`, `%smpcore_friends_list%`, `%smpcore_tab_mode%`, `%smpcore_tab_title%`, and `%smpcore_tab_members%`.
- Added direct `/friend` and `/friends` command registration.
- Added Team Dungeon entry point to the Party GUI.

### Changed
- Party invite picker now sorts friend players first before other online candidates.
- Party service now integrates with the dungeon module so the party leader can open dungeons directly in party mode.
- Dungeon startup flow now supports party runs from the existing dungeon UI instead of treating all runs as solo-only.
- Placeholder expansions now expose party, dungeon, friend, and social tab state for external plugins like TAB.

### Fixed
- Welcome/join flow patch notes were kept in sync by recording the new friend, TAB, and party-dungeon integration pass.
- Party dungeon starts now validate that all party members are online before launch.
- Party dungeon starts now validate per-member difficulty access, including Nightmare unlock requirements.
- Active dungeon group membership is now tracked per player so dungeon-specific TAB views can show the current team.
- Party dungeon failure now clears the active run for the group when a member dies, preventing stale dungeon teammate state.

### Fixed
- Chat prefixes are now always rendered as a core chat feature using LuckPerms first and a configured fallback prefix when LuckPerms has no value.
- Prefix rendering no longer depends on cosmetics selection state.

### Changed
- Added `chat.default-prefix` to `core/config.yml` so servers can control the fallback prefix shown when a player has no LuckPerms prefix.

### Fixed
- Duel matchmaking now starts matches immediately after finding an opponent instead of showing a second queue-side countdown.
- Duel arena starts still use the configured start countdown, with blindness applied on match found and cleared when the arena countdown ends.

### Added
- Added a custom fishing minigame with GUI timing, moving fish targets, rarity rolls, rewards, and persistent fishing stats.
- Added fishing session storage and reward handling with Vault coin payouts and ItemsAdder-ready fish icons/rewards.
- Added `/3SMPCore` integration points for fishing GUI routing through the shared chest-menu listener system.
- Added a chest-GUI Daily Reward system with `/daily`, `/rewards`, and `/3smpcore daily`.
- Added SQLite-backed daily claim storage with streak tracking, 24-hour cooldowns, and weekly/monthly bonus reward support.
- Added configurable daily reward entries with coins, commands, items, and ItemsAdder-ready icon/item placeholders.
- Added Sapphire shop categories for crate keys, gem extractors, gem capsules, cosmetics, and donor ranks with balance checks before purchase.
- Added gem capsule shop support with Rough, Polished, Cut, Flawless, and Mystery tiers.
- Added `/gems capsules` and `/gems givecapsule <player> <tier> <amount>`.

### Fixed
- Duel rounds now track score across the full match and continue into additional rounds until a team reaches the configured win threshold.
- Duel match-found blindness now only applies during the arena transition and is cleared when the fight starts.
- Duel instance worlds now force a VoidGen-style generator and `generateStructures(false)` to avoid terrain generation in unexplored chunks.
- LuckPerms prefixes remain the always-on chat prefix source and do not depend on badges or cosmetic state.
- Party duel round selection is now passed into the actual match instead of being ignored at start.
- Party duel setup continues to use the saved arena and rounds values when starting from the GUI or sign input.
- Duel queue UI now shows the actual queued mode and kit instead of only generic queue counts.
- Duel match instances are recreated from the saved arena template before players are teleported in, keeping live maps reset between fights.
- Duel queue timers now use actionbar only instead of chat messages, so queue updates no longer spam chat.
- Duel arena maps are now protected from breaking unless the block was placed during the current match.
- Chat rendering no longer injects cosmetic badges ahead of LuckPerms prefixes.

### Added
- Added duel-only kill effect templates in `cosmetics/cosmetics.yml`, with the default spark effect and placeholder premium variants.

## 2026-04-24

### Added
- Added declared `/afk`, `/speed`, `/fly`, `/gamemode`, `/gm`, `/gms`, `/gmc`, `/gma`, `/gmsp`, `/time`, and `/gamerule` commands to `plugin.yml`.
- Added `admin/permissions.yml` as a grouped permission reference for setup and staff configuration.
- Added a real Chat Colors category to the Cosmetics hub and linked it from the summary screen.
- Added AFK zone subsystem with `/3smpcore afkzone wand|create|delete|list`, zone tracking, Vault-backed coin rewards, and AFK state handling.

### Changed
- Chat rendering now resolves LuckPerms prefixes as the always-on prefix source and expands PlaceholderAPI placeholders in prefix text for ItemsAdder-friendly rank formatting.
- Chat color rendering now happens on the final rendered chat component so selected colors apply consistently.
- Cosmetics summary now reflects the always-on LuckPerms prefix model instead of treating prefixes like a cosmetic toggle.
- Essential utility commands now support granular permission nodes alongside the legacy `3smpcore.essentials` bypass permission.
- AFK rewards now only run inside configured AFK zones and respect duel/dungeon/creative/spectator exclusions.

### Fixed
- `/speed`, `/fly`, and gamemode shortcut commands now reject use during active duels.
- Cosmetic chat colors now have a real GUI path and rank-aware unlock checks instead of only existing in config.
- AFK zone state no longer conflicts with the legacy AFK timeout manager.

### Added
- Added a Souls system with kill-based drops, PvP zone drops, optional duel drops, anti-farm cooldowns, persistent balance storage, and a `/souls` chest GUI.
- Added Souls reward trading for coins, items, and commands with configurable shop entries.
- Added `SoulManager`, `SoulDropService`, `SoulGui`, and `SoulStorage` as the core Souls subsystem structure.

### Added
- Added a Market District system with a separate Multiverse world, plot ownership storage, weekly rent handling, and `/market` chest GUI access.
- Added market plot admin tools for wand-based region setup, plot creation, deletion, naming, pricing, rent values, and trust lists.
- Added market plot protection rules so only owners and trusted players can build inside their plots in the market world.
- Added market world bootstrap and auto-registration support for the configured market world.

### Added
- Added a shop chest system for owned market plots with buyer purchase GUI, owner settings GUI, and owner shift-right-click chest access.
- Added shop chest storage, stock handling, and transaction services with Vault-backed payouts to the chest owner.
- Added shop chest enable/disable behavior that auto-disables empty shops and notifies the owner.
- Added shop chest configuration support for item, price, quantity, and fair-price enforcement hooks.

### Added
- Added shared wrapper services for economy, messages, GUI theming, and player data access.
- Added top-level `config.yml`, `messages.yml`, `gui.yml`, `rewards.yml`, `fishing.yml`, `souls.yml`, and `market.yml` defaults for cleaner server-side configuration entry points.
- Added `/3smpcore reload` coverage for market plot refreshes so the market layer stays in sync after config reloads.

### Fixed
- Fishing rod casts now open the fishing GUI through the actual cast event flow, while cancelling vanilla fishing behavior so normal fish catches do not run.
- Added `/fishdebug` and `/fishingdebug` for live fishing session diagnostics, guarded by `3smpcore.fishing.debug`.
- `/market` now routes players into the market world before opening the market GUI, with a `/markert` alias for the common typo.
- The market hub now exposes clearer plot info and settings actions from the chest GUI.
- Survival now saves and clears the live inventory before teleporting to spawn or survival, preventing item leakage between spawn and survival profiles.
- Survival now treats the overworld, nether, and end as the same survival family for inventory profile saving/loading.
- Market world now shares the same survival-side inventory profile instead of falling back to spawn inventory.
- Survival-family deaths now respawn at spawn only when no bed or anchor respawn point exists.
- Dungeon entry now saves the player inventory profile first and clears the live inventory before applying the dungeon kit, preventing spawn items from carrying into dungeon runs.
- Survival-world command usage is now restricted to the configured allowed commands only.
- The duel home menu no longer routes through the dead formats screen; 1v1 kit selection and party duel queue flow now open directly from the main chest GUI.
- Fishing now opens from both the real cast event path and a right-click rod fallback, so fishing rods reliably open the arcade GUI.
- The accidental `/markert` alias was removed so only `/market` remains.
- Duel dev panel now includes a dungeon editor shortcut that teleports devs into the dungeon world with the room marker tools ready.
- Duel home now shows only the real duel options: `1v1` and `Party Duels`.
- The dungeon compass item now teleports players to dungeon spawn instead of opening the dungeon GUI directly.
- `/dungeon` now routes players to dungeon spawn, while the dev panel owns the dungeon editor entry.
- Dungeons now use their own saved `dungeon` inventory profile instead of sharing spawn items.
- `/leave` now exits dungeon space as well as duels.
- Dungeon runtime and dungeon editor now use separate void worlds, with structure generation disabled by default.
- Fishing now only opens the arcade GUI from an actual water cast path instead of non-water rod use.
- Dungeon editor tools are now hardened against clicks, drags, drops, and slot scrambling, with the main save tool snapping back into its fixed toolbar slot.
- Gamerule handling now uses the Paper registry-based lookup path instead of deprecated legacy gamerule APIs.
- Dungeon editor tool now only toggles in the dungeon editor world, and the dungeon editor/live/spawn worlds are separated by default.
- Dungeon spawn now uses its own void world instead of sharing the live dungeon world.
- Dungeon room chaining now rejects templates that do not expose compatible markers for connection.
- Dungeon builder diagnostics now print live debug messages for editor sessions, template saves, room placement, and flexible link checks.
- Dungeon room placement now uses flexible offsets instead of a rigid grid layout so rooms can connect more naturally across offsets and height changes.
- Dungeon connection preview now shows a dedicated debug state plus marker counts and size info directly in the dev toolbox.
- Fixed the duel dev panel dungeon editor handoff by wiring the dungeon service into the duel module after plugin bootstrap, preventing null crashes from the editor shortcut.
- Duel kit selector now hides rounds and auto-splash details and instead shows kit description plus the current queued player count for that kit.
- Added a one-time `/dungeon template` initializer that prepares the dungeon spawn world, locks itself after first use, and added nested `/dungeon spawn set` handling for dungeon teleport spawn placement.
- Dungeon editor now strips spawn/dungeon clutter on entry, keeps the editor tool flow clean, and the connection preview now reports the detected room type using player spawn, entrance, boss, exit, or normal markers.
- Added `/dungeon editor leave` so devs can exit the dungeon editor cleanly, clear editor clutter, and return to the dungeon spawn flow.
- Natural mob spawning is now cancelled in dungeon worlds and dungeon spawn, while custom/plugin-spawned dungeon mobs are still allowed through the custom spawn path.
- Dungeon mob spawning is now controlled by a config whitelist of spawn reasons, defaulting to `CUSTOM` for plugin-spawned mobs only.
- `/leave` now exits dungeon runs back to dungeon spawn instead of routing players to the main spawn world.
- Duel live/editor arena worlds now force a flat, no-structures bootstrap so the copied arena stays isolated from extra terrain generation outside the arena copy.
- Duel leave/disconnect handling now announces the exit on-screen first and delays arena cleanup slightly so the instance world is not torn down while players are still being transferred out.
- Dungeon room saving now persists full block data strings alongside materials so stateful blocks keep their orientation and blockstate when templates are pasted back in.
- Duel start now re-applies the selected kit one tick after teleport and skips hub-item refreshes for active duel players, preventing the cosmetics item from overwriting the round kit during arena entry.
- Duel state reset now preserves SPEED potion effects instead of stripping them, so arena buffs and kit-applied speed are not wiped during duel setup.
- Spawn/dungeon-spawn speed handling is now limited to managed spawn worlds only, and duel round resets now fully heal players while clearing placed blocks and dropped round items between rounds.
- Team duels now use proper red/blue temporary scoreboard teams, block friendly fire, and only award a round when every player on one team has been eliminated.
- Duel countdown freeze now zeroes player velocity as well as movement position, making the 5-second round-start lock feel solid instead of letting players slide.
- Duel live-world cleanup now also tracks bucket-placed fluids and only records/removes player-placed blocks inside active duel instance worlds, keeping every placed block from the match cleared out reliably.
- Duel round cleanup now hard-removes common temporary arena materials like oak planks, cobwebs, obsidian, cobblestone, stone, lava, and water from loaded duel instance chunks, both between rounds and before match-world teardown.
- Duel players now get explicit red/blue name coloring during team matches, and name/list names are restored cleanly after the duel ends.
- Added a duel-side golden-apple inventory resync after consumption to reduce ghost-gap desyncs during matches.
- Chat formatting now resolves LuckPerms prefixes as the always-on prefix source, then applies configurable rank chat styles for player name color, tag color, and fallback message color using `chat.rank-styles` in `core/config.yml`.
- Added configurable rank-based chat styling examples for owner, admin, dev, mvp, and vip so LuckPerms primary groups can drive visible chat theming without turning prefixes into cosmetics.
- Removed prefix selection from the cosmetics/perks system so prefixes are no longer treated as a player cosmetic and are now expected to come automatically from LuckPerms only.
- Chat formatting now runs at MONITOR priority and supports direct rank `name-format` and `tag-format` templates, making gradient player names and tag styling configurable per LuckPerms rank instead of relying only on recolor passes.
- Added example gradient `name-format` and `tag-format` entries for owner, admin, dev, mvp, and vip in `core/config.yml` so player names no longer stay white when a rank style is configured.
- Added PlaceholderAPI placeholders for TAB integration so backend-formatted chat visuals can be reused in TAB: `%smpcore_chat_prefix%`, `%smpcore_chat_name%`, `%smpcore_chat_tag%`, `%smpcore_chat_display%` and the same aliases under `%3smpcore_*%`.
- Placeholder expansions now receive the chat formatter directly, allowing LuckPerms prefix output, rank-based gradient name formats, and styled tags to be exposed consistently to other plugins like TAB.
- Chat now uses a sturdier LuckPerms provider lookup path instead of the old brittle plugin-reflection API call, so chat prefixes and primary-group based name colors stop falling back to the fake default member prefix.
- The old `[Member]` chat fallback prefix was removed from default config and plain hex message coloring now renders correctly instead of using malformed MiniMessage hex tags.
- Duel arena editor worlds now always spawn players at `0,64,0` and switch them into creative mode on entry.
- Duel world service now performs a stale match-world cleanup sweep for old `arena_*_match_*` worlds so leftover instance folders do not break future arena matches.
- Party duel start now uses the selected arena and selected round count directly instead of falling back to random arena selection or the kit default rounds.
- Party duel round restarts now re-run the prepare phase so the freeze timer works on every round, not only the first one.
- Duel main menu `2v2` button now uses a static shield icon instead of a player head to avoid the weird visual changing/pose behavior in the GUI.
- Party duel setup now shows the selected arena and round count directly in the main menu, kit picker, and map picker, so the chosen values are visible before starting.
- Party duel map selection cards now show the current arena selection and kit cards now show the current kit selection, making the setup UI easier to trust and less twitchy.
