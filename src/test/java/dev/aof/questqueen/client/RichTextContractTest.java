package dev.aof.questqueen.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game's description parser and the web editor's (editor/src/richtext.ts) must read every description the same
 * way, or the editor's preview would lie. Both run the same cases, in src/test/resources/richtext-cases.json.
 */
class RichTextContractTest {
    private static JsonObject text(RichText.Text t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t.text());
        o.addProperty("c", t.color() & 0xFFFFFFFFL);
        o.addProperty("s", t.size());
        o.addProperty("b", t.bold());
        o.addProperty("i", t.italic());
        o.addProperty("u", t.underline());
        return o;
    }

    static JsonArray shape(String source) {
        JsonArray out = new JsonArray();
        for (RichText.Block block : RichText.parse(source)) {
            switch (block) {
                case RichText.Gap ignored -> out.add("gap");
                case RichText.Rule ignored -> out.add("rule");
                case RichText.Image image -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("img", image.texture().toString());
                    o.addProperty("cap", image.caption());
                    out.add(o);
                }
                case RichText.Paragraph p -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("h", p.heading().name().toLowerCase());
                    o.addProperty("bullet", p.bullet());
                    JsonArray in = new JsonArray();
                    for (RichText.Inline inline : p.inlines()) {
                        switch (inline) {
                            case RichText.Text t -> in.add(text(t));
                            case RichText.Item item -> {
                                JsonObject i = new JsonObject();
                                i.addProperty("item", item.id().toString());
                                i.addProperty("n", item.count());
                                in.add(i);
                            }
                            case RichText.Glyph glyph -> {
                                JsonObject g = new JsonObject();
                                g.addProperty("glyph", glyph.id());
                                g.addProperty("c", glyph.color() & 0xFFFFFFFFL);
                                in.add(g);
                            }
                        }
                    }
                    o.add("in", in);
                    out.add(o);
                }
            }
        }
        return out;
    }

    @Test
    void theGameAndTheEditorParseEveryCaseAlike() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/richtext-cases.json")) {
            assertTrue(in != null, "richtext-cases.json is on the test classpath");
            JsonArray cases = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            assertTrue(cases.size() > 20, "the shared case list is not empty");
            for (JsonElement element : cases) {
                JsonObject c = element.getAsJsonObject();
                String source = c.get("source").getAsString();
                assertEquals(c.get("shape"), shape(source), "shape of " + c.get("source"));
                assertEquals(c.get("plain").getAsString(), RichText.plain(source), "plain text of " + c.get("source"));
            }
        }
    }
}
