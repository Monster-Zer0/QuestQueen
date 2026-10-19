// One in-page dialog for the whole editor, in place of the browser's alert / confirm / prompt. It takes focus,
// keeps Tab inside, Esc cancels, Enter takes the default button, and focus goes back where it was on close.
import { h } from "./dom";

let host: HTMLElement | null = null;

/** Where dialogs open (the shell's pop-up layer); document.body until set. */
export function setDialogHost(el: HTMLElement): void {
  host = el;
}

interface Button<T> {
  label: string;
  hint?: string;
  value: T;
  primary?: boolean;
  danger?: boolean;
}

function open<T>(title: string, body: string | null, buttons: Button<T>[], cancel: T, layout: "row" | "list"): Promise<T> {
  return new Promise((resolve) => {
    const before = document.activeElement as HTMLElement | null;
    const titleId = `dlg-${Math.random().toString(36).slice(2)}`;
    const close = (value: T) => {
      backdrop.remove();
      before?.focus?.();
      resolve(value);
    };
    const buttonEls = buttons.map((b) =>
      h("button", { type: "button", class: `dlg-btn${b.primary ? " primary" : ""}${b.danger ? " danger" : ""}`, onclick: () => close(b.value) },
        b.label, b.hint ? h("small", {}, b.hint) : null));
    const panel = h("div", { class: `dlg dlg-${layout}`, role: "dialog", "aria-modal": "true", "aria-labelledby": titleId },
      h("h2", { id: titleId, class: "dlg-title" }, title),
      body ? h("p", { class: "dlg-body" }, body) : null,
      h("div", { class: "dlg-buttons" }, ...buttonEls));
    const backdrop = h("div", { class: "dlg-backdrop" }, panel);
    backdrop.addEventListener("mousedown", (event) => { if (event.target === backdrop) close(cancel); });
    panel.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        close(cancel);
      } else if (event.key === "Enter" && !(event.target as HTMLElement).matches("button")) {
        event.preventDefault();
        const primary = buttons.findIndex((b) => b.primary);
        close(buttons[primary >= 0 ? primary : 0].value);
      } else if (event.key === "Tab") {
        const first = buttonEls[0];
        const last = buttonEls[buttonEls.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    });
    (host ?? document.body).append(backdrop);
    // Focus the panel itself first so Enter means "the default button" rather than whichever button has focus.
    panel.tabIndex = -1;
    panel.focus();
  });
}

/** Yes/no question. Resolves true for OK, false for Cancel or Esc. */
export function ask(text: string, opts: { ok?: string; cancel?: string; danger?: boolean; title?: string } = {}): Promise<boolean> {
  return open(opts.title ?? text, opts.title ? text : null, [
    { label: opts.cancel ?? "Cancel", value: false },
    { label: opts.ok ?? "OK", value: true, primary: true, danger: opts.danger },
  ], false, "row");
}

/** A message with one button. */
export function tell(text: string, opts: { title?: string; ok?: string } = {}): Promise<void> {
  return open(opts.title ?? text, opts.title ? text : null, [{ label: opts.ok ?? "OK", value: undefined, primary: true }], undefined, "row");
}

/** Pick one of several options (each with an optional hint line). Resolves its value, or null on Esc. */
export function choose<T>(title: string, options: { label: string; hint?: string; value: T; danger?: boolean }[], text?: string): Promise<T | null> {
  return open<T | null>(title, text ?? null, options.map((o) => ({ ...o })), null, "list");
}
