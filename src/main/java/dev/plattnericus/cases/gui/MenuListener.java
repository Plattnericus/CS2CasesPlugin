package dev.plattnericus.cases.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cancels every interaction with menu inventories and dispatches button clicks (throttled). */
public final class MenuListener implements Listener {

    private static final long CLICK_INTERVAL_MS = 120;

    private final Map<UUID, Long> lastClick = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_INTERVAL_MS) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        menu.click(event.getSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    /** Keep hotbar scrolling usable while a menu inventory has focus. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        int requested = event.getNewSlot();
        Bukkit.getScheduler().runTask(menu.ctx.plugin(), () -> {
            if (!event.getPlayer().isOnline()
                    || event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) != menu) {
                return;
            }
            if (event.getPlayer().getInventory().getHeldItemSlot() != requested) {
                event.getPlayer().getInventory().setHeldItemSlot(requested);
                event.getPlayer().updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu) {
            menu.closed();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
