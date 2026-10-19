// Help for whatever is on screen (the ? button), and a five-step tour on the first visit. A step points at an
// element: the panel highlights it, scrolls it into view, and moves to the top when it would cover it. Steps
// marked whenShown are left out when their element isn't there (the More drawer while it's closed).
import { h } from "./dom";
import type { BookState } from "./store";

export interface GuideStep {
  title: string;
  body: string;
  target: string;
  whenShown?: boolean;
}

export type Screen = "board-edit" | "board-preview" | "card" | "picker" | "stages" | "chapter-settings" | "intro";

const GUIDE_KEY = "questqueen.book.guide";

const HELP: Record<Screen, { label: string; steps: GuideStep[] }> = {
  "board-edit": { label: "Editing the board", steps: [
    { title: "The board", target: ".book-board", body: "Each square is a quest. Click one to open its card. Click an empty square to add a quest there, or press N." },
    { title: "Move and link", target: ".link-handle", whenShown: true, body: "Drag a quest to move it. Drag a blue diamond on its edge onto the next quest to link them. Links go straight across or straight down, never diagonally." },
    { title: "Gates", target: ".book-board", body: "Click an arrow to choose its gate: AND needs every quest before it, OR needs any one, XOR lets the player finish only one path." },
    { title: "Chapters", target: ".book-sidebar", body: "Chapters are on the left. + adds one. ⋯ renames it, opens its settings (theme, background, unlocks, board size), duplicates or deletes it. Drag a row to reorder it; drop on the right part of a row to nest it." },
    { title: "Find anything", target: "[data-act=find]", body: "Ctrl+K finds any quest, chapter, stage or action by name." },
    { title: "Save", target: "[data-act=save]", body: "Saves every changed chapter to the world. Ctrl+S saves only this one. If something must be fixed first, the list opens instead." },
  ] },
  "board-preview": { label: "Previewing as a player", steps: [
    { title: "What players see", target: ".book-board", body: "This is the book as a player sees it: locked quests show a padlock, hidden ones aren't there, finished ones are done." },
    { title: "Who is looking", target: "[data-act=preview-as]", body: "Pick the player: someone new, someone who finished everything up to a quest, or someone who has certain stages." },
    { title: "Back to editing", target: "[data-mode=edit]", body: "Switch to Edit to change anything." },
  ] },
  card: { label: "Editing a quest", steps: [
    { title: "Title and icon", target: ".card-title", body: "Click the title or the icon to change them." },
    { title: "Description", target: ".card-desc", body: "Click the description to write it, with bold, colours, items and pictures. The preview under it is what players read." },
    { title: "Tasks", target: ".card-tasks", body: "Each line is one task. Change its kind and fill in its boxes right there. ⊕ TASK adds another." },
    { title: "Rewards", target: ".card-rewards", body: "Rewards work the same way. ⊕ REWARD adds another." },
    { title: "More, copy, delete", target: ".card-strip", body: "More holds the gates into this quest, when it unlocks, what hides it, where it jumps and the scrolls it gives. Duplicate and Delete are here too." },
    { title: "More", target: ".card-more", whenShown: true, body: "Each part saves as you change it. Ctrl+Z undoes." },
  ] },
  picker: { label: "Choosing a task or reward", steps: [
    { title: "Say what it is", target: ".picker-search", body: "Type what the player should do — kill, craft, visit, talk — or what they get." },
    { title: "Or pick one", target: ".kind-grid", body: "Every kind, in plain words, grouped by what it's about." },
    { title: "Fill it in", target: ".your-task", whenShown: true, body: "Fill in the boxes. The line under them is what players will read." },
    { title: "Add it", target: ".picker-actions", body: "Add puts it on the quest. Esc closes without changing anything." },
  ] },
  stages: { label: "Stages", steps: [
    { title: "Stages", target: ".stage-left", body: "A stage is a milestone a quest gives. Until players have it, whatever it locks stays locked. Yours can be changed here; a pack's can only be copied." },
    { title: "Make one", target: ".stage-new", body: "Name it, choose which quest gives it, then what it locks." },
    { title: "The stage", target: ".stage-detail", body: "Giving it from a quest changes that quest's chapter: the chip at the bottom saves it." },
    { title: "Back", target: ".stages-close", body: "× goes back to the quests." },
  ] },
  "chapter-settings": { label: "Chapter settings", steps: [
    { title: "Name and place", target: "[data-setting=title]", body: "The chapter's title, and which chapter it sits under." },
    { title: "Look", target: "[data-setting=theme]", body: "The theme colours the book; the background can be the theme, a colour or a picture." },
    { title: "Unlocks", target: "[data-setting=unlock_op]", body: "What opens this chapter: all of the conditions, or any one. Hide keeps it out of the list until then." },
    { title: "Board size", target: "[data-setting=grid]", body: "How many squares wide and tall. It never shrinks under a quest." },
  ] },
  intro: { label: "Act intro", steps: [
    { title: "An act", target: ".intro-title", body: "A chapter with chapters under it and no quests of its own is an act: players read this page before its chapters." },
    { title: "Picture", target: ".intro-path", whenShown: true, body: "A resource pack texture, like mypack:textures/gui/intro/act1.png." },
    { title: "Text", target: ".intro-body", body: "Click to write what players read." },
  ] },
};

