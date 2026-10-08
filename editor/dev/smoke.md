# Editor smoke checks

Fast checks for the browser editor without Minecraft. The logic behind each one is unit tested in
`src/editor.test.ts` (`npm test`); these confirm the wiring in the page.

## Start

```bash
npm run mock    # fake game bridge on 127.0.0.1:47821 (showcase, dev chapters, fixtures/editor_probe.json)
npm run dev     # editor on http://localhost:5173
```

`GET http://127.0.0.1:47821/__saves` lists every save the page sent; `POST /__reset` clears it and reloads the chapters.

## Checks

1. **Undo stays in its chapter:** delete a quest, switch chapter, press Ctrl+Z. Nothing changes there; back in the first chapter, Ctrl+Z restores it.
2. **Rule changes keep conditions:** in Editor Probe, LINK mode, change Left → Gated to OR and save. `__saves` still has its advancement condition.
3. **Fork:** XOR on one arrow out of a quest sets every arrow out of that quest. The other parents of the child keep their rule.
4. **Chapter ids:** the id field is read-only for chapters that came from the game. A new chapter can take an id, but not one already used.
5. **Parent list:** the parent list never offers the chapter itself or its descendants.
6. **New chapter, Blank, Starter:** each one adds a chapter and leaves the others open.
7. **New quest:** a quest from an empty cell has a checkmark task.
8. **Grid:** asking for a grid smaller than the quests on it is refused with the minimum size.
9. **Choice reward:** every option shows with its count; + OPTION and REMOVE OPTION work.
10. **Search:** finds quests in other chapters by title or id, and says when nothing matches.
11. **Save status:** saving shows "Saved · id", and a problem count shows while editing. An edited chapter gets a dot in the list.

The game side is `e2e/flow.mjs`. Run `./gradlew runClient -Pqq.e2e` with Marionette in `run/client/mods`, then `node e2e/flow.mjs`.
