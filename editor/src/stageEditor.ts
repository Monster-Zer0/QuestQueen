// The stage editor: one component, shown in the Stages tab and as a pop-up from a quest's stage picker.
// Three plain sections — what it is, how players get it, what it locks until then — and a live sentence that
// says the whole thing back to the author. It edits a draft and hands it to ctx.onSave; main.ts talks to the game.
import type { Chapter } from "./types";
import type { CatalogEntry, PackCatalog } from "./packCatalog";
import { itemSelectHtml, readItemSelect, wireItemSelects } from "./itemSelect";
import { stageSelectHtml, wireStageSelects } from "./stageSelect";
import {
  LOCK_KINDS,
  cleanStage,
  flagOn,
  kindSpec,
  requiresCycle,
  stageProblems,
  stageUsage,
  summarySentence,
  uniqueStageId,
  type AuthoredStage,
  type LockKind,
  type StageInfo,
  type StageLockGroup,
  type StageUse,
} from "./stages";

const MAX_HITS = 60;

export interface StageEditorContext {
  catalog: () => PackCatalog;
  stages: () => StageInfo[];
  chapters: () => Chapter[];
  bridgeBase: () => string;
  iconUrl: (item: string) => string;
  /** Resolves to an error message, or null once the game has the stage. */
  onSave: (stage: AuthoredStage, progress: (text: string) => void) => Promise<string | null>;
  onDelete: (id: string) => Promise<string | null>;
  /** Puts a stage reward on a quest (the chapter's own Save writes it). */
  onGrantFromQuest: (stageId: string, chapterId: string, tileId: string) => void;
  onJump: (chapterId: string, tileId?: string) => void;
  /** After a save or delete: the id that is now selected, or "" when the stage is gone. */
  onDone: (id: string) => void;
  onCancel?: () => void;
  /** Open another stage (a prerequisite chip). */
  onOpen?: (id: string) => void;
}

export interface StageEditorOptions {
  /** A name typed into a picker before "New stage…". */
  prefillName?: string;
  /** Shown in a pop-up: adds Cancel and hides the jump-to-quest links. */
  modal?: boolean;
  /** A new stage that starts as a copy of this one (Duplicate). */
  copyOf?: AuthoredStage;
  /** A line to show by the buttons at first, e.g. "Saved". */
  notice?: string;
}

