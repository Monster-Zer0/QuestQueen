# Quest Queen

A NeoForge 1.21.1 quest book: pixel-grid chapters from datapack JSON, server-side SQLite team progress, and a browser quest editor.

## Run

```bat
gradlew runClient
```

Open the book with **J**. Sample line: obtain a chest, then kill a zombie.

## Editor

In-game: `/questqueen editor` starts the browser UI at `http://127.0.0.1:47821`. The quest book itself is play-only.

Offline:

```bat
cd editor
npm install
npm run dev
```

Load or export a folder/zip of `data/*/questqueen/**/*.json`. There is no hosted pack updater.

## Layout

- `src/main/java/dev/aof/questqueen/` - mod
- `src/main/resources/data/questqueen/questqueen/` - sample chapter and scrolls
- `schema/questqueen.schema.json` - shared schema
- `editor/` - Vite authoring UI

## License

[MIT](LICENSE). You can use, change, and share this project. Keep the copyright notice with copies.
