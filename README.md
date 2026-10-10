# Mapper Zones (Minecraft Forge 1.20.1)

Mapper Zones is a server-authoritative trigger-zone toolkit for Forge 1.20.1. It includes zone selection, zone triggers, conditions, actions, global/player variables, JSON import/export, networking and lightweight editor screens.

## Requirements
- Minecraft Forge 1.20.1 (47.2.20)
- Java 17
- Internet access on the first build so Gradle and Forge dependencies can be downloaded

## Build on Windows
1. Install a 64-bit Java 17 JDK and make sure `java -version` reports 17.
2. Extract the project folder.
3. Open a terminal in this folder and run `gradlew.bat build`.
4. The mod jar will be in `build\libs\MapperZones-1.0.0.jar`.

The included `gradlew.bat` bootstraps Gradle 8.8 if it is not already cached. It is a small self-contained bootstrap script; it does not require a separate wrapper JAR.

## Commands
- `/mapperzones gui` — open the zone browser.
- `/mapperzones wand` — give the selection wand.
- `/mapperzones create <name>` — create a box from the two selected points.
- `/mapperzones remove <name>` — remove a zone.
- `/mapperzones list` — list zones.
- `/mapperzones enable <name> <true|false>` — toggle a zone.
- `/mapperzones reset <name>` — clear one-time trigger history.
- `/mapperzones export <name>` and `/mapperzones import <name>` — JSON export/import in `config/mapperzones/exports`.
- `/mapperzones action <name> <enter|exit|stay> <action...>` — append an action.
- `/mapperzones condition <name> <condition...>` — append a condition.
- `/mapperzones var set <global|player> <name> <value...>` — set a variable.
- `/mapperzones var get <global|player> <name>` and `/mapperzones var add <global|player> <name> <amount>`.
- `/mapperzones var remove <global|player> <name>` — remove a variable.

## Selection and action syntax
Use the wand on two blocks to select opposite corners. Then run `/mapperzones create <name>`.

Action strings supported by the action executor:
- `command:<server command without leading slash>`
- `delay <ticks> <action...>`
- `sound:<minecraft:sound_id> [volume] [pitch]`
- `particle:<minecraft:particle_id> [count]`
- `effect:<minecraft:effect_id> <seconds> <amplifier>`
- `teleport:<x> <y> <z>`
- `freeze:<ticks>`
- `inventory:save` / `inventory:restore`
- `redstone:<x> <y> <z> <ticks>` — temporarily sets a redstone block and restores the previous block
- `zone:enable <zone>` / `zone:disable <zone>` / `zone:reset <zone>`

Each condition line is ANDed with the other lines. Expression lines support comparisons (`==`, `!=`, `>`, `<`, `>=`, `<=`), logical operators (`&&`, `||`), parentheses and quoted strings. Prefer the `player.` prefix for player properties:

- `player.health >= 10`, `player.max_health == 20`
- `player.food >= 6 && player.health < player.max_health`
- `player.name == "Steve"`
- `player.dimension == "minecraft:overworld"`
- `player.level >= 5`, `player.experience > 0.5`, `player.saturation >= 3`
- `player.absorption > 0`, `player.armor >= 10`, `player.air < player.max_air`
- `player.x >= 0`, `player.y < 80`, `player.z != 0`
- Boolean properties: `player.on_ground`, `player.sprinting`, `player.sneaking`, `player.alive`, `player.in_water`, `player.underwater`, `player.invisible`, `player.sleeping`
- `player.uuid == "..."`, `player.team == "red"`
- Custom personal variables can still be referenced as `player.quest == "started"`; global variables as `global.score >= 5`.

Legacy forms remain supported: `health >= 10`, `level >= 5`, `item minecraft:diamond`, `team <name>`, `var <scope> <name> <operator> <value>`, `time <operator> <ticks>`, `weather clear|rain|thunder`, and `dim <namespace:dimension>`. The wand renderer displays each zone name above its boundary while the wand is held; disabled zones are marked `[OFF]`.

Text supports `${name}` for global variables and `${player.name}` for the current player's personal variables.

## Data and safety
Zone data is saved as world SavedData; variables are persisted with it. The JSON export folder is under the server's config directory. Imported JSON is parsed and validated before a zone is replaced. Commands and actions run on the logical server only.

## Maintenance notes (1.20.1)

- Action strings accept both `sound minecraft:block.note_block.pling 1 1` and `sound:minecraft:block.note_block.pling 1 1` (and equivalent supported action prefixes). `command:say Hello` and `command say Hello` are also accepted.
- Entry, exit, and stay events have separate cooldown timestamps. `oneTime` is consumed on entry, not by a stay/exit event.
- Delayed actions are saved with world data and run when their target player is online; a limit of 10,000 pending actions and 200 executions per server tick protects the server from accidental overload.
- Temporary redstone blocks are restored when their timer expires or during orderly server shutdown. An abrupt process crash can still interrupt in-memory restoration timers.
- Zone import/load validates zone names and action/condition list sizes and logs invalid saved zones instead of silently discarding them. Exported JSON has `schemaVersion: 1`; legacy files without a version remain supported.
