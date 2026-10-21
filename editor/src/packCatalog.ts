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
  /** Mob tags, for "any mob in a tag". */
  entityTags?: CatalogEntry[];
  /** Scoreboard objectives, by name, for scoreboard gates. */
  objectives?: CatalogEntry[];
  /** The pack's scrolls (id and title), for "Scrolls it gives". */
  scrolls?: CatalogEntry[];
  /** Every texture PNG in the loaded resource packs, for picture boxes. */
  textures?: CatalogEntry[];
  /** Stages the pack defines (StageLock), with display names. */
  stages?: CatalogEntry[];
  /** Installed mods, for "lock a whole mod". */
  mods?: CatalogEntry[];
  /** True when StageLock is the stage mod, so stages can lock things. */
  stageLock?: boolean;
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
    entityTags: [],
    objectives: [],
    scrolls: [],
    textures: [],
    stages: [],
    mods: [],
    stageLock: false,
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
    entityTags: asEntries(raw.entityTags),
    objectives: asEntries(raw.objectives),
    scrolls: asEntries(raw.scrolls),
    textures: asEntries(raw.textures),
    stages: asEntries(raw.stages),
    mods: asEntries(raw.mods),
    stageLock: raw.stageLock === true,
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
