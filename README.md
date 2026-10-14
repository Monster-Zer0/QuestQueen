# Quest Queen

A NeoForge 1.21.1 quest book: pixel-grid chapters from datapack JSON, server-side SQLite progress shared through FTB Teams parties when installed, and a browser quest editor.

## Run

```bat
gradlew runClient
```

If `runClient` exits at once with `NoSuchElementException: No value present` in `BootstrapLauncher`, the run classpath file lists jars under a Gradle home that is gone. The build now rewrites that file whenever the Gradle home changes; on an older checkout, run `gradlew writeMinecraftClasspathClient --rerun`.

Machine-specific test fixtures are optional Gradle properties: `-Pqq.devInstance=<instance dir>` (JEI API drift check) and `-Pskylore.chapters=<chapters dir>` (caption corpus). Tests that need them skip when they are absent.

Open the book with **J**. JourneyMap also defaults to J, so rebind one of them in a pack that has both. With no pack chapters loaded, the book opens on the built-in Feature Showcase.

## Editor

In-game: `/questqueen editor` starts the browser UI at `http://127.0.0.1:47821`. The quest book itself is play-only.

Offline:

```bat
cd editor
npm install
npm run dev
```

Load or export a folder/zip of `data/*/questqueen/**/*.json`. There is no hosted pack updater.

## Using this in a modpack

Quest Queen does not ship a pack's quests. Put chapters in any datapack or mod resources:

```
data/<pack>/questqueen/chapters/<name>.json
data/<pack>/questqueen/scrolls/<name>.json
data/<pack>/questqueen/book.json
```

Each file's `"id"` should use that pack's namespace (`"<pack>:<name>"`). These ids are reserved for the built-in book and are hidden once a pack chapter exists: `questqueen:showcase`, `questqueen:showcase_stage`, `questqueen:blank`, `questqueen:starter`, `questqueen:nether`, `questqueen:test`, `questqueen:test_bonus`, and the scrolls `questqueen:showcase_brief`, `questqueen:chest_note`, `questqueen:zombie_note`, `questqueen:test_note`.

`demoChapters` in the common config is `auto` (hide the Feature Showcase when the pack has chapters), `always`, or `never`. `includeDevChapters` loads the harness chapters; those files are only on the dev run classpath, not in the published jar.

The pinned quest shows on the HUD at `[pinnedQuest] position` (`top_left` by default; also `top_center`, `top_right`, `middle_left`, `middle_right`, `bottom_left`, `bottom_right`), nudged by `offsetX` / `offsetY`. With the cursor free (chat, the inventory or any other screen), hovering it shows the quest's description, tasks and rewards. The survival inventory has a quest book button in its top-right corner; `[inventoryButton]` turns it off (`enabled`) or moves it (`offsetX`, `offsetY`) to clear another mod's button.

An advancement task's row says what the advancement is when hovered: its title and description, or for a tracking advancement with no display, what it asks for ("Have any Logs", "Place Hospital Bed", "Go to Crashed Main Ship").

A quest that is done but not yet claimed gets a gold wash over its tile and a gold frame, on every zoom level. `[claimChrome]` in the common config changes them: `claimTint` (default `33FFD54F`) and `claimEdge` (default `FFFFD54F`), as hex ARGB. Leave them empty to use the chapter theme's colours.

When the pack has its own chapters, the highest-priority `book.json` outside this mod's jar sets the sidebar title. Clicking a task item opens it in EMI, then REI, then JEI, whichever of those is installed.

## Descriptions

A quest's `"description"` is plain text, or text with markup. It scrolls when it is longer than the card, and the card's expand button opens it in a larger reader window.

| Write | You get |
|---|---|
| `# Heading`, `## Smaller heading` | headings |
| `**bold**`, `*italic*`, `__underline__` | emphasis |
| `{#RRGGBB}text{/#}`, `{size:12}text{/size}` | colour, size (8 to 24) |
| `- item` | a bullet |
| `---` | a divider line |
| `{item:minecraft:diamond}`, `{item:minecraft:iron_ingot x3}` | an item icon in the text; hover it for the name |
| `{glyph:star}` | one of the built-in glyphs |
| `![caption](mypack:textures/quest/castle.png)` | a picture, alone on its line |
| `{recipe:minecraft:furnace}` | the recipe that makes the item, alone on its line |
| `{recipe:id=minecraft:iron_ingot_from_smelting_iron_ore}` | one recipe by its id, alone on its line |
| `\*` | a literal `*` (works for any of the markup characters) |

Recipes come from the game's own recipe list, so recipes added or changed by datapacks or KubeJS show after `/reload`. Crafting, smelting (and the other furnaces), stonecutting and smithing are drawn with their own slot layouts; any other (modded) recipe is drawn as its inputs, an arrow and the output. An ingredient that accepts several items, like any log, cycles through them. A recipe that does not exist draws a "missing recipe" frame. `{recipe:<item>}` picks the item's crafting recipe first, then a furnace recipe, then any other.

Item icons in descriptions, including the slots of a recipe, open the item in the installed recipe viewer (EMI, REI or JEI): left-click for its recipes, right-click for its uses. With no recipe viewer they only show their name on hover.

