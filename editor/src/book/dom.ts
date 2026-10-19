// A small element helper and a keyed list update, so the book renders without a framework.

type Child = Node | string | null | undefined | false;

/**
 * h("button", { class: "x", type: "button", dataset: { id }, onclick }, "Label", child) — props starting with "on"
 * become listeners, `dataset` fills data-*, `style` may be a string, everything else is an attribute.
 */
export function h(tag: string, props: Record<string, unknown> = {}, ...children: Child[]): HTMLElement {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key.startsWith("on") && typeof value === "function") {
      el.addEventListener(key.slice(2), value as EventListener);
    } else if (key === "dataset") {
      Object.assign(el.dataset, value as Record<string, string>);
    } else if (key === "class") {
      el.className = String(value);
    } else {
      el.setAttribute(key, value === true ? "" : String(value));
    }
  }
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child);
  }
  return el;
}

const KEY = "reconcileKey";

/**
 * Make parent's children match items, in order: an item whose key had a node keeps that node, a new key gets create(); update
 * runs on every item's node, a dropped key's node is removed. Unchanged nodes are never recreated.
 */
export function reconcile<T>(
  parent: HTMLElement,
  items: T[],
  key: (item: T) => string,
  create: (item: T) => HTMLElement,
  update: (el: HTMLElement, item: T) => void,
): void {
  const old = new Map<string, HTMLElement>();
  for (const child of [...parent.children] as HTMLElement[]) {
    if (child.dataset[KEY] !== undefined) old.set(child.dataset[KEY]!, child);
  }
  let cursor: Element | null = parent.firstElementChild;
  for (const item of items) {
    const k = key(item);
    let el = old.get(k);
    if (el) {
      old.delete(k);
      update(el, item);
    } else {
      el = create(item);
      el.dataset[KEY] = k;
      update(el, item);
    }
    if (el !== cursor) parent.insertBefore(el, cursor);
    else cursor = cursor.nextElementSibling;
  }
  for (const stale of old.values()) stale.remove();
}
