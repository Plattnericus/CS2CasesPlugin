package dev.plattnericus.cases.shop;

import dev.plattnericus.cases.core.CasesContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Opens the shop on interaction and protects the dealer from damage, conversion and leashing. */
public final class ShopListener implements Listener {

    private final CasesContext ctx;

    public ShopListener(CasesContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!ctx.shop().isShop(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().hasPermission("mccases.shop")) {
            ctx.messages().send(event.getPlayer(), "general.no-permission");
            return;
        }
        new ShopMenu(ctx, event.getPlayer()).open();
    }

    /** Some villagers that generate with a village become dealers, at most one per village area. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !ctx.shop().naturalEnabled()) return;
        var reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) return;
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= ctx.shop().naturalChance()) return;
        if (ctx.shop().dealerNear(villager.getLocation(), 96)) return;
        ctx.shop().makeDealer(villager);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (ctx.shop().isShop(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (ctx.shop().isShop(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent event) {
        if (ctx.shop().isShop(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (ctx.shop().isShop(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
