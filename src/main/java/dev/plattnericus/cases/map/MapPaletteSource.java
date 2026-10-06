package dev.plattnericus.cases.map;

import dev.plattnericus.cases.render.MapDither;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The client map palette. It is read from the server API when available; {@code MapPalette#getColor}
 * is scheduled for removal, so it is called reflectively and a bundled copy of the palette
 * ({@code map-palette.txt}) takes over once the method is gone.
 */
public final class MapPaletteSource {

    /** Opaque palette entries: color and the map byte that selects it. */
    public record Palette(int[] rgb, byte[] ids) {

        public MapDither dither(double strength) {
            return new MapDither(rgb, ids, strength);
        }

        /** Color per map byte, for drawing through {@code MapCanvas#setPixelColor}. */
        public Color[] table() {
            Color[] table = new Color[256];
            for (int i = 0; i < rgb.length; i++) {
                table[ids[i] & 0xFF] = new Color(rgb[i]);
            }
            return table;
        }
    }

    private MapPaletteSource() {
    }

    public static Palette load() {
        Palette fromApi = fromApi();
        if (fromApi != null) {
            return fromApi;
        }
        try {
            return fromResource();
        } catch (IOException e) {
            throw new IllegalStateException("map palette unavailable", e);
        }
    }

    private static Palette fromApi() {
        try {
            Method getColor = Class.forName("org.bukkit.map.MapPalette").getMethod("getColor", byte.class);
            List<Integer> rgb = new ArrayList<>();
            List<Byte> ids = new ArrayList<>();
            for (int i = 4; i < 256; i++) {
                Color c;
                try {
                    c = (Color) getColor.invoke(null, (byte) i);
                } catch (ReflectiveOperationException e) {
                    if (e.getCause() instanceof IndexOutOfBoundsException) {
                        break;
                    }
                    throw e;
                }
                if (c != null && c.getAlpha() == 255) {
                    rgb.add(c.getRGB() & 0xFFFFFF);
                    ids.add((byte) i);
                }
            }
            return rgb.isEmpty() ? null : build(rgb, ids);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Palette fromResource() throws IOException {
        List<Integer> rgb = new ArrayList<>();
        List<Byte> ids = new ArrayList<>();
        try (InputStream in = MapPaletteSource.class.getResourceAsStream("/map-palette.txt")) {
            if (in == null) {
                throw new IOException("map-palette.txt missing");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                ids.add((byte) Integer.parseInt(parts[0]));
                rgb.add(Integer.parseInt(parts[1], 16));
            }
        }
        return build(rgb, ids);
    }

    private static Palette build(List<Integer> rgb, List<Byte> ids) {
        int[] colors = rgb.stream().mapToInt(Integer::intValue).toArray();
        byte[] bytes = new byte[ids.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ids.get(i);
        }
        return new Palette(colors, bytes);
    }
}
