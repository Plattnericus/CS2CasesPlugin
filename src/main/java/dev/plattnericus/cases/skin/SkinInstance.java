package dev.plattnericus.cases.skin;

import java.util.UUID;

/**
 * One concrete owned copy of a skin. Identity and roll results are fixed at creation; the mutable
 * fields (kills, favorite, status, admin overrides) are only touched on the server thread.
 */
public final class SkinInstance {

    public enum Status { PENDING, OWNED, LISTED, REMOVED }

    public enum Origin { CASE, ADMIN, TEST }

    private final UUID id;
    private final UUID owner;
    private final String skinId;
    private final long wearSeed;
    private final String sourceCase;
    private final Origin origin;
    private final long createdAt;
    private double floatValue;
    private int pattern;
    private boolean statTrak;
    private int kills;
    private PatternInfo patternInfo;
    private boolean favorite;
    private Status status;

    public SkinInstance(UUID id, UUID owner, String skinId, double floatValue, int pattern, long wearSeed,
                        boolean statTrak, int kills, PatternInfo patternInfo, String sourceCase, Origin origin,
                        long createdAt, boolean favorite, Status status) {
        this.id = id;
        this.owner = owner;
        this.skinId = skinId;
        this.floatValue = floatValue;
        this.pattern = pattern;
        this.wearSeed = wearSeed;
        this.statTrak = statTrak;
        this.kills = kills;
        this.patternInfo = patternInfo == null ? PatternInfo.NONE : patternInfo;
        this.sourceCase = sourceCase;
        this.origin = origin;
        this.createdAt = createdAt;
        this.favorite = favorite;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    /** First 8 hex chars of the id; what players and admins see. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public UUID owner() {
        return owner;
    }

    public String skinId() {
        return skinId;
    }

    public double floatValue() {
        return floatValue;
    }

    public int pattern() {
        return pattern;
    }

    public long wearSeed() {
        return wearSeed;
    }

    public boolean statTrak() {
        return statTrak;
    }

    public int kills() {
        return kills;
    }

    public PatternInfo patternInfo() {
        return patternInfo;
    }

    public String sourceCase() {
        return sourceCase;
    }

    public Origin origin() {
        return origin;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean favorite() {
        return favorite;
    }

    public Status status() {
        return status;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void setFloatValue(double floatValue) {
        this.floatValue = floatValue;
    }

    public void setPattern(int pattern) {
        this.pattern = pattern;
    }

    public void setStatTrak(boolean statTrak) {
        this.statTrak = statTrak;
    }

    public void setPatternInfo(PatternInfo patternInfo) {
        this.patternInfo = patternInfo == null ? PatternInfo.NONE : patternInfo;
    }

    public SkinInstance copyWithStatus(Status newStatus) {
        return new SkinInstance(id, owner, skinId, floatValue, pattern, wearSeed, statTrak, kills, patternInfo,
                sourceCase, origin, createdAt, favorite, newStatus);
    }

    public SkinInstance transferTo(UUID newOwner) {
        return new SkinInstance(id, newOwner, skinId, floatValue, pattern, wearSeed, statTrak, kills, patternInfo,
                sourceCase, origin, createdAt, false, Status.OWNED);
    }
}
