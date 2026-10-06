package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.map.MapPaletteSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the current API map palette to map-palette.txt (fallback for future API versions). */
public final class PaletteDump {

    private PaletteDump() {
    }

    public static void main(String[] args) throws IOException {
        MapPaletteSource.Palette p = MapPaletteSource.load();
        Path out = Path.of(args[0]);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# Minecraft map palette: <map byte> <rgb hex>. Generated from the Paper API.");
            for (int i = 0; i < p.rgb().length; i++) {
                w.printf("%d %06x%n", p.ids()[i] & 0xFF, p.rgb()[i]);
            }
        }
        System.out.println("Wrote " + p.rgb().length + " palette colors to " + out);
    }
}
