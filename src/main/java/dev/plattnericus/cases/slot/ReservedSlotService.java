package dev.plattnericus.cases.slot;

import dev.plattnericus.cases.core.CasesContext;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Optional inventory shortcut. Disabled by default; /inventory and /skins open the menu without
 * taking up a slot. Disabling the shortcut removes any remaining marked tokens.
 */
public final class ReservedSlotService implements Listener {

    private final CasesContext ctx;

    public ReservedSlotService(CasesContext ctx) {
        this.ctx = ctx;
    }

    private boolean enabled() {
        return ctx.settings().reservedSlot().enabled();
    }

    private int slot() {
        return ctx.settings().reservedSlot().slot();
    }

    public boolean isToken(ItemStack item) {
        return item != null && !item.isEmpty()
                && item.getPersistentDataContainer().has(ctx.keys().menuToken, PersistentDataType.BYTE);
    }

    private ItemStack token(Player player) {
        ItemStack item = new ItemStack(ctx.settings().reservedSlot().material());
        item.editMeta(meta -> {
            meta.displayName(ctx.messages(player).item("slot.name"));
            meta.lore(ctx.messages(player).itemList("slot.lore"));
            String model = ctx.settings().reservedSlot().model();
            if (model != null && !model.isBlank()) {
                NamespacedKey key = NamespacedKey.fromString(model);
                if (key != null) {
                    meta.setItemModel(key);
                }
            }
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(ctx.keys().menuToken, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Removes old tokens when disabled, or places the optional shortcut and removes stray copies. */
    public void ensure(Player player) {
        PlayerInventory inv = player.getInventory();
        boolean active = enabled();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if ((!active || i != slot()) && isToken(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isToken(player.getOpenInventory().getCursor())) {
            player.getOpenInventory().setCursor(null);
        }
        if (!active) return;
        ItemStack current = inv.getItem(slot());
        if (isToken(current)) {
            ItemStack expected = token(player);
            if (!current.equals(expected)) inv.setItem(slot(), expected);
            return;
        }
        if (current != null && !current.isEmpty()) {
            int free = -1;
            for (int i = 0; i < 36; i++) {
                if (i != slot() && (inv.getItem(i) == null || inv.getItem(i).isEmpty())) {
                    free = i;
                    break;
                }
            }
            if (free < 0) {
                ctx.messages().send(player, "slot.blocked");
                return;
            }
            inv.setItem(free, current);
        }
        inv.setItem(slot(), token(player));
    }

    public void ensureAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ensure(p);
        }
    }

    private void open(Player player) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (player.isOnline() && enabled()) {
                ctx.gallery().open(player, null);
            }
        });
    }

    private void cleanAfterInteraction(Player player) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (player.isOnline()) ensure(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ensure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> ensure(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        ensure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean involved = isToken(event.getCurrentItem()) || isToken(event.getCursor())
                || (event.getClick() == ClickType.NUMBER_KEY && isToken(player.getInventory().getItem(event.getHotbarButton())))
                || (event.getClick() == ClickType.SWAP_OFFHAND && isToken(player.getInventory().getItemInOffHand()))
                || (event.getClickedInventory() == player.getInventory() && event.getSlot() == slot() && enabled());
        if (!involved) {
            return;
        }
        if (!enabled()) {
            cleanAfterInteraction(player);
            return;
        }
        event.setCancelled(true);
        if (isToken(event.getCurrentItem()) && event.getClickedInventory() == player.getInventory()) {
            player.closeInventory();
            open(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled()) {
            if (isToken(event.getOldCursor()) && event.getWhoClicked() instanceof Player player) cleanAfterInteraction(player);
            return;
        }
        if (isToken(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (event.getView().getInventory(raw) == event.getWhoClicked().getInventory()
                    && event.getView().convertSlot(raw) == slot() && enabled()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isToken(event.getItemDrop().getItemStack())) {
            if (enabled()) event.setCancelled(true);
            else {
                event.getItemDrop().remove();
                cleanAfterInteraction(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isToken(event.getMainHandItem()) || isToken(event.getOffHandItem())) {
            if (enabled()) event.setCancelled(true);
            else cleanAfterInteraction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!isToken(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (!enabled()) {
            cleanAfterInteraction(event.getPlayer());
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            open(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isToken(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            if (!enabled()) cleanAfterInteraction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isToken);
        event.getItemsToKeep().removeIf(this::isToken);
    }
}
