// Tasks and rewards, chosen and written in plain words. Two pieces share one set of controls:
// - the control line, the original mock's EDIT MODE row: a book-style type dropdown, then the task's fields as
//   small boxed inputs on the same line (count, item, mob, …), used for every row on the edit card;
// - the picker (⊕ TASK / ⊕ REWARD): "What should the player do?" with Gather · Fight · Explore · Do and search,
//   then the same control line as "Your task", with the sentence players will see, and Add.
import { bridgeBase } from "../bridge";
import { commandPreviewHtml } from "../commandReward";
import { iconUrl } from "../itemSelect";
import type { CatalogEntry } from "../packCatalog";
import type { Reward, Task } from "../types";
import { h } from "./dom";
import { namer } from "./names";
import { REWARD_KINDS, TASK_KINDS, emptyReward, emptyTask, rewardKindOf, kindOf, rewardSentence, taskSentence, type FieldSpec, type Kind, type Picker } from "./sentences";
import type { BookState } from "./store";

const LISTS: Partial<Record<Picker, (s: BookState) => CatalogEntry[]>> = {
  item: (s) => s.catalog.items, entity: (s) => s.catalog.entities, block: (s) => s.catalog.blocks, tag: (s) => s.catalog.tags,
  entityTag: () => [], biome: (s) => s.catalog.biomes, structure: (s) => s.catalog.structures, dimension: (s) => s.catalog.dimensions ?? [],
  fluid: (s) => s.catalog.fluids ?? [], advancement: (s) => s.catalog.advancements, loot: (s) => s.catalog.loot,
  stage: (s) => s.stages.stages.map((st) => ({ id: st.id, name: st.name })),
};

/** One shared <datalist> per catalog list, so suggestions are not copied into every field. */
function datalistFor(picker: Picker, state: BookState): string | undefined {
  const entries = LISTS[picker]?.(state);
  if (!entries?.length) return undefined;
  const id = `qq-list-${picker}`;
  let list = document.getElementById(id);
  if (!list || list.childElementCount !== entries.length) {
    list?.remove();
    list = h("datalist", { id }, ...entries.slice(0, 8000).map((e) => h("option", { value: e.id }, e.name && e.name !== e.id ? e.name : "")));
    document.body.append(list);
  }
  return id;
}

type Value = Record<string, unknown>;

function field(spec: FieldSpec, value: Value, state: BookState, set: (key: string, v: unknown) => void): HTMLElement {
  const raw = value[spec.key];
  const number = spec.picker === "number";
  const input = h("input", {
    class: `box${number ? " num" : ""}`, type: number ? "number" : "text", dataset: { field: spec.key },
    value: raw === undefined ? "" : String(raw), list: datalistFor(spec.picker, state),
    "aria-label": spec.label, placeholder: number ? "" : spec.picker === "text" ? spec.label : "namespace:id",
    min: number && spec.key !== "x" && spec.key !== "y" && spec.key !== "z" ? "1" : undefined, spellcheck: "false",
  }) as HTMLInputElement;
  const icon = spec.picker === "item" ? h("img", { class: "field-icon icon", alt: "", hidden: !raw }) as HTMLImageElement : null;
  const showIcon = () => {
    if (!icon) return;
    icon.hidden = !input.value.trim();
    if (input.value.trim()) icon.src = iconUrl(bridgeBase(), input.value.trim().split("[")[0]);
  };
  showIcon();
  input.addEventListener("input", () => {
    const text = input.value.trim();
    set(spec.key, number ? (text === "" ? undefined : Number(text)) : text);
    showIcon();
  });
  return h("label", { class: "field" }, h("span", { class: "field-label" }, spec.label), icon, input);
}

function fieldsOf(kind: Kind | undefined, value: Value, state: BookState, set: (key: string, v: unknown) => void): HTMLElement[] {
  return (kind?.fields ?? []).map((spec) => field(spec, value, state, set));
}

function kindSelect(kinds: Kind[], current: string, grouped: boolean): HTMLSelectElement {
  const options = (list: Kind[]) => list.map((k) => h("option", { value: k.type, selected: k.type === current }, k.name));
  const children = grouped
    ? (["Gather", "Fight", "Explore", "Do"] as const).map((g) => h("optgroup", { label: g }, ...options(kinds.filter((k) => k.group === g))))
    : options(kinds);
  const select = h("select", { class: "kind-select", "aria-label": "Type" }, ...children) as HTMLSelectElement;
  select.value = current;
  return select;
}

