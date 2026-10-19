// Text edited in place on the book: click, type, Enter (or leaving the field) keeps it, Esc puts it back.
// The element is marked data-editing while focused so a redraw never replaces it mid-word.

export interface InlineOptions {
  value: string;
  onCommit: (value: string) => void;
  /** An empty result reverts instead of committing (titles). */
  allowEmpty?: boolean;
  label: string;
}

export function inlineText(el: HTMLElement, opts: InlineOptions): void {
  let cancelled = false;
  el.textContent = opts.value;
  el.contentEditable = "true";
  el.spellcheck = true;
  el.setAttribute("role", "textbox");
  el.setAttribute("aria-label", opts.label);
  el.classList.add("editable");
  const commit = () => {
    delete el.dataset.editing;
    if (cancelled) {
      cancelled = false;
      el.textContent = opts.value;
      return;
    }
    const text = (el.textContent ?? "").replace(/\s+/g, " ").trim();
    if (!text && !opts.allowEmpty) {
      el.textContent = opts.value;
      return;
    }
    if (text !== opts.value) opts.onCommit(text);
  };
  el.addEventListener("focus", () => { el.dataset.editing = "1"; });
  el.addEventListener("blur", commit);
  el.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      commit();
      el.blur();
    } else if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      cancelled = true;
      commit();
      el.blur();
    }
  });
  // Plain text only: a paste of formatted text keeps just its words.
  el.addEventListener("paste", (event) => {
    event.preventDefault();
    const text = event.clipboardData?.getData("text/plain") ?? "";
    document.execCommand?.("insertText", false, text.replace(/\s+/g, " "));
  });
}
