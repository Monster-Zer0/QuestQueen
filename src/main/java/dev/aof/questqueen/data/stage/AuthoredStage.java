package dev.aof.questqueen.data.stage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.aof.questqueen.compat.Stages;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A stage as the web editor writes it: what it is, how players get it, and what it locks until then. Quest Queen
 * keeps this shape at {@code data/<ns>/questqueen/stages/} and turns it into StageLock's own definition and
 * restriction files ({@link StageLockFiles}). The shape is deliberately small and whitelisted: the browser never
 * hands raw StageLock JSON to the server.
 */
public record AuthoredStage(
        String id,
        String name,
        String description,
        String icon,
        String scope,
        boolean message,
        List<String> requires,
        List<String> advancements,
        List<Lock> locks
) {
    public static final Set<String> SCOPES = Set.of("player", "team", "global");
    public static final Set<String> KINDS = Set.of("item", "block", "mod", "dimension", "entity");
    static final int MAX_LOCKS = 64;
    static final int MAX_TARGETS = 512;
    static final int MAX_TEXT = 1024;

    /** An id, a {@code #tag} or an {@code @modid}: the selectors StageLock reads. */
    private static final Pattern TARGET = Pattern.compile("^[#@]?[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");
    private static final Pattern RESOURCE = Pattern.compile("^([a-z0-9_.-]+:)?[a-z0-9_./-]+$");

    private static final Codec<String> STAGE_ID = Codec.STRING.comapFlatMap(
            raw -> Stages.validId(raw)
                    ? DataResult.success(raw.trim().toLowerCase(Locale.ROOT))
                    : DataResult.error(() -> "not a stage id: " + raw),
            id -> id);
    private static final Codec<String> RESOURCE_ID = Codec.STRING.comapFlatMap(
            raw -> RESOURCE.matcher(raw).matches() ? DataResult.success(raw) : DataResult.error(() -> "not an id: " + raw),
            id -> id);
    private static final Codec<String> TEXT = Codec.STRING.comapFlatMap(
            raw -> raw.length() <= MAX_TEXT ? DataResult.success(raw) : DataResult.error(() -> "text longer than " + MAX_TEXT),
            text -> text);

    /** One "until then, players can't…" group: a kind, what it covers, and plain on/off toggles. */
    public record Lock(String kind, List<String> targets, Map<String, Boolean> flags, String message) {
        private static final Codec<String> KIND = Codec.STRING.comapFlatMap(
                raw -> KINDS.contains(raw) ? DataResult.success(raw) : DataResult.error(() -> "unknown lock kind: " + raw),
                kind -> kind);
        private static final Codec<String> TARGET_ID = Codec.STRING.comapFlatMap(
                raw -> TARGET.matcher(raw).matches() ? DataResult.success(raw) : DataResult.error(() -> "not a lock target: " + raw),
                target -> target);

        public static final Codec<Lock> CODEC = RecordCodecBuilder.create(i -> i.group(
                KIND.fieldOf("kind").forGetter(Lock::kind),
                TARGET_ID.sizeLimitedListOf(MAX_TARGETS).optionalFieldOf("targets", List.of()).forGetter(Lock::targets),
                Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("flags", Map.of()).forGetter(Lock::flags),
                TEXT.optionalFieldOf("message", "").forGetter(Lock::message)
        ).apply(i, Lock::new));

        /** A toggle's value, or its default when the editor left it out. */
        public boolean flag(String key, boolean fallback) {
            Boolean value = flags.get(key);
            return value == null ? fallback : value;
        }
    }

    private static final Codec<String> SCOPE = Codec.STRING.comapFlatMap(
            raw -> SCOPES.contains(raw) ? DataResult.success(raw) : DataResult.error(() -> "unknown scope: " + raw),
            scope -> scope);

    public static final Codec<AuthoredStage> CODEC = RecordCodecBuilder.<AuthoredStage>create(i -> i.group(
            STAGE_ID.fieldOf("id").forGetter(AuthoredStage::id),
            TEXT.optionalFieldOf("name", "").forGetter(AuthoredStage::name),
            TEXT.optionalFieldOf("description", "").forGetter(AuthoredStage::description),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(AuthoredStage::icon),
            SCOPE.optionalFieldOf("scope", "player").forGetter(AuthoredStage::scope),
            Codec.BOOL.optionalFieldOf("message", true).forGetter(AuthoredStage::message),
            STAGE_ID.sizeLimitedListOf(MAX_LOCKS).optionalFieldOf("requires", List.of()).forGetter(AuthoredStage::requires),
            RESOURCE_ID.sizeLimitedListOf(MAX_LOCKS).optionalFieldOf("advancements", List.of()).forGetter(AuthoredStage::advancements),
            Lock.CODEC.sizeLimitedListOf(MAX_LOCKS).optionalFieldOf("locks", List.of()).forGetter(AuthoredStage::locks)
    ).apply(i, AuthoredStage::new)).validate(AuthoredStage::check);

    private static DataResult<AuthoredStage> check(AuthoredStage stage) {
        if (!stage.icon.isEmpty() && !RESOURCE.matcher(stage.icon).matches()) {
            return DataResult.error(() -> "icon is not an item id: " + stage.icon);
        }
        if (stage.requires.contains(stage.id)) {
            return DataResult.error(() -> "stage " + stage.id + " cannot require itself");
        }
        return DataResult.success(stage);
    }
}
