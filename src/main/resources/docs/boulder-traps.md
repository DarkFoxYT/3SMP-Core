# 3SMPCore Boulder Traps

## In-game workflow

1. Select the room bounds with the dungeon wand.
2. Run `/3smpcore dungeon boulder tool <roomId> <trapId>`.
3. Right click the spawn block.
4. Left click path blocks in order.
5. Shift-right click two blocks to set trigger pos1 and pos2.
6. Press Q to save. Sneak + Q clears the temporary edit session.
7. Run `/3smpcore dungeon boulder preview <roomId> <trapId>`.
8. Run `/3smpcore dungeon boulder test <roomId> <trapId>`.
9. Start the dungeon normally.

## MythicMobs

Copy `src/main/resources/mythicmobs/DungeonBoulder.yml` into:

`plugins/MythicMobs/Mobs/DungeonBoulder.yml`

The MythicMob is only the visual/anchor. 3SMPCore controls movement, trigger logic, collision, and killing.

## ModelEngine

1. Create/import a ModelEngine model with id `boulder`.
2. Set:

```yaml
boulder:
  mythicmob-id: "DungeonBoulder"
  modelengine-id: "boulder"
  animations:
    idle: "idle"
    rolling: "rolling"
```

3. Optional per-trap override:

```yaml
modelengine-id: "boulder"
```

The boulder only has two runtime states. Spawned boulders start in `IDLE` and request the `idle` loop. Triggered boulders switch to `ROLLING`, stop `idle`, and request the `rolling` loop while 3SMPCore controls movement, collision, killing, sounds, and particles.

If ModelEngine or the model is unavailable, 3SMPCore falls back to a full-bright BlockDisplay.
