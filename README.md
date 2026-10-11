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
| `\*` | a literal `*` (works for any of the markup characters) |

Pictures are ordinary textures: put the PNG in the pack's resource pack (or a mod) and give its full resource location, which starts with `textures/` and ends in `.png`. A picture that cannot be found is drawn as a labelled frame, and the log says which texture it was. Anything that is not valid markup, such as an unclosed tag, is shown as the characters you typed. Descriptions written before this keep working unchanged.

The web editor has buttons for all of this above the description box, a live preview, and flags a picture path or item id that would show up as plain text.

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