/** Render the editor for {@code info} (null = a new stage) into {@code host}. */
export function renderStageEditor(host: HTMLElement, info: StageInfo | null, ctx: StageEditorContext, options: StageEditorOptions = {}): void {
  const isNew = info === null;
  const readOnly = info !== null && !info.authored;
  const draft: AuthoredStage = info?.stage
    ? structuredClone(info.stage)
    : options.copyOf
      ? { ...structuredClone(options.copyOf), name: options.prefillName ?? options.copyOf.name }
      : { id: info?.id ?? "", name: info?.name ?? options.prefillName ?? "", locks: [] };
  if (isNew) draft.id = uniqueStageId(draft.name ?? "", ctx.stages().map((s) => s.id));
  let idTouched = false;
  let addingLock = false;
  let pickingQuest = false;
  let busy = false;
  let status = options.notice ?? "";
  let statusBad = false;
  const catalog = ctx.catalog();
  const stageLockOn = catalog.stageLock !== false;

  const names = (kind: LockKind, id: string): string => nameOf(catalog, kind, id);
  const usage = (): { grants: StageUse[]; needs: StageUse[] } => stageUsage(ctx.chapters(), draft.id);

  const paint = () => {
    const { grants, needs } = usage();
    const ro = readOnly || busy ? "readonly" : "";
    const dis = readOnly || busy ? "disabled" : "";
    const title = draft.name?.trim() || (isNew ? "New stage" : draft.id);
    host.innerHTML = `
      <div class="stage-editor ${readOnly ? "read-only" : ""}">
        <header class="stage-editor-head">
          <img class="stage-editor-icon" alt="" width="32" height="32" src="${esc(ctx.iconUrl(draft.icon || "minecraft:amethyst_shard"))}" />
          <div>
            <h2>${esc(title)}</h2>
            <small>${isNew ? "Not saved yet" : esc(draft.id)} · <span class="stage-badge ${readOnly ? "pack" : "yours"}">${readOnly ? "from a datapack" : "made here"}</span></small>
          </div>
        </header>
        ${readOnly ? `<p class="stage-note">This stage is defined by a datapack, so it can't be changed here.
          <button type="button" class="btn btn-ghost btn-sm" data-act="duplicate">Duplicate to edit</button></p>` : ""}

        <section class="stage-step">
          <h3><span class="stage-step-n">1</span> What is it?</h3>
          <label for="stage-name">NAME</label>
          <input id="stage-name" value="${esc(draft.name ?? "")}" placeholder="Iron Age" ${ro} autocomplete="off" />
          <label for="stage-desc">DESCRIPTION <small>(optional, shown in JEI tooltips and /stagelock)</small></label>
          <textarea id="stage-desc" rows="2" placeholder="Smelt your first iron ingot." ${ro}>${esc(draft.description ?? "")}</textarea>
          ${itemSelectHtml(`id="stage-icon"`, draft.icon ?? "", readOnly || busy, "ICON")}
          <details class="stage-advanced">
            <summary>Advanced</summary>
            <label for="stage-id">STAGE ID</label>
            <input id="stage-id" value="${esc(draft.id)}" ${ro || (!isNew ? "readonly" : "")}
              title="${isNew ? "Made from the name. Quests and commands use this." : "A saved stage keeps its id."}" />
            <label for="stage-scope">WHO GETS IT</label>
            <select id="stage-scope" ${dis}>
              ${[["player", "Just the player who earns it"], ["team", "Their whole team"], ["global", "Everyone on the server"]]
                .map(([value, text]) => `<option value="${value}" ${(draft.scope ?? "player") === value ? "selected" : ""}>${text}</option>`).join("")}
            </select>
            <label class="check"><input id="stage-message" type="checkbox" ${draft.message !== false ? "checked" : ""} ${dis} /> Tell the player in chat when they unlock it</label>
          </details>
        </section>

        <section class="stage-step">
          <h3><span class="stage-step-n">2</span> How do players get it?</h3>
          ${grants.length
            ? `<ul class="stage-uses">${grants.map((use) => useRow(use, "Completing", options.modal)).join("")}</ul>`
            : `<p class="muted">No quest gives this stage yet.</p>`}
          ${readOnly ? "" : pickingQuest ? questPickerHtml(ctx.chapters(), draft.id) : `<button type="button" class="btn btn-ghost btn-sm" data-act="pick-quest">+ Granted by a quest…</button>`}
          <label>ALSO UNLOCKED BY ADVANCEMENT <small>(optional)</small></label>
          <div class="stage-chips">
            ${(draft.advancements ?? []).map((adv, i) => chip(esc(adv), readOnly ? "" : `data-remove-adv="${i}"`)).join("")}
          </div>
          ${readOnly ? "" : `<div class="stage-add-row">
            <input id="stage-adv" list="stage-advancements" placeholder="minecraft:story/smelt_iron" ${ro} />
            <button type="button" class="btn btn-ghost btn-sm" data-act="add-adv">Add</button>
          </div>
          <datalist id="stage-advancements">${(catalog.advancements ?? []).map((e) => `<option value="${esc(e.id)}"></option>`).join("")}</datalist>`}
          <label>NEEDS THESE STAGES FIRST <small>(optional)</small></label>
          <div class="stage-chips">
            ${(draft.requires ?? []).map((id, i) => chip(esc(stageName(ctx.stages(), id)), readOnly ? "" : `data-remove-req="${i}"`, `data-open-stage="${esc(id)}"`)).join("")}
          </div>
          ${readOnly ? "" : stageSelectHtml(`id="stage-require-pick"`, "", busy, "", "prereq", false)}
          ${needs.length ? `<label>QUESTS WAITING ON IT</label><ul class="stage-uses">${needs.map((use) => useRow(use, needVerb(use), options.modal)).join("")}</ul>` : ""}
        </section>

        <section class="stage-step">
          <h3><span class="stage-step-n">3</span> Until then, players can't…</h3>
          ${stageLockOn ? "" : `<p class="stage-note">StageLock isn't installed, so stages can't lock anything. They still work as flags for quests.</p>`}
          ${(draft.locks ?? []).map((lock, index) => lockHtml(lock, index, readOnly || busy, ctx, catalog)).join("")}
          ${readOnly || !stageLockOn ? "" : addingLock ? `
            <div class="stage-kinds" role="group" aria-label="What to lock">
              ${LOCK_KINDS.map((spec) => `<button type="button" class="stage-kind" data-add-kind="${spec.kind}"><span class="stage-kind-emoji" aria-hidden="true">${spec.emoji}</span>${esc(spec.label)}</button>`).join("")}
              <button type="button" class="btn btn-ghost btn-sm" data-act="cancel-kind">Cancel</button>
            </div>` : `<button type="button" class="btn btn-ghost" data-act="add-lock">+ Lock something</button>`}
        </section>

        <footer class="stage-editor-foot">
          <p class="stage-summary" id="stage-summary"></p>
          <ul class="stage-problems" id="stage-problems"></ul>
          <div class="stage-actions">
            ${readOnly ? "" : `<button type="button" class="btn btn-accent" data-act="save" ${busy ? "disabled" : ""}>${isNew ? "Create stage" : "Save stage"}</button>`}
            ${options.modal ? `<button type="button" class="btn btn-ghost" data-act="cancel" ${busy ? "disabled" : ""}>Cancel</button>` : ""}
            ${!readOnly && !isNew ? `<button type="button" class="btn btn-danger btn-sm" data-act="delete" ${busy ? "disabled" : ""}>Delete stage</button>` : ""}
            <span class="hint-text ${statusBad ? "bad" : "ok"}" role="status">${esc(status)}</span>
          </div>
        </footer>
      </div>`;
    wire();
    refreshSummary();
  };

  const refreshSummary = () => {
    const summary = host.querySelector<HTMLElement>("#stage-summary");
    const grants = usage().grants.map((use) => use.tileTitle ?? use.chapterTitle);
    if (summary) summary.innerHTML = esc(summarySentence(draft, grants, names)).replace(/^(Players without )(.+?)( can't)/, "$1<strong>$2</strong>$3");
    const problems = host.querySelector<HTMLElement>("#stage-problems");
    if (problems) {
      const list = readOnly ? [] : stageProblems(draft, ctx.stages(), isNew);
      problems.innerHTML = list.map((problem) => `<li>${esc(problem)}</li>`).join("");
    }
    const headTitle = host.querySelector<HTMLElement>(".stage-editor-head h2");
    if (headTitle) headTitle.textContent = draft.name?.trim() || (isNew ? "New stage" : draft.id);
  };

  const wire = () => {
    const on = (selector: string, event: string, fn: (el: HTMLElement, ev: Event) => void) =>
      host.querySelectorAll<HTMLElement>(selector).forEach((el) => el.addEventListener(event, (ev) => fn(el, ev)));

    on("#stage-name", "input", (el) => {
      draft.name = (el as HTMLInputElement).value;
      if (isNew && !idTouched) {
        draft.id = uniqueStageId(draft.name, ctx.stages().map((s) => s.id));
        const idInput = host.querySelector<HTMLInputElement>("#stage-id");
        if (idInput) idInput.value = draft.id;
      }
      refreshSummary();
    });
    on("#stage-desc", "input", (el) => { draft.description = (el as HTMLTextAreaElement).value; });
    on("#stage-id", "input", (el) => {
      idTouched = true;
      draft.id = (el as HTMLInputElement).value.trim().toLowerCase();
      refreshSummary();
    });
    on("#stage-scope", "change", (el) => { draft.scope = (el as HTMLSelectElement).value as AuthoredStage["scope"]; });
    on("#stage-message", "change", (el) => { draft.message = (el as HTMLInputElement).checked; });
    wireItemSelects(host, catalog.items, ctx.bridgeBase(), () => {
      const icon = readItemSelect(host, "#stage-icon");
      if (icon === (draft.icon ?? "")) return;
      draft.icon = icon;
      const img = host.querySelector<HTMLImageElement>(".stage-editor-icon");
      if (img) img.src = ctx.iconUrl(icon || "minecraft:amethyst_shard");
    }, "#stage-icon");

    on("[data-act=add-adv]", "click", () => {
      const input = host.querySelector<HTMLInputElement>("#stage-adv");
      const value = input?.value.trim() ?? "";
      if (!value) return;
      draft.advancements = [...new Set([...(draft.advancements ?? []), value])];
      paint();
    });
    on("#stage-adv", "keydown", (_el, ev) => {
      if ((ev as KeyboardEvent).key === "Enter") host.querySelector<HTMLButtonElement>("[data-act=add-adv]")?.click();
    });
    on("[data-remove-adv]", "click", (el) => {
      draft.advancements?.splice(Number(el.dataset.removeAdv), 1);
      paint();
    });
    on("[data-remove-req]", "click", (el, ev) => {
      ev.stopPropagation();
      draft.requires?.splice(Number(el.dataset.removeReq), 1);
      paint();
    });
    on("[data-open-stage]", "click", (el) => {
      if (ctx.onOpen && !options.modal) ctx.onOpen(el.dataset.openStage ?? "");
    });
    wireStageSelects(host, {
      stages: () => ctx.stages().filter((s) => s.id !== draft.id && !(draft.requires ?? []).includes(s.id)),
      iconUrl: ctx.iconUrl,
      onChange: () => {
        const picked = host.querySelector<HTMLInputElement>("#stage-require-pick")?.value ?? "";
        if (!picked) return;
        if (requiresCycle(ctx.stages().filter((s) => s.id !== draft.id).concat([{ id: draft.id, name: "", authored: true, stage: draft }]), draft.id, picked)) {
          setStatus(`${stageName(ctx.stages(), picked)} already needs this stage.`, true);
          return;
        }
        draft.requires = [...(draft.requires ?? []), picked];
        paint();
      },
      onCreate: () => setStatus("Save this stage first, then make the other one from the Stages tab.", true),
      onEdit: () => {},
      hint: () => "",
    });

    on("[data-act=pick-quest]", "click", () => { pickingQuest = true; paint(); });
    on("[data-act=cancel-quest]", "click", () => { pickingQuest = false; paint(); });
    on("[data-act=grant-quest]", "click", () => {
      const select = host.querySelector<HTMLSelectElement>("#stage-quest");
      const [chapterIndex, tileIndex] = (select?.value ?? "").split(":").map(Number);
      const picked = ctx.chapters()[chapterIndex];
      const chapterId = picked?.id;
      const tileId = picked?.tiles[tileIndex]?.id;
      if (!chapterId || !tileId) return;
      if (!draft.id) {
        setStatus("Give the stage a name first.", true);
        return;
      }
      ctx.onGrantFromQuest(draft.id, chapterId, tileId);
      pickingQuest = false;
      setStatus("Reward added. Save that chapter to keep it.", false, false);
      paint();
    });
    on("[data-jump-chapter]", "click", (el) => ctx.onJump(el.dataset.jumpChapter ?? "", el.dataset.jumpTile || undefined));

    on("[data-act=add-lock]", "click", () => { addingLock = true; paint(); });
    on("[data-act=cancel-kind]", "click", () => { addingLock = false; paint(); });
    on("[data-add-kind]", "click", (el) => {
      draft.locks = [...(draft.locks ?? []), { kind: el.dataset.addKind as LockKind, targets: [] }];
      addingLock = false;
      paint();
      host.querySelector<HTMLInputElement>(`[data-lock-search="${draft.locks.length - 1}"]`)?.focus();
    });
    on("[data-remove-lock]", "click", (el) => {
      draft.locks?.splice(Number(el.dataset.removeLock), 1);
      paint();
    });
    on("[data-remove-target]", "click", (el) => {
      const [lockIndex, targetIndex] = (el.dataset.removeTarget ?? "").split(":").map(Number);
      draft.locks?.[lockIndex]?.targets.splice(targetIndex, 1);
      paint();
    });
    on("[data-lock-flag]", "change", (el) => {
      const [lockIndex, key] = (el.dataset.lockFlag ?? "").split(":");
      const lock = draft.locks?.[Number(lockIndex)];
      if (!lock || !key) return;
      lock.flags = { ...(lock.flags ?? {}), [key]: (el as HTMLInputElement).checked };
      refreshSummary();
    });
    on("[data-lock-message]", "input", (el) => {
      const lock = draft.locks?.[Number(el.dataset.lockMessage)];
      if (lock) lock.message = (el as HTMLInputElement).value;
    });
    host.querySelectorAll<HTMLInputElement>("[data-lock-search]").forEach((input) => wireTargetSearch(input));

    on("[data-act=save]", "click", () => void save());
    on("[data-act=cancel]", "click", () => ctx.onCancel?.());
    on("[data-act=delete]", "click", () => void remove());
    on("[data-act=duplicate]", "click", () => {
      renderStageEditor(host, null, ctx, { ...options, copyOf: draft, prefillName: `${draft.name || draft.id} (copy)` });
    });
  };

  /** The search box under a lock: type to filter the pack, click or Enter to add, Enter on "#tag" adds the tag. */
  const wireTargetSearch = (input: HTMLInputElement) => {
    const index = Number(input.dataset.lockSearch);
    const lock = draft.locks?.[index];
    const results = host.querySelector<HTMLElement>(`[data-lock-results="${index}"]`);
    if (!lock || !results) return;
    const pool = targetPool(catalog, lock.kind);
    const add = (id: string) => {
      const value = id.trim();
      if (!value || lock.targets.includes(value)) return;
      lock.targets.push(value);
      paint();
      host.querySelector<HTMLInputElement>(`[data-lock-search="${index}"]`)?.focus();
    };
    const render = () => {
      const needle = input.value.trim().toLowerCase();
      if (!needle) {
        results.hidden = true;
        return;
      }
      const hits = pool.filter((entry) => !lock.targets.includes(entry.id)
        && (entry.id.toLowerCase().includes(needle) || (entry.name ?? "").toLowerCase().includes(needle))).slice(0, MAX_HITS);
      const typedTag = /^#[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(needle) && lock.kind !== "mod" && lock.kind !== "dimension";
      results.innerHTML = [
        ...(typedTag ? [`<button type="button" class="stage-hit" data-hit="${esc(needle)}"><span class="stage-hit-tag">#</span><span><strong>Everything tagged ${esc(needle)}</strong></span></button>`] : []),
        ...hits.map((entry) => `<button type="button" class="stage-hit" data-hit="${esc(entry.id)}">
          ${lock.kind === "item" ? `<img class="item-icon" alt="" width="16" height="16" loading="lazy" src="${esc(ctx.iconUrl(entry.id))}" />` : ""}
          <span><strong>${esc(entry.name || entry.id)}</strong> <small>${esc(entry.id)}</small></span></button>`),
        ...(!hits.length && !typedTag ? [`<div class="stage-hit-empty">Nothing matches. ${lock.kind === "item" || lock.kind === "block" || lock.kind === "entity" ? "Type #namespace:tag for a tag." : ""}</div>`] : []),
      ].join("");
      results.hidden = false;
      results.querySelectorAll<HTMLButtonElement>("[data-hit]").forEach((button) => {
        button.addEventListener("mousedown", (event) => {
          event.preventDefault();
          add(button.dataset.hit ?? "");
        });
      });
    };
    input.addEventListener("input", render);
    input.addEventListener("focus", render);
    input.addEventListener("blur", () => window.setTimeout(() => { results.hidden = true; }, 120));
    input.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        const first = results.querySelector<HTMLButtonElement>("[data-hit]");
        if (first) add(first.dataset.hit ?? "");
      } else if (event.key === "Escape") {
        results.hidden = true;
      }
    });
  };

  const setStatus = (text: string, bad: boolean, repaint = true) => {
    status = text;
    statusBad = bad;
    if (repaint) {
      const el = host.querySelector<HTMLElement>(".stage-actions [role=status]");
      if (el) {
        el.textContent = text;
        el.classList.toggle("bad", bad);
        el.classList.toggle("ok", !bad);
      }
    }
  };

  const save = async () => {
    const problems = stageProblems(draft, ctx.stages(), isNew);
    if (problems.length) {
      setStatus(problems[0], true);
      return;
    }
    busy = true;
    setStatus("Saving…", false, false);
    paint();
    const error = await ctx.onSave(cleanStage(draft), (text) => setStatus(text, false));
    busy = false;
    if (error) {
      setStatus(error, true, false);
      paint();
      return;
    }
    ctx.onDone(draft.id);
  };

  const remove = async () => {
    const { grants, needs } = usage();
    const used = [...grants, ...needs];
    const detail = used.length
      ? `\n\nThese still mention it and will point at a stage that no longer exists:\n${used.map((use) => `• ${use.tileTitle ?? use.chapterTitle} (${use.chapterTitle})`).join("\n")}`
      : "";
    if (!window.confirm(`Delete the stage ${draft.name || draft.id}?${detail}`)) return;
    busy = true;
    setStatus("Deleting…", false, false);
    paint();
    const error = await ctx.onDelete(draft.id);
    busy = false;
    if (error) {
      setStatus(error, true, false);
      paint();
      return;
    }
    ctx.onDone("");
  };

  paint();
  if (isNew) host.querySelector<HTMLInputElement>("#stage-name")?.focus();
}

