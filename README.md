# Quest Queen

A NeoForge 1.21.1 quest book: pixel-grid chapters from datapack JSON, server-side SQLite team progress, and a browser quest editor.

## Run

```bat
gradlew runClient
```

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

When the pack has its own chapters, the highest-priority `book.json` outside this mod's jar sets the sidebar title. Clicking a task item opens it in EMI, then REI, then JEI, whichever of those is installed.

## Layout

- `src/main/java/dev/aof/questqueen/` - mod
- `src/main/resources/data/questqueen/questqueen/` - Feature Showcase shipped in the jar
- `src/dev/resources/data/questqueen/questqueen/` - harness chapters, dev runs only
- `schema/questqueen.schema.json` - shared schema
- `editor/` - Vite authoring UI

## License

[MIT](LICENSE). You can use, change, and share this project. Keep the copyright notice with copies.