/** Drop empty optional halves of "a or b" fields, so a mob task with an empty tag field saves without "tag". */
function tidy(kind: Kind | undefined, value: Value): Value {
  const out = { ...value };
  for (const spec of kind?.fields ?? []) {
    if (spec.optionalWith && (out[spec.key] === "" || out[spec.key] === undefined)) {
      if (out[spec.optionalWith] !== undefined && out[spec.optionalWith] !== "") delete out[spec.key];
    }
    if (out[spec.key] === undefined) delete out[spec.key];
  }
  return out;
}

/** The mock's control line for a task: type dropdown, then its fields. onChange gets each new task. */
export function taskLine(task: Task, state: BookState, onChange: (task: Task) => void): HTMLElement {
  const line = h("div", { class: "control-line" });
  let value: Value = { ...task };
  const draw = () => {
    const kind = kindOf(String(value.type));
    const select = kindSelect(TASK_KINDS, String(value.type), true);
    select.addEventListener("change", () => {
      value = { ...emptyTask(select.value) };
      onChange(value as unknown as Task);
      draw();
    });
    line.replaceChildren(select, ...fieldsOf(kind, value, state, (key, v) => {
      value = tidy(kind, { ...value, [key]: v });
      onChange(value as unknown as Task);
    }));
  };
  draw();
  return line;
}

/** The control line for a reward, with the choice and command builders. */
export function rewardLine(reward: Reward, state: BookState, onChange: (reward: Reward) => void): HTMLElement {
  const line = h("div", { class: "control-line reward-line" });
  let value: Reward = structuredClone(reward);
  const emit = () => onChange(structuredClone(value));
  const draw = () => {
    const select = kindSelect(REWARD_KINDS, value.type, false);
    select.addEventListener("change", () => {
      value = emptyReward(select.value);
      emit();
      draw();
    });
    const parts: HTMLElement[] = [select];
    if (value.type === "choice") {
      const options = value.options ?? [];
      const list = h("div", { class: "choice-options" }, ...options.map((option, i) => h("div", { class: "choice-option" },
        ...fieldsOf(rewardKindOf("item"), option as unknown as Value, state, (key, v) => {
          (options[i] as unknown as Value)[key] = v;
          emit();
        }),
        h("button", { type: "button", class: "choice-remove", "aria-label": `Remove option ${i + 1}`, disabled: options.length <= 2,
          onclick: () => { options.splice(i, 1); emit(); draw(); } }, "✕"))));
      parts.push(list, h("button", { type: "button", class: "picker-btn small", onclick: () => { options.push(emptyReward("item")); value.options = options; emit(); draw(); } }, "+ Option"));
    } else if (value.type === "command") {
      const commandFields: FieldSpec[] = [
        { key: "command", label: "Command", picker: "text" }, { key: "icon", label: "Icon (item, glyph:id or a texture)", picker: "text" },
        { key: "name", label: "Name players see", picker: "text" }, { key: "description", label: "Description on hover", picker: "text" },
      ];
      const previewEl = h("div", { class: "command-preview" });
      const refresh = () => { previewEl.innerHTML = commandPreviewHtml(value, (id) => iconUrl(bridgeBase(), id)); };
      parts.push(...commandFields.map((spec) => field(spec, value as unknown as Value, state, (key, v) => {
        (value as unknown as Value)[key] = v;
        if (v === "" && key !== "command") delete (value as unknown as Value)[key];
        refresh();
        emit();
      })), h("div", { class: "field-label" }, "Players see"), previewEl);
      refresh();
    } else {
      const kind = rewardKindOf(value.type);
      parts.push(...fieldsOf(kind, value as unknown as Value, state, (key, v) => {
        value = tidy(kind, { ...(value as unknown as Value), [key]: v }) as unknown as Reward;
        emit();
      }));
    }
    line.replaceChildren(...parts);
  };
  draw();
  return line;
}

interface PickerSpec<T> {
  title: string;
  kinds: Kind[];
  grouped: boolean;
  empty: (type: string) => T;
  line: (value: T, state: BookState, onChange: (v: T) => void) => HTMLElement;
  sentence: (value: T, state: BookState) => string;
  noun: string;
}

