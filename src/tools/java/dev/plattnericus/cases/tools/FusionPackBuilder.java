package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.pack.FusionPack;
import java.nio.file.Path;

/** The default build always combines current generated assets with the checked-in server overlay. */
public final class FusionPackBuilder {
    private FusionPackBuilder() { }
    public static void main(String[] args) throws Exception {
        FusionPack.merge(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), "mccases", args[3]);
        System.out.println("Fusion HD pack: current MCCases assets + exact custom server overlay -> " + args[2]);
    }
}
