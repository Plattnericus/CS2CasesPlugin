package dev.plattnericus.cases.knife;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.LruCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Counts kills on StatTrak skins. A kill counts only if the killer holds the sword, bow or crossbow
 * that shows their equipped StatTrak skin, the victim is not the killer, and the same death has not been
 * counted already (some plugins fire death events twice).
 */
public final class StatTrakListener implements Listener {

    private final CasesContext ctx;
    private final LruCache<String, Boolean> counted = new LruCache<>(1024);

    public StatTrakListener(CasesContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (!(victim instanceof Player) && !ctx.settings().statTrak().countMobs()) {
            return;
        }
        SkinInstance knife = ctx.knives().heldSkin(killer);
        if (knife == null || !knife.statTrak() || ctx.commerce().locked(knife.id())) {
            return;
        }
        String key = victim.getUniqueId() + ":" + Bukkit.getCurrentTick();
        if (counted.get(key) != null) {
            return;
        }
        counted.put(key, Boolean.TRUE);
        knife.setKills(knife.kills() + 1);
        ctx.repository().addKills(knife.id(), 1);
        ctx.knives().refreshHeld(killer);
    }
}
