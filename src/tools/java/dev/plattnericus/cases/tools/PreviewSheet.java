package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.CatalogLoader;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.pattern.PatternClassifier;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.render.ArgbImage;
import dev.plattnericus.cases.render.MapCardComposer;
import dev.plattnericus.cases.render.MapCardStyle;
import dev.plattnericus.cases.render.MapDither;
import dev.plattnericus.cases.render.RenderSettings;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.TextureStore;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Visual check of the render pipeline: renders a contact sheet (full resolution and the exact
 * dithered map output) and prints pattern classification statistics over all seeds.
 */
public final class PreviewSheet {

    private PreviewSheet() {
    }

    public static void main(String[] args) throws Exception {
        File defaults = new File(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        TextureStore textures = new TextureStore(new File(defaults, "textures"));
        CatalogLoader.Result result = new CatalogLoader(new File(defaults, "catalog"), textures).load(1);
        result.problems().forEach(p -> System.out.println("PROBLEM " + p));
        result.notes().forEach(p -> System.out.println("NOTE " + p));
        Catalog catalog = result.catalog();
        System.out.println("skins=" + catalog.skins().size() + " cases=" + catalog.cases().size());
        SkinRenderer renderer = new SkinRenderer(textures, RenderSettings.defaults());
        MapDither dither = mapDither();

        String[][] picks = {
                {"ak47_case_hardened", "661", "0.03"}, {"ak47_case_hardened", "100", "0.03"}, {"karambit_case_hardened", "387", "0.03"},
                {"karambit_doppler", "10", "0.01"}, {"karambit_doppler", "11", "0.01"}, {"butterfly_fade", "12", "0.02"},
                {"m9_bayonet_marble_fade", "40", "0.02"}, {"awp_lightning_strike", "1", "0.02"}, {"ak47_asiimov", "5", "0.25"},
                {"m4a4_dragon_king", "7", "0.10"}, {"awp_hyper_beast", "3", "0.05"}, {"ak47_fire_serpent", "8", "0.20"},
                {"deagle_printstream", "2", "0.04"}, {"usps_kill_confirmed", "4", "0.30"}, {"awp_fever_dream", "9", "0.20"},
                {"karambit_tiger_tooth", "3", "0.01"}, {"bayonet_crimson_web", "3", "0.15"}, {"talon_vanilla", "0", "0.0"},
                {"skeleton_fade", "500", "0.01"}, {"kukri_blue_steel", "7", "0.6"}, {"glock18_water_elemental", "5", "0.08"},
                {"ak47_case_hardened", "661", "0.06"}, {"ak47_case_hardened", "661", "0.25"}, {"ak47_case_hardened", "661", "0.70"},
        };
        int cols = 6;
        int cell = 256;
        int rows = (picks.length + cols - 1) / cols;
        BufferedImage full = new BufferedImage(cols * cell, rows * (cell + 30), BufferedImage.TYPE_INT_RGB);
        BufferedImage maps = new BufferedImage(cols * 128, rows * 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = full.createGraphics();
        g.setColor(new Color(0x16181c));
        g.fillRect(0, 0, full.getWidth(), full.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < picks.length; i++) {
            SkinDefinition skin = catalog.skin(picks[i][0]);
            if (skin == null) {
                System.out.println("missing skin " + picks[i][0]);
                continue;
            }
            int seed = Integer.parseInt(picks[i][1]);
            double fl = Double.parseDouble(picks[i][2]);
            var rendered = renderer.render(skin, seed, fl, 0);
            PatternReport report = PatternClassifier.classify(skin, seed, rendered.paint(), catalog.patterns());
            int ox = (i % cols) * cell;
            int oy = (i / cols) * (cell + 30);
            g.drawImage(rendered.image().toBufferedImage(), ox, oy, null);
            g.setColor(Color.WHITE);
            String label = skin.displayName() + " #" + seed + " " + fl;
            g.drawString(label, ox + 4, oy + cell + 12);
            String cls = (report.variantName() != null ? report.variantName() + " " : "")
                    + (report.classification() != null ? report.classification() + " " : "")
                    + (report.fadePercent() != null ? report.fadePercent() + "%" : "");
            g.setColor(new Color(0xaab0b8));
            g.drawString(cls, ox + 4, oy + cell + 25);

            ArgbImage card = MapCardComposer.compose(rendered.image(), skin.rarity().color(), MapCardStyle.defaults());
            int[] decoded = dither.decode(dither.dither(card));
            maps.setRGB((i % cols) * 128, (i / cols) * 128, 128, 128, decoded, 0, 128);
        }
        g.dispose();
        ImageIO.write(full, "png", new File(out, "skins.png"));
        ImageIO.write(maps, "png", new File(out, "maps.png"));

        stats(catalog, renderer, "ak47_case_hardened");
        stats(catalog, renderer, "karambit_case_hardened");
        stats(catalog, renderer, "karambit_fade");
        stats(catalog, renderer, "karambit_marble_fade");
        stats(catalog, renderer, "karambit_doppler");
    }

    static MapDither mapDither() {
        return dev.plattnericus.cases.map.MapPaletteSource.load().dither(0.85);
    }

    private static void stats(Catalog catalog, SkinRenderer renderer, String skinId) throws Exception {
        SkinDefinition skin = catalog.skin(skinId);
        if (skin == null) {
            return;
        }
        Map<String, List<Double>> values = new TreeMap<>();
        Map<String, Integer> classes = new TreeMap<>();
        for (int seed = 0; seed <= 999; seed++) {
            PatternReport r = PatternClassifier.classify(skin, seed, renderer.paint(skin, seed), catalog.patterns());
            r.metrics().forEach((k, v) -> values.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
            String key = (r.variantName() == null ? "" : r.variantName() + " / ") + (r.classification() == null ? "-" : r.classification());
            classes.merge(key, 1, Integer::sum);
        }
        System.out.println("== " + skinId);
        values.forEach((k, list) -> {
            double[] a = list.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            System.out.printf("  %-18s min=%.3f p50=%.3f p90=%.3f p99=%.3f max=%.3f%n", k, a[0], a[a.length / 2],
                    a[(int) (a.length * 0.9)], a[(int) (a.length * 0.99)], a[a.length - 1]);
        });
        System.out.println("  classes " + classes);
    }
}
