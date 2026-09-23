package dev.aof.questqueen.data;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * Full chapter record. {@code theme} / {@code background} / {@code intro} are optional for pack authors.
 */
public record Chapter(
        ResourceLocation id,
        String title,
        java.util.List<Tile> tiles,
        java.util.List<Link> links,
        int gridWidth,
        int gridHeight,
        Optional<ResourceLocation> parent,
        int order,
        Optional<Icon> icon,
        Gate unlock,
        boolean hideUntilUnlocked,
        String theme,
        Optional<ChapterBackground> background,
        Optional<ChapterIntro> intro
) {
    public static final String DEFAULT_THEME = "midnight_royalty";

    public static final com.mojang.serialization.Codec<Chapter> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Chapter::id),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("title", "").forGetter(Chapter::title),
            Tile.CODEC.listOf().optionalFieldOf("tiles", java.util.List.of()).forGetter(Chapter::tiles),
            Link.CODEC.listOf().optionalFieldOf("links", java.util.List.of()).forGetter(Chapter::links),
            com.mojang.serialization.Codec.INT.optionalFieldOf("grid_width", GridSize.DEFAULT_WIDTH).forGetter(Chapter::gridWidth),
            com.mojang.serialization.Codec.INT.optionalFieldOf("grid_height", GridSize.DEFAULT_HEIGHT).forGetter(Chapter::gridHeight),
            ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(Chapter::parent),
            com.mojang.serialization.Codec.INT.optionalFieldOf("order", 0).forGetter(Chapter::order),
            Icon.CODEC.optionalFieldOf("icon").forGetter(Chapter::icon),
            Gate.CODEC.optionalFieldOf("unlock", Gate.AND).forGetter(Chapter::unlock),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("hide_until_unlocked", false).forGetter(Chapter::hideUntilUnlocked),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("theme", DEFAULT_THEME).forGetter(Chapter::theme),
            ChapterBackground.CODEC.optionalFieldOf("background").forGetter(Chapter::background),
            ChapterIntro.CODEC.optionalFieldOf("intro").forGetter(Chapter::intro)
    ).apply(instance, Chapter::new));

    public Chapter {
        tiles = java.util.List.copyOf(tiles);
        links = java.util.List.copyOf(links);
        gridWidth = GridSize.clampWidth(gridWidth);
        gridHeight = GridSize.clampHeight(gridHeight);
        unlock = unlock == null ? Gate.AND : unlock;
        theme = theme == null || theme.isBlank() ? DEFAULT_THEME : theme;
        background = background == null ? Optional.empty() : background;
        intro = intro == null ? Optional.empty() : intro;
    }

    public Chapter(ResourceLocation id, String title, java.util.List<Tile> tiles, java.util.List<Link> links) {
        this(id, title, tiles, links, GridSize.DEFAULT_WIDTH, GridSize.DEFAULT_HEIGHT,
                Optional.empty(), 0, Optional.empty(), Gate.AND, false, DEFAULT_THEME, Optional.empty(), Optional.empty());
    }

    public Chapter(ResourceLocation id, String title, java.util.List<Tile> tiles, java.util.List<Link> links, int gridWidth, int gridHeight) {
        this(id, title, tiles, links, gridWidth, gridHeight, Optional.empty(), 0, Optional.empty(), Gate.AND, false, DEFAULT_THEME, Optional.empty(), Optional.empty());
    }

    public Optional<Tile> tile(String tileId) {
        return tiles.stream().filter(tile -> tile.id().equals(tileId)).findFirst();
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < gridWidth && y < gridHeight;
    }

    public Chapter withTiles(java.util.List<Tile> newTiles) {
        return copy(newTiles, links, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, theme, background, intro);
    }

    public Chapter withLinks(java.util.List<Link> newLinks) {
        return copy(tiles, newLinks, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, theme, background, intro);
    }

    public Chapter withGridSize(int width, int height) {
        return copy(tiles, links, width, height, parent, order, icon, unlock, hideUntilUnlocked, theme, background, intro);
    }

    public Chapter withMeta(Optional<ResourceLocation> newParent, int newOrder, Optional<Icon> newIcon, Gate newUnlock) {
        return copy(tiles, links, gridWidth, gridHeight, newParent, newOrder, newIcon, newUnlock, hideUntilUnlocked, theme, background, intro);
    }

    public Chapter withHideUntilUnlocked(boolean hide) {
        return copy(tiles, links, gridWidth, gridHeight, parent, order, icon, unlock, hide, theme, background, intro);
    }

    public Chapter withTitle(String newTitle) {
        return new Chapter(id, newTitle, tiles, links, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, theme, background, intro);
    }

    public Chapter withTheme(String newTheme, Optional<ChapterBackground> newBackground) {
        return copy(tiles, links, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, newTheme, newBackground, intro);
    }

    public Chapter withIntro(Optional<ChapterIntro> newIntro) {
        return copy(tiles, links, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, theme, background, newIntro);
    }

    private Chapter copy(
            java.util.List<Tile> newTiles,
            java.util.List<Link> newLinks,
            int width,
            int height,
            Optional<ResourceLocation> newParent,
            int newOrder,
            Optional<Icon> newIcon,
            Gate newUnlock,
            boolean hide,
            String newTheme,
            Optional<ChapterBackground> newBackground,
            Optional<ChapterIntro> newIntro
    ) {
        return new Chapter(id, title, newTiles, newLinks, width, height, newParent, newOrder, newIcon, newUnlock, hide, newTheme, newBackground, newIntro);
    }

    public boolean hasLink(String from, String to) {
        return links.stream().anyMatch(link -> link.from().equals(from) && link.to().equals(to));
    }

    public Optional<Link> link(String from, String to) {
        return links.stream().filter(link -> link.from().equals(from) && link.to().equals(to)).findFirst();
    }

    public Chapter upsertLink(String from, String to, GateOp op) {
        java.util.List<Link> next = new java.util.ArrayList<>();
        boolean replaced = false;
        for (Link link : links) {
            if (link.from().equals(from) && link.to().equals(to)) {
                next.add(link.withOp(op));
                replaced = true;
            } else if (link.to().equals(to)) {
                next.add(link.withOp(op));
            } else {
                next.add(link);
            }
        }
        if (!replaced) {
            next.add(new Link(from, to, Gate.of(op)));
        }
        return withLinks(next);
    }

    public Chapter removeLink(String from, String to) {
        return withLinks(links.stream()
                .filter(link -> !(link.from().equals(from) && link.to().equals(to)))
                .toList());
    }

    public Chapter setLinkOp(String from, String to, GateOp op) {
        return upsertLink(from, to, op);
    }

    public Chapter replaceTile(Tile replacement) {
        java.util.List<Tile> next = new java.util.ArrayList<>();
        boolean found = false;
        for (Tile tile : tiles) {
            if (tile.id().equals(replacement.id())) {
                next.add(replacement);
                found = true;
            } else {
                next.add(tile);
            }
        }
        if (!found) {
            next.add(replacement);
        }
        return withTiles(next);
    }

    public Chapter removeTile(String tileId) {
        java.util.List<Tile> nextTiles = tiles.stream().filter(tile -> !tile.id().equals(tileId)).toList();
        java.util.List<Link> nextLinks = links.stream()
                .filter(link -> !link.from().equals(tileId) && !link.to().equals(tileId))
                .toList();
        return copy(nextTiles, nextLinks, gridWidth, gridHeight, parent, order, icon, unlock, hideUntilUnlocked, theme, background, intro);
    }

    public static Chapter blank(ResourceLocation id) {
        return new Chapter(id, "New chapter", java.util.List.of(Tile.blank("start", 1, 1)), java.util.List.of());
    }
}
