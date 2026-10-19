// The stage picker used wherever a quest names a stage (Requires stage, Hidden until, stage rewards, unlock
// conditions). It looks like the item picker; the chosen id lives in a hidden <input> so the card's apply() reads
// it like any other field. The last row always offers to make a new stage from what was typed.
import type { StageInfo } from "./stages";

const STAGE_FALLBACK_ICON = "minecraft:amethyst_shard";

export interface StageSelectContext {
  stages: () => StageInfo[];
  iconUrl: (item: string) => string;
  onChange: () => void;
  /** Make a new stage named {@code name}; call {@code done} with its id once it is saved (or never, if cancelled). */
  onCreate: (name: string, done: (id: string) => void) => void;
  onEdit: (id: string) => void;
  /** One line under the field about the chosen stage, as HTML. */
  hint: (id: string, role: string) => string;
}

export function stageSelectHtml(inputAttrs: string, value: string, disabled: boolean, label: string, role = "", allowNone = true): string {
  return `
    ${label ? `<label>${escapeHtml(label)}</label>` : ""}
    <div class="stage-select" data-stage-role="${escapeHtml(role)}" ${allowNone ? "data-allow-none" : ""}>
      <input type="hidden" ${inputAttrs} value="${escapeHtml(value)}" />
      <button type="button" class="stage-select-trigger" ${disabled ? "disabled" : ""}>
        <img class="item-icon" alt="" width="16" height="16" decoding="async" />
        <span class="stage-select-label"></span>
        <small class="stage-select-id"></small>
        <span class="item-select-caret">▾</span>
      </button>
      <div class="stage-select-panel" hidden>
        <input type="search" class="stage-select-filter" placeholder="Find a stage, or type a new name…" autocomplete="off" />
        <div class="stage-select-list" role="listbox"></div>
      </div>
      <div class="stage-select-hint"></div>
    </div>`;
}

let outsideBound = false;

export function wireStageSelects(root: HTMLElement, ctx: StageSelectContext): void {
  if (!outsideBound) {
    outsideBound = true;
    document.addEventListener("click", (event) => {
      const target = event.target as Node | null;
      document.querySelectorAll<HTMLElement>(".stage-select-panel").forEach((panel) => {
        const host = panel.closest(".stage-select");
        if (host && target && host.contains(target)) return;
        panel.hidden = true;
      });
    });
  }

  root.querySelectorAll<HTMLElement>(".stage-select:not([data-wired])").forEach((host) => {
    host.dataset.wired = "1";
    const input = host.querySelector<HTMLInputElement>("input[type=hidden]");
    const trigger = host.querySelector<HTMLButtonElement>(".stage-select-trigger");
    const panel = host.querySelector<HTMLElement>(".stage-select-panel");
    const filter = host.querySelector<HTMLInputElement>(".stage-select-filter");
    const list = host.querySelector<HTMLElement>(".stage-select-list");
    const label = host.querySelector<HTMLElement>(".stage-select-label");
    const idEl = host.querySelector<HTMLElement>(".stage-select-id");
    const icon = host.querySelector<HTMLImageElement>(".item-icon");
    const hint = host.querySelector<HTMLElement>(".stage-select-hint");
    if (!input || !trigger || !panel || !filter || !list || !label) return;
    const role = host.dataset.stageRole ?? "";
    const allowNone = host.hasAttribute("data-allow-none");

    const paint = () => {
      const id = input.value;
      const info = ctx.stages().find((entry) => entry.id === id);
      label.textContent = id ? info?.name ?? id : allowNone ? "(none)" : "Choose a stage…";
      if (idEl) idEl.textContent = id && info?.name !== id ? id : "";
      if (icon) {
        if (id) {
          icon.src = ctx.iconUrl(info?.stage?.icon || STAGE_FALLBACK_ICON);
          icon.style.visibility = "visible";
        } else {
          icon.removeAttribute("src");
          icon.style.visibility = "hidden";
        }
      }
      if (hint) {
        hint.innerHTML = id ? ctx.hint(id, role) : "";
        hint.querySelectorAll<HTMLElement>("[data-stage-edit]").forEach((button) => {
          button.addEventListener("click", (event) => {
            event.preventDefault();
            ctx.onEdit(button.dataset.stageEdit ?? "");
          });
        });
      }
    };

    const setValue = (id: string) => {
      input.value = id;
      paint();
      ctx.onChange();
    };

    const renderList = () => {
      const typed = filter.value.trim();
      const needle = typed.toLowerCase();
      const stages = ctx.stages();
      const matched = needle
        ? stages.filter((entry) => entry.id.toLowerCase().includes(needle) || entry.name.toLowerCase().includes(needle))
        : stages;
      const rows = matched.map((entry) => `
        <button type="button" class="stage-select-option ${entry.id === input.value ? "active" : ""}" data-id="${escapeHtml(entry.id)}">
          <img class="item-icon" alt="" width="16" height="16" loading="lazy" src="${escapeHtml(ctx.iconUrl(entry.stage?.icon || STAGE_FALLBACK_ICON))}" />
          <span class="item-select-option-text">
            <strong>${escapeHtml(entry.name)}</strong>
            <small>${escapeHtml(entry.id)}</small>
          </span>
          <span class="stage-badge ${entry.authored ? "yours" : "pack"}">${entry.authored ? "yours" : "pack"}</span>
        </button>`);
      if (allowNone && !needle) {
        rows.unshift(`<button type="button" class="stage-select-option none" data-id=""><span class="item-select-option-text"><strong>(none)</strong></span></button>`);
      }
      if (!matched.length && needle) rows.push(`<div class="stage-select-empty">No stage matches “${escapeHtml(typed)}”.</div>`);
      rows.push(`<button type="button" class="stage-select-option create" data-create="1">
        <span class="stage-create-plus">+</span>
        <span class="item-select-option-text"><strong>${typed ? `New stage “${escapeHtml(typed)}”…` : "New stage…"}</strong>
        <small>Name it, choose what it locks</small></span>
      </button>`);
      list.innerHTML = rows.join("");
      list.querySelectorAll<HTMLButtonElement>(".stage-select-option").forEach((option) => {
        option.addEventListener("click", (event) => {
          event.preventDefault();
          event.stopPropagation();
          panel.hidden = true;
          if (option.dataset.create) {
            ctx.onCreate(typed, (id) => setValue(id));
          } else {
            setValue(option.dataset.id ?? "");
          }
        });
      });
    };

    paint();
    trigger.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (trigger.disabled) return;
      const willOpen = panel.hidden;
      document.querySelectorAll<HTMLElement>(".stage-select-panel").forEach((other) => { other.hidden = true; });
      panel.hidden = !willOpen;
      if (!panel.hidden) {
        filter.value = "";
        renderList();
        filter.focus();
      }
    });
    filter.addEventListener("input", renderList);
    filter.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        const first = list.querySelector<HTMLButtonElement>(".stage-select-option:not(.none)");
        first?.click();
      } else if (event.key === "Escape") {
        panel.hidden = true;
        trigger.focus();
      }
    });
    panel.addEventListener("mousedown", (event) => event.stopPropagation());
  });
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}
