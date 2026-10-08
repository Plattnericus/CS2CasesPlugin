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
        new CasePreviewMenu(ctx, event.getPlayer(), def, null).open();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        ctx.caseItems().refreshModels(event.getPlayer());
    }
}
