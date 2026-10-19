// Display names for ids, from the pack catalog the game sent ("minecraft:chicken" → "Chicken").
import type { CatalogEntry } from "../packCatalog";
import type { BookState } from "./store";
import type { Namer } from "./sentences";

const LIST: Record<string, keyof BookState["catalog"]> = {
  item: "items", entity: "entities", entityTag: "tags", tag: "tags", block: "blocks", biome: "biomes",
  structure: "structures", dimension: "dimensions", fluid: "fluids", advancement: "advancements", loot: "loot",
};

/** "minecraft:oak_log" → "Oak Log": what the editor says when the pack gives no name. */
export function prettyId(id: string): string {
  const path = id.slice(id.indexOf(":") + 1).split("/").pop() ?? id;
  return path.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function namer(state: BookState): Namer {
  return (kind, raw) => {
    const id = raw.split("[")[0];
    if (kind === "stage") return state.stages.stages.find((s) => s.id === id)?.name || prettyId(id);
    const list = LIST[kind] ? (state.catalog[LIST[kind]] as CatalogEntry[] | undefined) : undefined;
    const full = id.includes(":") ? id : `minecraft:${id}`;
    const name = list?.find((entry) => entry.id === full)?.name;
    return name && name !== full ? name : prettyId(full);
  };
}
