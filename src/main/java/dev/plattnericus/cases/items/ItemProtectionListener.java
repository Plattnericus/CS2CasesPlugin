package dev.plattnericus.cases.items;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Case and key items use ordinary base materials, so they must never act as those materials:
 * no crafting, no anvils/looms/cartography, no villager trading, no block interactions.
 */
public final class ItemProtectionListener implements Listener {

    private final CaseItems items;

    public ItemProtectionListener(CaseItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (items.isMarked(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (items.isMarked(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    /**
     * Keeps plugin items out of merchant, crafter and workstation input slots. Crafting grids are
     * covered by {@link #onCraft}, which empties the result instead.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (!isWorkstation(top)) {
            return;
        }
        boolean moving = items.isMarked(event.getCursor()) && event.getClickedInventory() == event.getView().getTopInventory();
        boolean shift = event.isShiftClick() && items.isMarked(event.getCurrentItem())
                && event.getClickedInventory() != event.getView().getTopInventory();
        boolean hotbar = event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && event.getClickedInventory() == event.getView().getTopInventory()
                && items.isMarked(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        boolean offhand = event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && event.getClickedInventory() == event.getView().getTopInventory()
                && items.isMarked(event.getWhoClicked().getInventory().getItemInOffHand());
        if (moving || shift || hotbar || offhand) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!isWorkstation(event.getView().getTopInventory().getType()) || !items.isMarked(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Prevents use on blocks such as lecterns, vaults or decorated pots. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && items.isMarked(event.getItem())
                && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    private static boolean isWorkstation(InventoryType type) {
        return switch (type) {
            case MERCHANT, CRAFTER, ANVIL, SMITHING, GRINDSTONE, CARTOGRAPHY, LOOM, STONECUTTER -> true;
            default -> false;
        };
    }
}