function openPicker<T extends { type: string }>(anchor: HTMLElement, state: BookState, spec: PickerSpec<T>, current?: T): Promise<T | null> {
  document.querySelector(".task-picker")?.remove();
  return new Promise((resolve) => {
    let value: T | null = current ? structuredClone(current) : null;
    const close = (result: T | null) => {
      pop.remove();
      anchor.focus?.();
      resolve(result);
    };
    const search = h("input", { class: "picker-search", type: "search", placeholder: "Search: kill, find, craft, visit, talk…", "aria-label": `Search ${spec.noun} types` }) as HTMLInputElement;
    const groups = spec.grouped ? (["Gather", "Fight", "Explore", "Do"] as const) : ([null] as const);
    const options: HTMLButtonElement[] = [];
    const grid = h("div", { class: `kind-grid${spec.grouped ? "" : " single"}` }, ...groups.map((g) => h("div", { class: "kind-col" },
      g ? h("div", { class: "kind-group" }, g.toUpperCase()) : null,
      ...spec.kinds.filter((k) => !g || k.group === g).map((k) => {
        const b = h("button", { type: "button", class: "kind-option", dataset: { type: k.type, words: `${k.name} ${k.hint} ${k.type}`.toLowerCase() },
          onclick: () => choose(k.type) }, h("strong", {}, k.name), h("small", {}, k.hint)) as HTMLButtonElement;
        options.push(b);
        return b;
      }))));
    search.addEventListener("input", () => {
      const q = search.value.trim().toLowerCase();
      for (const b of options) b.hidden = Boolean(q) && !b.dataset.words!.includes(q);
    });
    const builder = h("section", { class: "your-task", hidden: true, "aria-label": `Your ${spec.noun}` });
    const see = h("div", { class: "players-see" });
    const ok = h("button", { type: "button", class: "picker-btn primary", onclick: () => value && close(value) }, current ? "Save" : `Add ${spec.noun}`);
    const changeType = h("button", { type: "button", class: "picker-link", onclick: () => { grid.hidden = false; search.hidden = false; search.focus(); } }, "Change type");
    const refreshSee = () => {
      see.replaceChildren(h("span", { class: "see-label" }, "Players see: "), h("span", { class: "see-text qq-text" }, value ? spec.sentence(value, state) : ""));
    };
    const showBuilder = () => {
      if (!value) return;
      builder.hidden = false;
      builder.replaceChildren(h("div", { class: "kind-group" }, `YOUR ${spec.noun.toUpperCase()}`),
        spec.line(value, state, (v) => { value = v; refreshSee(); }), see,
        h("div", { class: "picker-actions" }, current ? changeType : null, h("span", { class: "strip-space" }),
          h("button", { type: "button", class: "picker-btn", onclick: () => close(null) }, "Cancel"), ok));
      refreshSee();
      builder.querySelector<HTMLElement>("input, select")?.focus();
    };
    const choose = (type: string) => {
      value = spec.empty(type);
      grid.hidden = true;
      search.hidden = true;
      showBuilder();
    };
    const pop = h("div", { class: "task-picker", role: "dialog", "aria-modal": "true", "aria-label": spec.title },
      h("div", { class: "picker-head" }, h("h2", { class: "picker-title" }, spec.title),
        h("button", { type: "button", class: "picker-btn", "aria-label": "Close", onclick: () => close(null) }, "✕")),
      search, grid, builder,
      h("div", { class: "picker-actions picker-foot" }, h("span", { class: "strip-space" }),
        h("button", { type: "button", class: "picker-btn", onclick: () => close(null) }, "Cancel")));
    pop.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        close(null);
      }
    });
    document.body.append(pop);
    if (current) {
      grid.hidden = true;
      search.hidden = true;
      showBuilder();
    } else {
      search.focus();
    }
    // The foot's Cancel only matters before a type is chosen.
    builder.addEventListener("focusin", () => { pop.querySelector<HTMLElement>(".picker-foot")!.hidden = true; });
  });
}

const countSuffix = (task: Task) => {
  const need = task.count ?? task.levels ?? task.waves;
  return need && need > 1 ? `  0/${need}` : "";
};

export function pickTask(anchor: HTMLElement, state: BookState, current?: Task): Promise<Task | null> {
  return openPicker(anchor, state, {
    title: "What should the player do?", kinds: TASK_KINDS, grouped: true, empty: emptyTask, line: taskLine, noun: "task",
    sentence: (t, s) => taskSentence(t, namer(s)) + countSuffix(t),
  }, current);
}

export function pickReward(anchor: HTMLElement, state: BookState, current?: Reward): Promise<Reward | null> {
  return openPicker(anchor, state, {
    title: "What do they get?", kinds: REWARD_KINDS, grouped: false, empty: emptyReward, line: rewardLine, noun: "reward",
    sentence: (r, s) => rewardSentence(r, namer(s)),
  }, current);
}
