package dev.aof.questqueen.client;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The PNGs the editor bridge may hand the browser ({@code GET /api/texture?id=}): the player's own font sheet,
 * description pictures and chapter backgrounds. Only a lowercase {@code ns:path} under {@code textures/} or
 * {@code font/} ending in {@code .png} gets through, with no {@code ..}, {@code .} or empty segment, so the route can
 * never be walked to another kind of resource.
 */
public final class TexturePath {
    static final int MAX_ID = 256;
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int READ_TIMEOUT_SECONDS = 5;

    private TexturePath() {
    }

    public static Optional<ResourceLocation> parse(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID || id.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        int colon = id.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        String path = id.substring(colon + 1);
        if (!(path.startsWith("textures/") || path.startsWith("font/")) || !path.endsWith(".png")) {
            return Optional.empty();
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.empty();
            }
        }
        // tryParse refuses upper case and every character a resource location may not hold.
        return Optional.ofNullable(ResourceLocation.tryParse(id));
    }

    /** The resource's bytes, or empty when it is absent, too big or the client cannot answer in time. */
    public static Optional<byte[]> read(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // The resource manager is swapped on reload; read it on the client thread like ItemIconPng renders.
            return minecraft.isSameThread() ? readNow(minecraft, location)
                    : minecraft.submit(() -> readNow(minecraft, location)).get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> readNow(Minecraft minecraft, ResourceLocation location) {
        var resource = minecraft.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = resource.get().open()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            return bytes.length > MAX_BYTES ? Optional.empty() : Optional.of(bytes);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
