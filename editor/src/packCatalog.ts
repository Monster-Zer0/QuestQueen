export interface CatalogEntry {
  id: string;
  name?: string;
}

export interface PackCatalog {
  items: CatalogEntry[];
  entities: CatalogEntry[];
  blocks: CatalogEntry[];
  tags: CatalogEntry[];
  advancements: CatalogEntry[];
  biomes: CatalogEntry[];
  structures: CatalogEntry[];
  loot: CatalogEntry[];
  fluids?: CatalogEntry[];
  dimensions?: CatalogEntry[];
  stats?: CatalogEntry[];
  /** Stages the pack defines (StageLock), with display names. */
  stages?: CatalogEntry[];
}

export function emptyCatalog(): PackCatalog {
  return {
    items: [],
    entities: [],
    blocks: [],
    tags: [],
    advancements: [],
    biomes: [],
    structures: [],
    loot: [],
    fluids: [],
    dimensions: [],
    stats: [],
    stages: [],
  };
}

export function fromApi(raw: Record<string, unknown>): PackCatalog {
  return {
    items: asEntries(raw.items),
    entities: asEntries(raw.entities),
    blocks: asEntries(raw.blocks),
    tags: asEntries(raw.tags),
    advancements: asEntries(raw.advancements),
    biomes: asEntries(raw.biomes),
    structures: asEntries(raw.structures),
    loot: asEntries(raw.loot),
    fluids: asEntries(raw.fluids),
    dimensions: asEntries(raw.dimensions),
    stats: asEntries(raw.stats),
    stages: asEntries(raw.stages),
  };
}

function asEntries(raw: unknown): CatalogEntry[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((item) => {
    if (typeof item === "string") {
      return { id: item, name: item };
    }
    const record = item as { id?: string; name?: string };
    return { id: record.id ?? "", name: record.name ?? record.id ?? "" };
  }).filter((entry) => entry.id);
}
