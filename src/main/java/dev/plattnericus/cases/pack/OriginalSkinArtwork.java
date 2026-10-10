package dev.plattnericus.cases.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Durable, unmodified artwork supplied with the server's original Fusion pack. */
final class OriginalSkinArtwork {
    private final Map<String, byte[]> images;

    OriginalSkinArtwork() throws IOException {
        var entries = new HashMap<String, byte[]>();
        try (var input = OriginalSkinArtwork.class.getResourceAsStream("/original-skins.zip")) {
            if (input == null) throw new IOException("Missing bundled original skin artwork");
            try (var zip = new ZipInputStream(input)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.endsWith(".png") && name.startsWith("assets/mccases/textures/item/"))
                        entries.put(name.substring("assets/mccases/textures/item/".length()), zip.readAllBytes());
                }
            }
        }
        if (entries.isEmpty()) throw new IOException("Bundled original artwork is empty");
        images = Map.copyOf(entries);
    }

    BufferedImage image(String path) throws IOException {
        byte[] bytes = images.get(path + ".png");
        if (bytes == null) return null;
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != 128 || image.getHeight() != 128)
            throw new IOException("Invalid original artwork: " + path);
        return image;
    }
}
