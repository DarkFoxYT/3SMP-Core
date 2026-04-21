# 3SMPCore

3SMPCore is the main general-purpose core plugin for the 3 SMP server.

## Included systems

- Perks framework with chat prefixes, tags, colors, cosmetics, and effects
- Sapphire currency wrapper around PlayerPoints
- Gem framework with PDC-based socket data and seasonal registries
- SQLite storage by default

## Build

```bash
./gradlew build
```

## Setup

1. Drop the built jar into your Paper 1.21.x server.
2. Install optional integrations if used:
   - ItemsAdder
   - PlaceholderAPI
   - PlayerPoints
3. Start the server once to generate configs.
4. Edit the YAML files in `plugins/3SMPCore/`.

## Store delivery examples

- `sap give <player> <amount>`
- `perks unlock <player> <perkId>`
- `perks setprefix <player> <prefixId>`
- `gems give <player> <gemId> <amount>`
- `gems giveextractor <player> <amount>`