function lockHtml(lock: StageLockGroup, index: number, readOnly: boolean, ctx: StageEditorContext, catalog: PackCatalog): string {
  const spec = kindSpec(lock.kind);
  const dis = readOnly ? "disabled" : "";
  return `
    <fieldset class="stage-lock" data-kind="${spec.kind}">
      <legend><span aria-hidden="true">${spec.emoji}</span> ${esc(spec.label)}
        ${readOnly ? "" : `<button type="button" class="icon-btn" data-remove-lock="${index}" title="Remove this lock" aria-label="Remove this lock">×</button>`}
      </legend>
      <div class="stage-chips">
        ${lock.targets.map((target, j) => chip(
          `${spec.kind === "item" && !target.startsWith("#") ? `<img class="item-icon" alt="" width="16" height="16" src="${esc(ctx.iconUrl(target))}" />` : ""}${esc(nameOf(catalog, spec.kind, target))}`,
          readOnly ? "" : `data-remove-target="${index}:${j}"`,
          `title="${esc(target)}"`,
        )).join("")}
        ${lock.targets.length ? "" : `<span class="muted">Nothing picked yet.</span>`}
      </div>
      ${readOnly ? "" : `<div class="stage-search">
        <input type="search" data-lock-search="${index}" placeholder="${esc(spec.placeholder)}" autocomplete="off" />
        <div class="stage-hits" data-lock-results="${index}" hidden></div>
      </div>`}
      ${spec.flags.length ? `<div class="stage-flags">${spec.flags.map((flag) => `
        <label class="check"><input type="checkbox" data-lock-flag="${index}:${flag.key}" ${flagOn(lock, flag.key) ? "checked" : ""} ${dis} /> ${esc(flag.label)}</label>`).join("")}
      </div>` : ""}
      ${spec.message !== undefined || lock.message ? `<label>MESSAGE <small>(optional, shown when they try)</small></label>
        <input data-lock-message="${index}" value="${esc(lock.message ?? "")}" placeholder="${esc(spec.message ?? "")}" ${readOnly ? "readonly" : ""} />` : ""}
    </fieldset>`;
}

