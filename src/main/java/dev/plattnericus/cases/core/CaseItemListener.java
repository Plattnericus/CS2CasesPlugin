package dev.plattnericus.cases.core;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.gui.menu.CasePreviewMenu;
import dev.plattnericus.cases.items.CaseItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking a case item opens its contents (with the open button), like inspecting a case in CS2. */
public final class CaseItemListener implements Listener {

    private final CasesContext ctx;
    private final java.util.Map<java.util.UUID, Integer> lastUse = new java.util.HashMap<>();

    public CaseItemListener(CasesContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        CaseItems.Identity id = ctx.caseItems().identify(event.getItem());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer previous = lastUse.put(event.getPlayer().getUniqueId(), tick);
        if (previous != null && previous == tick) return;
        ctx.caseItems().refreshModels(event.getPlayer());
        if (id.type().equals(CaseItems.TYPE_KEY)) {
            ctx.messages().send(event.getPlayer(), "items.key.use-hint");
            return;
        }
        CaseDefinition def = ctx.catalog().caseDefinition(id.id());
        if (def == null || !def.enabled()) {
            ctx.messages().send(event.getPlayer(), "opening.case-disabled");
            return;
        }
        if (ctx.settings().opening().worldDisplay() && ctx.openings().activeCount(event.getPlayer()) > 0) {
            // Repeated item clicks add a live reel without reopening the launcher. Explicit
            // quantity commands still support larger queues; casual spam stops at nine slots.
            if (ctx.openings().activeCount(event.getPlayer()) + ctx.openings().queuedCount(event.getPlayer())
                    < Math.min(9, ctx.settings().opening().maxActive())) ctx.openings().open(event.getPlayer(), def, false, false);
            else ctx.messages(event.getPlayer()).send(event.getPlayer(), "opening.limit");
        } else new CasePreviewMenu(ctx, event.getPlayer(), def, null).open();
    }

    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { lastUse.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        ctx.caseItems().refreshModels(event.getPlayer());
    }
}
