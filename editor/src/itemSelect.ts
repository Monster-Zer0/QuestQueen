import type { CatalogEntry } from "./packCatalog";

const MAX_ROWS = 250;

export function iconUrl(bridgeBase: string, id: string): string {
  return `${bridgeBase}/api/icon?id=${encodeURIComponent(id)}`;
}

export function itemSelectHtml(
  attrs: string,
  value: string,
  disabled: boolean,
  label = "ITEM",
): string {
  const shown = value || "Select item…";
  return `
    <label>${label}</label>
    <div class="item-select" ${attrs} data-value="${escapeAttr(value)}">
      <button type="button" class="item-select-trigger" ${disabled ? "disabled" : ""}>
        <img class="item-icon" alt="" width="16" height="16" decoding="async" />
        <span class="item-select-label">${escapeAttr(shown)}</span>
        <span class="item-select-caret">▾</span>
      </button>
      <div class="item-select-panel" hidden>
        <input type="search" class="item-select-filter" placeholder="Filter pack items…" autocomplete="off" />
        <div class="item-select-meta"></div>
        <div class="item-select-list"></div>
      </div>
    </div>
  `;
}

let outsideBound = false;

export function wireItemSelects(
  root: HTMLElement,
  items: CatalogEntry[],
  bridgeBase: string,
  onChange: () => void,
): void {
  if (!outsideBound) {
    outsideBound = true;
    document.addEventListener("click", (event) => {
      const target = event.target as Node | null;
      document.querySelectorAll<HTMLElement>(".item-select-panel").forEach((panel) => {
        const host = panel.closest(".item-select");
        if (host && target && host.contains(target)) return;
        panel.hidden = true;
      });
    });
  }

  root.querySelectorAll<HTMLElement>(".item-select").forEach((host) => {
    const trigger = host.querySelector<HTMLButtonElement>(".item-select-trigger");
    const panel = host.querySelector<HTMLElement>(".item-select-panel");
    const filter = host.querySelector<HTMLInputElement>(".item-select-filter");
    const list = host.querySelector<HTMLElement>(".item-select-list");
    const meta = host.querySelector<HTMLElement>(".item-select-meta");
    const label = host.querySelector<HTMLElement>(".item-select-label");
    const icon = host.querySelector<HTMLImageElement>(".item-icon");
    if (!trigger || !panel || !filter || !list || !label) return;

    const setValue = (id: string) => {
      host.dataset.value = id;
      label.textContent = id || "Select item…";
      if (icon) {
        if (id) {
          icon.src = iconUrl(bridgeBase, id);
          icon.style.visibility = "visible";
        } else {
          icon.removeAttribute("src");
          icon.style.visibility = "hidden";
        }
      }
      onChange();
    };

    setValue(host.dataset.value ?? "");

    const renderList = () => {
      const needle = filter.value.trim().toLowerCase();
      const matched = !needle
        ? items
        : items.filter((entry) =>
            entry.id.toLowerCase().includes(needle)
            || (entry.name ?? "").toLowerCase().includes(needle));
      const shown = matched.slice(0, MAX_ROWS);
      if (meta) {
        meta.textContent = matched.length > MAX_ROWS
          ? `Showing ${shown.length} of ${matched.length} — keep typing to narrow`
          : `${matched.length} pack items`;
      }
      list.innerHTML = shown.map((entry) => `
        <button type="button" class="item-select-option" data-id="${escapeAttr(entry.id)}">
          <img class="item-icon" alt="" width="16" height="16" loading="lazy" decoding="async"
               src="${iconUrl(bridgeBase, entry.id)}" />
          <span class="item-select-option-text">
            <strong>${escapeAttr(entry.name ?? entry.id)}</strong>
            <small>${escapeAttr(entry.id)}</small>
          </span>
        </button>
      `).join("");
      list.querySelectorAll<HTMLButtonElement>(".item-select-option").forEach((option) => {
        option.addEventListener("click", (event) => {
          event.preventDefault();
          event.stopPropagation();
          setValue(option.dataset.id ?? "");
          panel.hidden = true;
        });
      });
    };

    trigger.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (trigger.disabled) return;
      const willOpen = panel.hidden;
      root.querySelectorAll<HTMLElement>(".item-select-panel").forEach((other) => {
        other.hidden = true;
      });
      panel.hidden = !willOpen;
      if (!panel.hidden) {
        filter.value = "";
        renderList();
        filter.focus();
      }
    });

    filter.addEventListener("input", () => renderList());
    filter.addEventListener("mousedown", (event) => event.stopPropagation());
    panel.addEventListener("mousedown", (event) => event.stopPropagation());
  });
}

export function readItemSelect(root: HTMLElement, selector: string): string {
  return root.querySelector<HTMLElement>(selector)?.dataset.value ?? "";
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}
