package dev.plattnericus.cases.skin;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.UUID;

/** Compact JSON form of an instance for the crash-safety journal in the player's data file. */
public final class InstanceCodec {

    private static final Gson GSON = new Gson();

    private record Dto(int v, String id, String owner, String skin, double fl, int pattern, long wearSeed,
                       boolean st, String variant, String variantName, String cls, int tier, int color, Double fade,
                       String source, String origin, long created, boolean traded) {
    }

    private InstanceCodec() {
    }

    public static String encode(SkinInstance i) {
        PatternInfo p = i.patternInfo();
        return GSON.toJson(new Dto(1, i.id().toString(), i.owner().toString(), i.skinId(), i.floatValue(), i.pattern(),
                i.wearSeed(), i.statTrak(), p.variantId(), p.variantName(), p.classification(), p.tier(), p.color(),
                p.fadePercent(), i.sourceCase(), i.origin().name(), i.createdAt(), i.traded()));
    }

    /** @return the decoded instance (status PENDING) or null if the entry is unreadable */
    public static SkinInstance decode(String json) {
        try {
            Dto d = GSON.fromJson(json, Dto.class);
            if (d == null || d.v() != 1 || d.id() == null || d.owner() == null || d.skin() == null
                    || d.skin().isBlank() || d.origin() == null || !Double.isFinite(d.fl())
                    || d.fl() < 0 || d.fl() > 1 || d.pattern() < 0) {
                return null;
            }
            return new SkinInstance(UUID.fromString(d.id()), UUID.fromString(d.owner()), d.skin(), d.fl(), d.pattern(),
                    d.wearSeed(), d.st(), 0,
                    new PatternInfo(d.variant(), d.variantName(), d.cls(), d.tier(), d.color(), d.fade()),
                    d.source(), SkinInstance.Origin.valueOf(d.origin()), d.created(), false, SkinInstance.Status.PENDING, d.traded());
        } catch (JsonParseException | IllegalArgumentException e) {
            return null;
        }
    }
}
