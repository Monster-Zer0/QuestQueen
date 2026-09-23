/**
 * Chapter theme presets — keep in sync with
 * `dev.aof.questqueen.client.BookTheme` (Java is source of truth).
 */
export interface ThemeSwatch {
  id: string;
  name: string;
  void: string;
  cell: string;
  card: string;
  text: string;
  locked: string;
  current: string;
  neu: string;
  edit: string;
  completed: string;
  failed: string;
  lockedEdge: string;
}

export const DEFAULT_THEME_ID = "midnight_royalty";

export const BOOK_THEMES: ThemeSwatch[] = [
  {
    id: "midnight_royalty",
    name: "Midnight Royalty",
    void: "#24142C",
    cell: "#18101F",
    card: "#16121C",
    text: "#F5F0E0",
    locked: "#2B203A",
    current: "#3DFF6E",
    neu: "#488BD4",
    edit: "#FFB84A",
    completed: "#FFF540",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "ocean_depths",
    name: "Ocean Depths",
    void: "#0A1E28",
    cell: "#08161C",
    card: "#0C1418",
    text: "#E8F4F8",
    locked: "#142830",
    current: "#3DFFC8",
    neu: "#3AA8E8",
    edit: "#FFC060",
    completed: "#E8F0FF",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "ember_forge",
    name: "Ember Forge",
    void: "#1A100C",
    cell: "#120C08",
    card: "#100C08",
    text: "#FFF0E0",
    locked: "#241810",
    current: "#FF7A3A",
    neu: "#E86828",
    edit: "#FFB040",
    completed: "#FFD080",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "verdant_grove",
    name: "Verdant Grove",
    void: "#0E1A12",
    cell: "#0A140E",
    card: "#0C140E",
    text: "#F0F8E8",
    locked: "#182820",
    current: "#5CFF7A",
    neu: "#4AC878",
    edit: "#FFC050",
    completed: "#FFE060",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "desert_oasis",
    name: "Desert Oasis",
    void: "#2A2218",
    cell: "#1E1810",
    card: "#18140C",
    text: "#FFF8E8",
    locked: "#2A2218",
    current: "#40E8C0",
    neu: "#38B8D0",
    edit: "#FFB050",
    completed: "#FFE8A0",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "frost_peak",
    name: "Frost Peak",
    void: "#121820",
    cell: "#0C1218",
    card: "#0C1014",
    text: "#F0F8FF",
    locked: "#182028",
    current: "#80E8FF",
    neu: "#68A8E8",
    edit: "#C0E0FF",
    completed: "#F0F8FF",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "nether_scar",
    name: "Nether Scar",
    void: "#100808",
    cell: "#0C0404",
    card: "#0C0404",
    text: "#FFF0E8",
    locked: "#1C0C0C",
    current: "#FF6020",
    neu: "#E84818",
    edit: "#FF9040",
    completed: "#FFC060",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "end_chorus",
    name: "End Chorus",
    void: "#1A1028",
    cell: "#120C1C",
    card: "#100C18",
    text: "#F8F0FF",
    locked: "#221830",
    current: "#E060FF",
    neu: "#68C0FF",
    edit: "#FF90D0",
    completed: "#F0C0FF",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "copper_circuit",
    name: "Copper Circuit",
    void: "#121A18",
    cell: "#0C1412",
    card: "#0C1410",
    text: "#E8F8F0",
    locked: "#182820",
    current: "#50E8A8",
    neu: "#40B8A0",
    edit: "#E8A060",
    completed: "#D0E8C0",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
  {
    id: "paper_scroll",
    name: "Paper Scroll",
    void: "#2A2418",
    cell: "#221C14",
    card: "#1C1810",
    text: "#F8F0DC",
    locked: "#2C2418",
    current: "#C07030",
    neu: "#6880A0",
    edit: "#B85828",
    completed: "#D8B060",
    failed: "#E01018",
    lockedEdge: "#8C1A24",
  },
];

export const STOCK_BACKGROUNDS = [
  { id: "questqueen:textures/gui/bg/midnight.png", label: "Midnight haze" },
  { id: "questqueen:textures/gui/bg/ocean.png", label: "Ocean grain" },
  { id: "questqueen:textures/gui/bg/nether.png", label: "Nether ash" },
  { id: "questqueen:textures/gui/bg/frost.png", label: "Frost mist" },
] as const;

export function themeById(id: string | undefined): ThemeSwatch {
  return BOOK_THEMES.find((theme) => theme.id === id) ?? BOOK_THEMES[0];
}

export function themeOptionHtml(selectedId: string | undefined, disabled: boolean): string {
  const current = selectedId || DEFAULT_THEME_ID;
  const opts = BOOK_THEMES.map((theme) => {
    const selected = theme.id === current ? "selected" : "";
    return `<option value="${theme.id}" ${selected}>${theme.name}</option>`;
  }).join("");
  return `<select id="chapter-theme" ${disabled ? "disabled" : ""}>${opts}</select>`;
}
