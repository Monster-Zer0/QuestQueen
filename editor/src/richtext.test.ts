import { describe, expect, it } from "vitest";
import { descriptionProblems, parseRich, plainText, richToHtml, toShape, validItemId, validTexture } from "./richtext";
import { validate } from "./validate";
import type { Chapter } from "./types";
import cases from "../../src/test/resources/richtext-cases.json";

// The game's RichText.java reads these same cases (src/test/java/.../RichTextContractTest.java).
const CASES = cases as { source: string; shape: unknown; plain: string }[];

describe("description markup", () => {
  it("reads every shared case the way the game does", () => {
    expect(CASES.length).toBeGreaterThan(20);
    for (const c of CASES) {
      expect(toShape(parseRich(c.source)), JSON.stringify(c.source)).toEqual(c.shape);
      expect(plainText(c.source), JSON.stringify(c.source)).toEqual(c.plain);
    }
  });

  it("checks texture paths and item ids", () => {
    expect(validTexture("mypack:textures/quest/castle.png")).toBe(true);
    expect(validTexture("mypack:castle.png")).toBe(false);
    expect(validTexture("mypack:textures/castle")).toBe(false);
    expect(validTexture("https://example.com/a.png")).toBe(false);
    expect(validItemId("minecraft:diamond")).toBe(true);
    expect(validItemId("diamond")).toBe(true);
    expect(validItemId("Not An Id")).toBe(false);
    expect(validItemId("")).toBe(false);
  });

  it("flags markup that would show up as literal text", () => {
    expect(descriptionProblems("just words")).toEqual([]);
    expect(descriptionProblems("![x](mypack:textures/a.png)\n{item:minecraft:dirt x2} {glyph:sword}")).toEqual([]);
    const bad = descriptionProblems("![x](mypack:a.jpg)\n{item:Not An Id}\n{glyph:nope}");
    expect(bad).toHaveLength(3);
    expect(bad[0]).toContain("not a texture path");
    expect(bad[1]).toContain("not an item");
    expect(bad[2]).toContain("not a known glyph");
    expect(descriptionProblems("\\{item:Not An Id}")).toEqual([]);
  });

  it("renders a preview with formatting, icons and a picture frame", () => {
    const html = richToHtml(
      "# Title\n**bold** {#FF8800}orange{/#}\n- {item:minecraft:diamond x3}\n---\n![A castle](mypack:textures/a.png)",
      { iconUrl: (id) => `/icon?id=${id}` },
    );
    expect(html).toContain("desc-title");
    expect(html).toContain("font-weight:700");
    expect(html).toContain("color:#ff8800");
    expect(html).toContain("desc-bullet");
    expect(html).toContain('src="/icon?id=minecraft:diamond"');
    expect(html).toContain("x3");
    expect(html).toContain("<hr />");
    expect(html).toContain("mypack:textures/a.png");
    expect(html).toContain("A castle");
  });

  it("escapes HTML in descriptions", () => {
    const html = richToHtml('<img src=x onerror=alert(1)> & "q"');
    expect(html).not.toContain("<img");
    expect(html).toContain("&lt;img");
    expect(html).toContain("&amp;");
  });

  it("reports bad description markup as a problem before saving", () => {
    const chapter: Chapter = {
      id: "p:c", title: "C", tiles: [{
        id: "q", pos: { x: 0, y: 0 }, title: "Q", description: "![x](mypack:a.jpg)",
        tasks: [{ type: "checkmark" }],
      }], links: [],
    } as unknown as Chapter;
    const errors = validate(chapter);
    expect(errors.some((e) => e.includes("description") && e.includes("not a texture path"))).toBe(true);
  });
});
