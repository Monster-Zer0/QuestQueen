package dev.aof.questqueen.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoChaptersTest {
    private static Chapter ch(String id) {
        return Chapter.blank(ResourceLocation.parse(id));
    }

    private static Set<String> ids(DemoChapters.FilterResult result) {
        return DemoChapters.idsOf(result.chapters());
    }

    @Test
    void autoWithoutCustomKeepsShowcaseAndDropsDev() {
        List<Chapter> loaded = List.of(
                ch("questqueen:showcase"),
                ch("questqueen:showcase_stage"),
                ch("questqueen:test"),
                ch("questqueen:blank")
        );
        DemoChapters.FilterResult withPs = DemoChapters.filter(loaded, DemoChapters.Mode.AUTO, false, true);
        assertEquals(Set.of("questqueen:showcase", "questqueen:showcase_stage"), ids(withPs));
        DemoChapters.FilterResult noPs = DemoChapters.filter(loaded, DemoChapters.Mode.AUTO, false, false);
        assertEquals(Set.of("questqueen:showcase"), ids(noPs));
    }

    @Test
    void autoWithCustomDropsAllBuiltins() {
        List<Chapter> loaded = List.of(
                ch("questqueen:showcase"),
                ch("questqueen:test"),
                ch("skylore:act_i")
        );
        DemoChapters.FilterResult result = DemoChapters.filter(loaded, DemoChapters.Mode.AUTO, false, true);
        assertEquals(1, result.customCount());
        assertEquals(Set.of("skylore:act_i"), ids(result));
        assertEquals(2, result.dropped());
    }

    @Test
    void alwaysKeepsShowcaseBesidePack() {
        List<Chapter> loaded = List.of(ch("questqueen:showcase"), ch("skylore:crash"));
        DemoChapters.FilterResult result = DemoChapters.filter(loaded, DemoChapters.Mode.ALWAYS, false, true);
        assertEquals(Set.of("questqueen:showcase", "skylore:crash"), ids(result));
    }

    @Test
    void neverDropsPlayerDemos() {
        List<Chapter> loaded = List.of(ch("questqueen:showcase"), ch("skylore:crash"));
        DemoChapters.FilterResult result = DemoChapters.filter(loaded, DemoChapters.Mode.NEVER, false, true);
        assertEquals(Set.of("skylore:crash"), ids(result));
    }

    @Test
    void includeDevChaptersAddsTestIds() {
        List<Chapter> loaded = List.of(
                ch("questqueen:showcase"),
                ch("questqueen:test"),
                ch("questqueen:test_bonus"),
                ch("skylore:act_i")
        );
        DemoChapters.FilterResult autoDev = DemoChapters.filter(loaded, DemoChapters.Mode.AUTO, true, true);
        assertEquals(Set.of("questqueen:test", "questqueen:test_bonus", "skylore:act_i"), ids(autoDev));
        DemoChapters.FilterResult aloneDev = DemoChapters.filter(
                List.of(ch("questqueen:showcase"), ch("questqueen:test")),
                DemoChapters.Mode.AUTO, true, true);
        assertEquals(Set.of("questqueen:showcase", "questqueen:test"), ids(aloneDev));
    }

    @Test
    void pickChromePrefersPackNamespaceWhenCustom() {
        BookChrome jar = new BookChrome("CHAPTERS", -7692646, 1f, false, true);
        BookChrome pack = new BookChrome("Skylore", -7692646, 1f, false, false);
        BookChrome chosen = DemoChapters.pickChrome(List.of(
                new DemoChapters.ChromeFile(ResourceLocation.parse("questqueen:questqueen/book.json"), jar),
                new DemoChapters.ChromeFile(ResourceLocation.parse("skylore:questqueen/book.json"), pack)
        ), true);
        assertEquals("Skylore", chosen.sidebarTitle());
        assertFalse(chosen.titleUppercase());
        BookChrome fallback = DemoChapters.pickChrome(List.of(
                new DemoChapters.ChromeFile(ResourceLocation.parse("questqueen:questqueen/book.json"), jar)
        ), false);
        assertEquals("CHAPTERS", fallback.sidebarTitle());
        assertEquals("skylore", DemoChapters.pickChromeNamespace(List.of(
                new DemoChapters.ChromeFile(ResourceLocation.parse("questqueen:questqueen/book.json"), jar),
                new DemoChapters.ChromeFile(ResourceLocation.parse("skylore:questqueen/book.json"), pack)
        ), true));
    }

    @Test
    void pickChromeFollowsPackRankNotListOrder() {
        BookChrome jar = new BookChrome("CHAPTERS", -7692646, 1f, false, true);
        BookChrome low = new BookChrome("Low", -7692646, 1f, false, false);
        BookChrome high = new BookChrome("High", -7692646, 1f, false, false);
        List<DemoChapters.ChromeFile> files = List.of(
                new DemoChapters.ChromeFile(ResourceLocation.parse("skylore:questqueen/book.json"), low, "file/skylore", 2),
                new DemoChapters.ChromeFile(ResourceLocation.parse("questqueen:questqueen/book.json"), jar, DemoChapters.JAR_PACK, 50),
                new DemoChapters.ChromeFile(ResourceLocation.parse("other:questqueen/book.json"), high, "file/other", 9)
        );
        assertEquals("High", DemoChapters.pickChrome(files, true).sidebarTitle());
        assertEquals("other", DemoChapters.pickChromeNamespace(files, true));
        assertEquals("CHAPTERS", DemoChapters.pickChrome(files, false).sidebarTitle());
    }

    @Test
    void filterScrollsKeepsBriefWithShowcaseAndDropsDevNotes() {
        List<Scroll> loaded = List.of(
                new Scroll(ResourceLocation.parse("questqueen:showcase_brief"), "Brief", "text"),
                new Scroll(ResourceLocation.parse("questqueen:chest_note"), "Chest", "text"),
                new Scroll(ResourceLocation.parse("questqueen:test_note"), "Test", "text"),
                new Scroll(ResourceLocation.parse("skylore:letter"), "Letter", "text")
        );
        assertEquals(Set.of("questqueen:showcase_brief", "skylore:letter"),
                scrollIds(DemoChapters.filterScrolls(loaded, true, false)));
        assertEquals(Set.of("skylore:letter"),
                scrollIds(DemoChapters.filterScrolls(loaded, false, false)));
        assertEquals(Set.of("questqueen:chest_note", "questqueen:test_note", "skylore:letter"),
                scrollIds(DemoChapters.filterScrolls(loaded, false, true)));
    }

    private static Set<String> scrollIds(List<Scroll> scrolls) {
        Set<String> ids = new LinkedHashSet<>();
        for (Scroll scroll : scrolls) {
            ids.add(scroll.id().toString());
        }
        return ids;
    }

    @Test
    void modeParse() {
        assertEquals(DemoChapters.Mode.AUTO, DemoChapters.Mode.parse(""));
        assertEquals(DemoChapters.Mode.ALWAYS, DemoChapters.Mode.parse("ALWAYS"));
        assertEquals(DemoChapters.Mode.NEVER, DemoChapters.Mode.parse("never"));
        assertTrue(DemoChapters.isCustom(ch("skylore:act_i")));
        assertFalse(DemoChapters.isCustom(ch("questqueen:showcase")));
    }
}
