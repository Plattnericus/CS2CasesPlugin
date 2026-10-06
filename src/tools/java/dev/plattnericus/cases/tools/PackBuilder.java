package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.CatalogLoader;
import dev.plattnericus.cases.pack.PackExporter;
import dev.plattnericus.cases.render.RenderSettings;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.TextureStore;

import java.io.File;

/** Builds the optional resource pack from the bundled default catalog ({@code gradlew resourcePack}). */
public final class PackBuilder {

    private PackBuilder() {
    }

    public static void main(String[] args) throws Exception {
        File defaults = new File(args[0]);
        File target = new File(args[1]);
        TextureStore textures = new TextureStore(new File(defaults, "textures"));
        CatalogLoader.Result loaded = new CatalogLoader(new File(defaults, "catalog"), textures).load(1);
        if (!loaded.problems().isEmpty()) {
            loaded.problems().forEach(p -> System.err.println("catalog: " + p));
            throw new IllegalStateException("default catalog has problems");
        }
        PackExporter.Result result = PackExporter.export(loaded.catalog(), new SkinRenderer(textures, RenderSettings.defaults()),
                "mccases", "Plattnericus", target);
        result.failures().forEach(f -> System.err.println("skipped " + f));
        if (!result.failures().isEmpty()) throw new IllegalStateException("resource pack export is incomplete");
        System.out.println("Resource pack: " + result.skins() + " skins, " + result.cases() + " cases, " + result.keys()
                + " keys -> " + target);
    }
}
