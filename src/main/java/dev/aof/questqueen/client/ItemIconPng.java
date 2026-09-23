package dev.aof.questqueen.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemIconPng {
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final byte[] EMPTY = emptyPng();

    private ItemIconPng() {
    }

    public static byte[] pngFor(String id) {
        if (id == null || id.isBlank()) {
            return EMPTY;
        }
        return CACHE.computeIfAbsent(id, ItemIconPng::render);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static byte[] render(String id) {
        try {
            ResourceLocation location = ResourceLocation.parse(id);
            Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
            if (item == null || item == Items.AIR) {
                return EMPTY;
            }
            Minecraft minecraft = Minecraft.getInstance();
            ItemStack stack = new ItemStack(item);
            BakedModel model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null) {
                return EMPTY;
            }
            NativeImage nativeImage = sprite.contents().getOriginalImage();
            int width = Math.min(sprite.contents().width(), nativeImage.getWidth());
            int height = Math.min(sprite.contents().height(), nativeImage.getHeight());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = nativeImage.getPixelRGBA(x, y);
                    int a = (rgba >> 24) & 0xFF;
                    int b = (rgba >> 16) & 0xFF;
                    int g = (rgba >> 8) & 0xFF;
                    int r = rgba & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    private static byte[] emptyPng() {
        try {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception exception) {
            return new byte[0];
        }
    }
}
