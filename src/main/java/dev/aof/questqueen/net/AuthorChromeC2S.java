package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.BookChrome;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An author's edit to the book chrome (sidebar title, colour, scale, shadow). The book used to call
 * {@code QuestDefinitions.putChrome} straight from the render thread: that raced the server thread in
 * singleplayer and did nothing at all on a dedicated server. The server now applies it, gated like
 * {@link AuthorSaveC2S}.
 */
public record AuthorChromeC2S(String sidebarTitle, int titleColor, float titleScale, boolean titleShadow,
                              boolean titleUppercase) implements CustomPacketPayload {
    /** Longest sidebar title accepted; the sidebar has room for far less, this only bounds the packet. */
    public static final int MAX_TITLE = 64;

    public static final CustomPacketPayload.Type<AuthorChromeC2S> TYPE =
            new CustomPacketPayload.Type<>(QuestQueen.id("author_chrome"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorChromeC2S> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_TITLE), AuthorChromeC2S::sidebarTitle,
            ByteBufCodecs.INT, AuthorChromeC2S::titleColor,
            ByteBufCodecs.FLOAT, AuthorChromeC2S::titleScale,
            ByteBufCodecs.BOOL, AuthorChromeC2S::titleShadow,
            ByteBufCodecs.BOOL, AuthorChromeC2S::titleUppercase,
            AuthorChromeC2S::new
    );

    public static AuthorChromeC2S of(BookChrome chrome) {
        String title = chrome.sidebarTitle();
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
        return new AuthorChromeC2S(title, chrome.titleColor(), chrome.titleScale(), chrome.titleShadow(),
                chrome.titleUppercase());
    }

    /** The BookChrome constructor clamps scale and replaces a blank title, so a crafted packet stays in range. */
    public BookChrome chrome() {
        float scale = Float.isFinite(titleScale) ? titleScale : BookChrome.DEFAULT.titleScale();
        return new BookChrome(sidebarTitle, titleColor, scale, titleShadow, titleUppercase);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
