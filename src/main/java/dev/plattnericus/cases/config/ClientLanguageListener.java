package dev.plattnericus.cases.config;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

/** Client settings arrive after join; refresh next tick, after Paper stores the new locale. */
public final class ClientLanguageListener implements Listener {
    private final CasesContext ctx;
    private final java.util.function.Consumer<Player> refreshToken;
    private final java.util.Map<java.util.UUID, Boolean> pending = new java.util.HashMap<>();

    public ClientLanguageListener(CasesContext ctx, java.util.function.Consumer<Player> refreshToken) {
        this.ctx = ctx;
        this.refreshToken = refreshToken;
    }

    private void refreshLater(Player player, boolean menus) {
        java.util.UUID id = player.getUniqueId();
        Boolean queued = pending.putIfAbsent(id, menus);
        if (queued != null) {
            if (menus) pending.put(id, true);
            return;
        }
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            boolean refreshMenus = Boolean.TRUE.equals(pending.remove(id));
            if (!player.isOnline()) return;
            ctx.caseItems().refreshLanguage(player, ctx.catalog());
            ctx.knives().refreshHeld(player);
            refreshToken.accept(player);
            if (refreshMenus) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
                    menu.refreshLanguage();
                }
                ctx.gallery().refreshLanguage(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocale(PlayerLocaleChangeEvent event) {
        refreshLater(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && ctx.caseItems().identify(event.getItem().getItemStack()) != null) {
            refreshLater(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) refreshLater(player, false);
    }
}
