package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Q-01: the chapter is selected from an EXPLICIT id, never derived from the tile (Worf D-3).
 *
 * <p>These tests drive the real {@link ClientQuestState#selectChapter(String)} — including the write to the
 * remembered chapter — rather than a copy of its logic.
 */
class ChapterSelectTest {
    private static final ResourceLocation IDENTIFY = ResourceLocation.parse("questqueen:identify");
    private static final ResourceLocation CYBER = ResourceLocation.parse("questqueen:teknari_cybernetics");
    private static final ResourceLocation LOCKED = ResourceLocation.parse("questqueen:ignis_ascent");

    @AfterEach
    void reset() {
        ClientQuestState.pack = QuestPack.empty();
        ClientQuestState.progress = ProgressSnapshot.empty();
        // savedChapterId is deliberately NOT resettable from here — rememberChapter ignores null — so every
        // test that asserts on "the chapter did not change" names its own starting chapter explicitly rather
        // than depending on test order.
    }

    @Test
    void availableChapterSwitchesAndIsRemembered() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(IDENTIFY);

        assertEquals(ClientQuestState.ChapterSelect.SWITCHED, ClientQuestState.selectChapter(CYBER.toString()));
        assertEquals(CYBER, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    @Test
    void selectingTheDisplayedChapterIsAcceptedButNotASwitch() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(CYBER);

        assertEquals(ClientQuestState.ChapterSelect.ALREADY_CURRENT, ClientQuestState.selectChapter(CYBER.toString()));
        assertEquals(CYBER, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /** Acceptance (b): a locked chapter refuses, with no state change. */
    @Test
    void lockedChapterRefusesAndLeavesStateUntouched() {
        packOf(IDENTIFY, CYBER, LOCKED);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(CYBER);

        assertEquals(ClientQuestState.ChapterSelect.LOCKED, ClientQuestState.selectChapter(LOCKED.toString()));
        assertEquals(CYBER, ClientQuestState.rememberedChapter().orElseThrow().id(),
                "a refused chapter must not move the book");
    }

    /** Acceptance (c): an id that is valid but absent from the pack is a no-op, not a crash. */
    @Test
    void unknownChapterIsANoOp() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(IDENTIFY);

        assertEquals(ClientQuestState.ChapterSelect.UNKNOWN,
                ClientQuestState.selectChapter("questqueen:no_such_chapter"));
        assertEquals(IDENTIFY, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /** tryParse, not parse: a malformed id must be a no-op rather than a throw on the client thread. */
    @Test
    void malformedIdIsANoOpNotAThrow() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(IDENTIFY);

        // Rejected by the parser: uppercase, spaces and '!' are all outside [a-z0-9/._-], so tryParse gives null.
        assertEquals(ClientQuestState.ChapterSelect.MALFORMED, ClientQuestState.selectChapter("NOT A VALID ID!!"));
        assertEquals(IDENTIFY, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /**
     * The parser is more permissive than it looks, and the gap matters: {@code isValidPath} only rejects
     * characters outside {@code [a-z0-9/._-]}, so an EMPTY path passes and {@code "skylore:"} builds a real
     * {@code skylore:} rather than returning null. That lands in UNKNOWN, not MALFORMED. Pinned here so the
     * distinction is never mistaken for a bug — the invariant that matters is that neither outcome moves the
     * book.
     */
    @Test
    void trailingColonIsAcceptedByTheParserAndMerelyUnknown() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(IDENTIFY);

        ResourceLocation trailing = ResourceLocation.tryParse("skylore:");
        assertNotNull(trailing, "an empty path is accepted by isValidPath, so this parses rather than failing");
        assertEquals("skylore", trailing.getNamespace());
        assertTrue(trailing.getPath().isEmpty());

        assertEquals(ClientQuestState.ChapterSelect.UNKNOWN, ClientQuestState.selectChapter("skylore:"));
        assertEquals(IDENTIFY, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /**
     * A bare path is not malformed either — {@code tryParse} supplies the default namespace, so
     * {@code just_a_path} becomes {@code minecraft:just_a_path} and is simply not in this pack.
     */
    @Test
    void barePathParsesToTheDefaultNamespaceAndIsMerelyUnknown() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(IDENTIFY);

        assertEquals(ClientQuestState.ChapterSelect.UNKNOWN, ClientQuestState.selectChapter("just_a_path"));
        assertEquals(IDENTIFY, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /** Acceptance (d): no chapter requested leaves the remembered chapter alone. */
    @Test
    void noChapterRequestedKeepsTheRememberedChapter() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);
        ClientQuestState.rememberChapter(CYBER);

        assertEquals(ClientQuestState.ChapterSelect.NO_ID, ClientQuestState.selectChapter(""));
        assertEquals(ClientQuestState.ChapterSelect.NO_ID, ClientQuestState.selectChapter(null));
        assertEquals(CYBER, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    /**
     * The regression guard for the rejected design: the pack reuses tile ids across chapters, so "the first
     * chapter that owns this tile" is ambiguous and could open the wrong chapter. Only the explicit id
     * resolves it. Mirrors the real collision measured in the pack (craft_a_scanner in both identify and
     * teknari_cybernetics).
     */
    @Test
    void collidingTileIdsMeanTheChapterCannotBeDerivedFromTheTile() {
        packOf(IDENTIFY, CYBER);
        unlocked(IDENTIFY, CYBER);

        Chapter identify = ClientQuestState.chapter(IDENTIFY).orElseThrow();
        Chapter cyber = ClientQuestState.chapter(CYBER).orElseThrow();
        assertTrue(identify.tile("craft_a_scanner").isPresent());
        assertTrue(cyber.tile("craft_a_scanner").isPresent());
        assertNotEquals(identify.id(), cyber.id());

        // A derived chapter would have to pick one of those two at random. The explicit id does not.
        ClientQuestState.rememberChapter(IDENTIFY);
        assertEquals(ClientQuestState.ChapterSelect.SWITCHED, ClientQuestState.selectChapter(CYBER.toString()));
        assertEquals(CYBER, ClientQuestState.rememberedChapter().orElseThrow().id());
    }

    private static void packOf(ResourceLocation... chapters) {
        List<Chapter> built = new ArrayList<>();
        for (ResourceLocation id : chapters) {
            built.add(new Chapter(id, id.getPath(), List.of(Tile.blank("craft_a_scanner", 0, 0)), List.of()));
        }
        ClientQuestState.pack = new QuestPack(built, List.of());
    }

    private static void unlocked(ResourceLocation... ids) {
        Set<String> open = new HashSet<>();
        for (ResourceLocation id : ids) {
            open.add(id.toString());
        }
        ClientQuestState.progress = new ProgressSnapshot(
                "team",
                false,
                Map.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                open,
                Set.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
