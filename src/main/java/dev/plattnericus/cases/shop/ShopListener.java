package dev.plattnericus.cases.shop;

import dev.plattnericus.cases.core.CasesContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Villager;
import org.bukkit.Bukkit;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Opens the shop on interaction, protects the dealer and settles dealers in generated villages. */
public final class ShopListener implements Listener {

    /** A chunk nobody has spent a minute in yet still holds its generated villagers. */
    private static final long FRESH_CHUNK_TICKS = 1200;

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

    /**
     * Villagers that generated with a village arrive with their chunk. Every chunk is looked at once,
     * and only while it is still unvisited, so villagers that players moved or bred are never taken.
     * A village is counted when its first villager shows up; every n-th village gets one dealer.
     */
    @EventHandler
    public void onVillageLoad(EntitiesLoadEvent event) {
        if (!ctx.shop().naturalEnabled()) return;
        var chunk = event.getChunk();
        var marker = chunk.getPersistentDataContainer();
        if (marker.has(ctx.keys().shopChunk, PersistentDataType.BYTE)) return;
        marker.set(ctx.keys().shopChunk, PersistentDataType.BYTE, (byte) 1);
        if (chunk.getInhabitedTime() > FRESH_CHUNK_TICKS) return;
        Villager villager = null;
        for (var entity : event.getEntities()) {
            if (entity instanceof Villager v && v.isAdult() && !ctx.shop().isShop(v)) {
                villager = v;
                break;
            }
        }
        if (villager == null) return;
        Long village = ctx.shop().villageId(chunk);
        if (village == null || !ctx.shop().claimVillage(chunk.getWorld(), village)) return;
        Villager chosen = villager;
        // one tick later: the chunk is fully in the world
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (!chosen.isValid() || ctx.shop().isShop(chosen)) return;
            if (ctx.shop().dealerNear(chosen.getLocation(), ctx.shop().naturalDistance())) return;
            for (var made : ctx.shop().makeNatural(chosen)) ctx.npcAnimator().track(made);
            var at = chosen.getLocation();
            ctx.plugin().getLogger().info("A case dealer settled in the village at " + at.getBlockX() + ", " + at.getBlockZ()
                    + " (" + at.getWorld().getName() + ")");
        });
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