Pictures are ordinary textures: put the PNG in the pack's resource pack (or a mod) and give its full resource location, which starts with `textures/` and ends in `.png`. A picture that cannot be found is drawn as a labelled frame, and the log says which texture it was. Anything that is not valid markup, such as an unclosed tag, is shown as the characters you typed. Descriptions written before this keep working unchanged.

The web editor has buttons for all of this above the description box, a live preview, and flags a picture path or item id that would show up as plain text.

## Keyboard

The quest book can be driven entirely from the keyboard. Every key below is rebindable under **Controls > Key Binds > Quest Queen**, and the keyboard icon in the book's top bar lists the current bindings on hover (click it, or press F1, for a larger list). `[keyboard] showKeyHelpIcon` hides the icon.

| Key | Does |
|---|---|
| Arrow keys | move to the nearest quest in that direction (the camera follows) |
| Enter | open the selected quest's card; again to move focus into the card; on a focused part, click it |
| Esc | back out one step: card focus, then the card, then the book |
| C / Shift+C | claim or submit / claim all |
| P | pin or unpin |
| E | read the description in the large reader |
| R / U | recipes / uses of the focused item, in EMI, REI or JEI |
| Page Up / Page Down | previous / next chapter (inside a card: scroll its description) |
| = / − / F | zoom in / out / fit the chapter |
| Shift+arrows | pan the board |
| / | search |
| L | quest log |
| F1 | the key list |

Inside a card the arrows move between its parts: item icons and recipe slots in the description, task rows, what the quest unlocks, rewards, and the buttons. The focused part shows its tooltip.

While the book is open, Quest Queen handles its own keys before any other mod, so a recipe viewer's R/U or an inventory mod's keys never act on them; keys the book does not use pass through as usual. Everything typed into the book's search box stays there too.

## Task display items

Tasks that have no item of their own (advancement, stat, kill, trigger, checkmark, interact, visit, location, observation, raid, xp levels, NPC dialog) can name one with `"icon"`:

```json
{ "type": "advancement", "advancement": "minecraft:story/mine_stone", "icon": "minecraft:stone_pickaxe" }
```

The task row shows that item, hovering it shows its name, and clicking it opens its recipe in the installed recipe viewer. `icon` is an item id, optionally with `[components]` as in `/give`. The web editor has a DISPLAY ITEM picker on those task types.

## Command rewards

A `command` reward runs its command when the quest is claimed. Players never see the command itself. Three optional fields set what the book shows instead:

```json
{
  "type": "command",
  "command": "give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:\"tacz:glock_17\"}] 1",
  "icon": "tacz:modern_kinetic_gun[minecraft:custom_data={GunId:\"tacz:glock_17\"}]",
  "name": "Academy Sidearm",
  "description": "A Glock 17, loaded.\nSave it for things that shoot back."
}
```

- `icon` is an item written as in `/give` (`minecraft:diamond`, or `id[components]` so an item that draws itself from its data shows its real model), `glyph:<id>` for a built-in glyph, or a resource-pack picture (`<namespace>:textures/….png`).
- `name` is the hover title, and the line in the claim-all list and the log.
- `description` adds lines under the name on hover; `\n` starts a new line.

Any field left out falls back on its own. A `give` command (`give @s <item>[components] [count]`) shows the item it gives: its icon, its own tooltip and the count. Any other command shows a star and "Bonus reward". The web editor has the three fields under each command reward, a **Use item from command** button, and a preview of the hover.

## Stages

Quest Queen works with a stage mod when one is installed: StageLock (mod id `stagelock`) first, otherwise ProgressiveStages. Neither is required. Stages are used by:

- `"required_stage": "<id>"` on a quest: the quest stays closed until the player has the stage;
- `{"type": "stage", "id": "<id>"}` conditions in a link's `gate.conditions`, a chapter's `unlock`, or a quest's `hidden_until`;
- the `{"type": "stage", "stage": "<id>"}` reward, which grants the stage to the player.

`"stagelock"` and `"progressivestages"` work as the type name too, as does `"progression"` (StageLock's old name). A stage granted any other way (a command, an advancement, KubeJS, another mod) reaches the quest book without a relog.

With StageLock, stage ids are exact: lowercase, 1–64 characters of `a-z 0-9 _ . : / -`, with no implied namespace. A StageLock stage file at `data/mypack/stagelock/stages/iron_age.json` with no `"id"` defines `mypack:iron_age`, not `iron_age`. Quest Queen logs a `quest authoring problem` at load for stage ids that are invalid or that the pack's StageLock stage files do not define. The web editor offers the defined stages by name.

ProgressiveStages only grants stages its own stage files define.

## Layout

- `src/main/java/dev/aof/questqueen/` - mod
- `src/main/resources/data/questqueen/questqueen/` - Feature Showcase shipped in the jar
- `src/dev/resources/data/questqueen/questqueen/` - harness chapters, dev runs only
- `schema/questqueen.schema.json` - shared schema
- `editor/` - Vite authoring UI

## License

[MIT](LICENSE). You can use, change, and share this project. Keep the copyright notice with copies.