/** The five-step first-visit tour. */
export const TOUR: GuideStep[] = [
  { title: "The book", target: ".book-board", body: "This is your quest book, drawn the way the game draws it. Chapters are on the left; each square is a quest." },
  { title: "Edit in place", target: "[data-mode=edit]", body: "In Edit, click any text or picture to change it." },
  { title: "Add quests and links", target: ".book-board", body: "Click an empty square to add a quest. Drag the blue diamonds to link quests." },
  { title: "Preview as a player", target: "[data-mode=preview]", body: "See exactly what a player sees, as a new player or one further along." },
  { title: "Save", target: "[data-act=save]", body: "Save to world writes your chapters into the world. ? shows help for whatever you're looking at." },
];

export function helpFor(screen: Screen): GuideStep[] {
  return HELP[screen].steps;
}

const shown = (root: ParentNode, selector: string) => {
  const el = root.querySelector<HTMLElement>(selector);
  return Boolean(el && !el.closest("[hidden]"));
};

/** What is open, topmost first: picker > card > stages page > chapter settings > intro > the board by mode. */
export function detectScreen(root: ParentNode, state: Pick<BookState, "mode">): Screen {
  if (shown(root, ".task-picker")) return "picker";
  if (shown(root, ".quest-card")) return "card";
  if (shown(root, ".stages-host")) return "stages";
  if (shown(root, ".settings-page")) return "chapter-settings";
  if (shown(root, ".intro-host")) return "intro";
  return state.mode === "preview" ? "board-preview" : "board-edit";
}

function clearTargets() {
  document.querySelectorAll(".guide-target").forEach((el) => el.classList.remove("guide-target"));
}

/** Show steps in a panel in `host`; `onClose` runs on Done or ×. */
export function openGuide(host: HTMLElement, steps: GuideStep[], label = "", onClose?: () => void): void {
  host.querySelector(".guide")?.remove();
  if (!steps.length) return;
  let at = 0;
  const title = h("h2", {});
  const body = h("p", {});
  const count = h("div", { class: "guide-count" });
  const back = h("button", { type: "button", class: "guide-back" }, "Back") as HTMLButtonElement;
  const next = h("button", { type: "button", class: "guide-next primary" }) as HTMLButtonElement;
  const panel = h("aside", { class: "guide", role: "dialog", "aria-label": label || "Help" },
    h("button", { type: "button", class: "guide-close card-close", "aria-label": "Close help" }, "×"),
    count, title, body, h("div", { class: "guide-actions" }, back, next));
  const close = () => {
    clearTargets();
    panel.remove();
    onClose?.();
  };
  const paint = () => {
    const step = steps[at];
    clearTargets();
    title.textContent = step.title;
    body.textContent = step.body;
    count.textContent = `${label ? `${label} · ` : ""}Step ${at + 1} of ${steps.length}`;
    back.hidden = at === 0;
    next.textContent = at === steps.length - 1 ? "Done" : "Next";
    panel.classList.remove("guide-top");
    const target = document.querySelector<HTMLElement>(step.target);
    if (!target || target.closest("[hidden]")) return;
    target.classList.add("guide-target");
    // Centre a small target (a sticky footer would cover one scrolled only to the edge); a board-sized one stays.
    if (target.getBoundingClientRect().height < window.innerHeight / 2) {
      target.scrollIntoView?.({ block: "center" });
      const t = target.getBoundingClientRect();
      const g = panel.getBoundingClientRect();
      if (t.bottom > g.top && t.top < g.bottom && t.right > g.left && t.left < g.right) panel.classList.add("guide-top");
    }
  };
  next.addEventListener("click", () => {
    if (at >= steps.length - 1) return close();
    at++;
    paint();
  });
  back.addEventListener("click", () => {
    if (at === 0) return;
    at--;
    paint();
  });
  panel.querySelector(".guide-close")!.addEventListener("click", close);
  panel.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      event.stopPropagation();
      close();
    }
  });
  host.append(panel);
  paint();
  next.focus();
}

/** The ? button: help for what's on screen, without steps whose element isn't there. */
export function openHelp(host: HTMLElement, store: { get(): Pick<BookState, "mode"> }): void {
  const screen = detectScreen(document, store.get());
  const { label, steps } = HELP[screen];
  openGuide(host, steps.filter((step) => !step.whenShown || shown(document, step.target)), label);
}

/** The tour, once per browser. Resolves whether it opened. Storage that throws counts as seen. */
export function maybeTour(host: HTMLElement, storage: Pick<Storage, "getItem" | "setItem"> = localStorage): boolean {
  try {
    if (storage.getItem(GUIDE_KEY)) return false;
  } catch {
    return false;
  }
  openGuide(host, TOUR, "Welcome", () => {
    try {
      storage.setItem(GUIDE_KEY, "seen");
    } catch {
      /* private window: the tour may show again */
    }
  });
  return true;
}
