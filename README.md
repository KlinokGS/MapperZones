![MapperZones](https://iili.io/nVlhlAG.png)

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mapper-zones) - [Modrinth](https://modrinth.com/mod/mapper-zones)

# EN: MapperZones — Trigger zones for Minecraft maps

MapperZones is a map-making tool for creating trigger zones and configuring events that react to player movement and conditions. It includes a GUI editor, server-side variables, condition expressions, and visual zone boundaries.

## Commands

- `/mapperzones` — open the main GUI.
- `/mapperzones gui` — open the main GUI.
- `/mz` — short alias for `/mapperzones` when enabled in settings.
- `/mapperzones reset` — reset zone event state.
- Use the in-game GUI to create and edit zones, configure their conditions, and manage variables.

> The `/mz` alias can be disabled in the in-game settings if it conflicts with another mod or server command.

## Conditions and variables

Conditions are written as expressions, one per line. Examples:

```text
player.on_ground
exists(player.quest)
action.exit: time >= 12000
```

Variable comparisons can use expressions such as:

```text
global.score >= 5 && player.quest == "started"
```

- `global.<name>` — a shared server-side variable.
- `player.<name>` — a variable associated with an individual player.
- `exists(global.score)` — checks whether a variable exists.
- `action.enter:` and `action.exit:` — apply a condition to the corresponding zone event.
- Conditions can be combined using logical operators such as `&&` and `||`.

Use the in-game condition help screen for the syntax and examples supported by your installed version.

## In-game settings

The settings screen lets you configure:

- Zone-boundary render distance: 4, 8, 12, or 16 chunks (default: 8).
- Whether the `/mz` command alias is enabled (default: on).
- Whether zone names are shown above their boundaries (default: on).

Settings are stored in:

```text
config/mapperzones/settings.json
```

## What's included

- In-game GUI for managing trigger zones.
- Visual zone boundaries and zone names.
- Configurable render distance for zone highlighting.
- Zone events, including entering and exiting zones, and stay-time checks.
- `oneTime` behavior for one-time event execution.
- Condition expressions and variable-existence checks.
- Server-side `global` and per-player `player` variables.
- Variable management through the GUI.
- Localized interface and condition help.
- Optional `/mz` command alias.
- In-game settings screen.

---

# RU: MapperZones — Триггерные зоны для карт Minecraft

MapperZones — инструмент для создания триггерных зон и настройки событий, реагирующих на перемещение игрока и выполнение условий. Мод включает GUI-редактор, серверные переменные, выражения условий и визуальное отображение границ зон.

## Команды

- `/mapperzones` — открыть главное GUI.
- `/mapperzones gui` — открыть главное GUI.
- `/mz` — короткий псевдоним `/mapperzones`, если он включён в настройках.
- `/mapperzones reset` — сбросить состояние событий зон.
- Создавайте и редактируйте зоны, настраивайте условия и управляйте переменными через внутриигровой интерфейс.

> Псевдоним `/mz` можно отключить в настройках мода, если он конфликтует с другой командой или модом на сервере.

## Условия и переменные

Условия записываются выражениями, по одному на строку. Примеры:

```text
player.on_ground
exists(player.quest)
action.exit: time >= 12000
```

Сравнения переменных могут выглядеть так:

```text
global.score >= 5 && player.quest == "started"
```

- `global.<имя>` — общая серверная переменная.
- `player.<имя>` — переменная, относящаяся к конкретному игроку.
- `exists(global.score)` — проверяет, существует ли переменная.
- `action.enter:` и `action.exit:` — задают условие для соответствующего события входа в зону или выхода из неё.
- Условия можно объединять логическими операторами, например `&&` и `||`.

Используйте внутриигровую справку по условиям, чтобы посмотреть синтаксис и примеры, поддерживаемые установленной версией мода.

## Настройки в игре

В меню настроек доступны следующие параметры:

- Дальность отображения границ зон: 4, 8, 12 или 16 чанков (по умолчанию — 8).
- Включение короткой команды `/mz` (по умолчанию — включена).
- Отображение названий зон над границами (по умолчанию — включено).

Настройки хранятся в файле:

```text
config/mapperzones/settings.json
```

## Что уже реализовано

- Внутриигровой GUI для управления триггерными зонами.
- Визуальные границы зон и их названия.
- Настраиваемая дальность отображения границ.
- События зон, включая вход, выход и проверку времени пребывания.
- Режим `oneTime` для однократного выполнения событий.
- Выражения условий и проверка существования переменных.
- Серверные переменные `global` и персональные переменные `player`.
- Управление переменными через GUI.
- Локализация интерфейса и справка по условиям.
- Необязательный псевдоним команды `/mz`.
- Внутриигровое меню настроек.
