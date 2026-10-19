// An act intro: a chapter that holds other chapters and no quests. The book shows its intro page (a picture and
// text) before them; here that page is drawn in the board's place and edited in place, like a quest card.
import { bridgeBase } from "../bridge";
import { iconUrl } from "../itemSelect";
import { richToHtml } from "../richtext";
import type { Chapter } from "../types";
import { openDescribe } from "./describe";
import { h } from "./dom";
import { inlineText } from "./inline";
import type { Store } from "./store";

const pictureUrl = (texture: string) => `${bridgeBase()}/api/texture?id=${encodeURIComponent(texture)}`;
const preview = { iconUrl: (id: string) => iconUrl(bridgeBase(), id), pictureUrl };

export function renderIntro(host: HTMLElement, store: Store, chapter: Chapter): void {
  if (host.querySelector("[data-editing]")) return;
  const s = store.get();
  const editing = s.mode === "edit";
  const chapterId = chapter.id;
  const edit = (why: string, fn: (c: Chapter) => void) => store.edit(chapterId, why, fn);
  const title = h("h1", { class: "intro-title qq-text" });
  if (editing) inlineText(title, { value: chapter.title ?? "", label: "Act title", onCommit: (v) => edit("chapter title", (c) => { c.title = v; }) });
  else title.textContent = chapter.title || chapter.id;
  const image = chapter.intro?.image ?? "";
  const picture = image ? h("img", { class: "intro-picture", alt: "", src: pictureUrl(image) }) : h("div", { class: "intro-picture empty" }, editing ? "No picture yet" : "");
  const path = editing ? h("input", { class: "intro-path", value: image, placeholder: "mypack:textures/gui/intro/act1.png", "aria-label": "Intro picture (resource pack texture)" }) as HTMLInputElement : null;
  path?.addEventListener("change", () => edit("intro picture", (c) => { c.intro = { ...(c.intro ?? {}), image: path.value.trim() || undefined }; }));
  const body = h("div", { class: `intro-body qq-text${editing ? " editable" : ""}`, tabindex: editing ? "0" : undefined, role: editing ? "button" : undefined, "aria-label": editing ? "Edit the intro text" : undefined });
  body.innerHTML = chapter.intro?.body ? richToHtml(chapter.intro.body, preview) : editing ? '<span class="card-empty">Write the intro players read before these chapters…</span>' : "";
  if (editing) {
    const open = () => {
      if (body.dataset.editing) return;
      openDescribe(body, { value: chapter.intro?.body ?? "", preview, onCommit: (v) => edit("intro text", (c) => { c.intro = { ...(c.intro ?? {}), body: v }; }) });
    };
    body.addEventListener("click", open);
    body.addEventListener("keydown", (event) => { if (event.key === "Enter") { event.preventDefault(); open(); } });
  }
  const kids = s.chapters.filter((c) => c.parent === chapterId).map((c) => h("li", {}, c.title || c.id));
  host.replaceChildren(h("article", { class: "intro-page" }, h("div", { class: "intro-picture-wrap" }, picture, path), title, body,
    kids.length ? h("div", { class: "intro-kids" }, h("div", { class: "card-caption qq-text" }, "IN THIS ACT"), h("ul", {}, ...kids)) : null));
}