function questPickerHtml(chapters: Chapter[], stageId: string): string {
  const groups = chapters.map((c, ci) => (c.tiles?.length ? `
    <optgroup label="${esc(c.title || c.id)}">
      ${c.tiles.map((t, ti) => {
        const has = (t.rewards ?? []).some((r) => r.type === "stage" && r.stage === stageId);
        return `<option value="${ci}:${ti}" ${has ? "disabled" : ""}>${esc(t.title || t.id)}${has ? " (already)" : ""}</option>`;
      }).join("")}
    </optgroup>` : "")).join("");
  return `<div class="stage-add-row">
    <select id="stage-quest" aria-label="Quest that grants this stage">${groups}</select>
    <button type="button" class="btn btn-accent btn-sm" data-act="grant-quest">Add</button>
    <button type="button" class="btn btn-ghost btn-sm" data-act="cancel-quest">Cancel</button>
  </div>
  <p class="muted">Completing that quest will give the stage. Save its chapter to keep the change.</p>`;
}

function useRow(use: StageUse, verb: string, modal: boolean | undefined): string {
  const label = use.tileTitle ? `${verb} <strong>${esc(use.tileTitle)}</strong> <small>${esc(use.chapterTitle)}</small>` : `${verb} <strong>${esc(use.chapterTitle)}</strong>`;
  return `<li>${modal ? label : `<button type="button" class="stage-use" data-jump-chapter="${esc(use.chapter)}" data-jump-tile="${esc(use.tile ?? "")}">${label}</button>`}</li>`;
}

