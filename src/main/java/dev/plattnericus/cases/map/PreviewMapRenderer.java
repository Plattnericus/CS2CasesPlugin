package dev.plattnericus.cases.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/**
 * Draws a precomputed 128x128 image onto its map. The image arrives as map color bytes and is
 * written with exact palette colors, so the server maps every pixel back to the same byte.
 * Redraws only when the image changes.
 */
public final class PreviewMapRenderer extends MapRenderer {

    private final Color[] table;
    private volatile byte[] pixels;
    private byte[] drawn;

    public PreviewMapRenderer(Color[] table) {
        super(false);
        this.table = table;
    }

    public void setPixels(byte[] pixels) {
        this.pixels = pixels;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        byte[] current = pixels;
        if (current == null || current == drawn) {
            return;
        }
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixelColor(x, y, table[current[y * 128 + x] & 0xFF]);
            }
        }
        drawn = current;
    }
}
