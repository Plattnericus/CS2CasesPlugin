package dev.plattnericus.cases.admin;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Changes to a player's skins shared by commands and the management menus. Works for online
 * players (live profile) and offline players (database snapshot); everything is written through.
 */
public final class AdminActions {

    private final CasesContext ctx;

    public AdminActions(CasesContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Rolls and stores a new skin. Null values are rolled like a case drop.
     * {@code done} runs on the server thread with the stored instance, or null if storing failed.
     */
    public void giveSkin(UUID owner, SkinDefinition skin, Double floatValue, Integer pattern, Boolean statTrak,
                         Consumer<SkinInstance> done) {
        var random = ctx.openings().roller().random();
        double fl = floatValue != null ? Math.max(skin.minFloat(), Math.min(skin.maxFloat(), floatValue))
                : skin.minFloat() + random.nextDouble() * (skin.maxFloat() - skin.minFloat());
        int min = ctx.catalog().patterns().seedMin();
        int max = ctx.catalog().patterns().seedMax();
        int seed = pattern != null ? pattern : min + random.nextInt(max - min + 1);
        boolean st = statTrak != null && statTrak && skin.weapon().statTrak();
        long wearSeed = random.nextLong();
        ctx.render().report(skin, seed).exceptionally(e -> PatternReport.none(null, null)).thenAccept(report ->
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    SkinInstance inst = new SkinInstance(UUID.randomUUID(), owner, skin.id(), fl, seed, wearSeed, st, 0,
                            PatternInfo.of(report), "admin", SkinInstance.Origin.ADMIN, System.currentTimeMillis(), false,
                            SkinInstance.Status.OWNED);
                    ctx.repository().insert(inst).whenComplete((v, error) -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                        if (error != null) {
                            done.accept(null);
                            return;
                        }
                        ctx.profiles().addLoaded(owner, inst);
                        done.accept(inst);
                    }));
                }));
    }

    /** Removes a skin from its owner (soft delete, kept for the audit trail) and takes it off if equipped. */
    public void remove(PlayerProfile profile, SkinInstance inst) {
        if (!ctx.commerce().mutable(profile, inst)) return;
        EquipSlot slot = profile.slotOf(inst.id());
        Player owner = Bukkit.getPlayer(profile.owner());
        if (slot != null) {
            if (owner != null && ctx.profiles().get(owner) == profile) {
                ctx.knives().unequip(owner, slot);
            } else {
                profile.setEquipped(slot, null);
                ctx.repository().setEquipped(profile.owner(), slot.id(), null);
            }
        }
        inst.setStatus(SkinInstance.Status.REMOVED);
        profile.remove(inst.id());
        ctx.repository().removeOwned(profile.owner(), inst.id());
    }

    /** Stores forced values; a changed pattern is analysed again first. */
    public void saveRoll(PlayerProfile profile, SkinInstance inst, boolean reanalyse, Runnable done) {
        if (!ctx.commerce().mutable(profile, inst) || !ctx.commerce().reserveMutation(inst.id())) return;
        SkinDefinition skin = ctx.catalog().skin(inst.skinId());
        Runnable store = () -> {
            ctx.repository().updateRoll(inst);
            ctx.commerce().releaseMutation(inst.id());
            Player owner = Bukkit.getPlayer(profile.owner());
            if (owner != null) {
                ctx.knives().refreshHeld(owner);
            }
            done.run();
        };
        if (!reanalyse || skin == null) {
            store.run();
            return;
        }
        ctx.render().report(skin, inst.pattern()).exceptionally(e -> PatternReport.none(null, null))
                .thenAccept(r -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    inst.setPatternInfo(PatternInfo.of(r));
                    store.run();
                }));
    }
}