function needVerb(use: StageUse): string {
  switch (use.how) {
    case "required": return "Locked:";
    case "hidden": return "Hidden:";
    case "link": return "Arrow into";
    case "chapter": return "Chapter";
    default: return "";
  }
}

function chip(inner: string, removeAttr: string, extra = ""): string {
  return `<span class="stage-chip" ${extra}>${inner}${removeAttr ? `<button type="button" class="stage-chip-x" ${removeAttr} aria-label="Remove">×</button>` : ""}</span>`;
}

function stageName(stages: StageInfo[], id: string): string {
  return stages.find((s) => s.id === id)?.name ?? id;
}

function targetPool(catalog: PackCatalog, kind: LockKind): CatalogEntry[] {
  switch (kind) {
    case "item": return [...catalog.items, ...catalog.tags.map((t) => ({ id: `#${t.id}`, name: `#${t.id}` }))];
    case "block": return catalog.blocks;
    case "mod": return catalog.mods ?? [];
    case "dimension": return catalog.dimensions ?? [];
    case "entity": return catalog.entities;
  }
}

function nameOf(catalog: PackCatalog, kind: LockKind, id: string): string {
  if (id.startsWith("#")) return id;
  const pool = targetPool(catalog, kind);
  return pool.find((entry) => entry.id === id)?.name || id;
}

function esc(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}
