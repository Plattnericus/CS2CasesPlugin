package dev.plattnericus.cases.knife;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ListIterator;
import java.util.UUID;

/**
 * Equipping knives. The database stores which instance is equipped; any sword the owner holds is
 * shown as that knife. Event driven: the held sword is refreshed only when what the player holds
 * can have changed, never by scanning inventories every tick.
 */
public final class KnifeService implements Listener {

    private final CasesContext ctx;
    private final KnifeCosmetics cosmetics;
    private final java.util.Set<UUID> equipping = new java.util.HashSet<>();

    public KnifeService(CasesContext ctx, KnifeCosmetics cosmetics) {
        this.ctx = ctx;
        this.cosmetics = cosmetics;
    }

    public KnifeCosmetics cosmetics() {
        return cosmetics;
    }

    /** Knives go into the knife slot; weapon skins into the bow slot (see {@link #equip(Player, SkinInstance, EquipSlot)}). */
    public boolean equip(Player player, SkinInstance instance) {
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        return equip(player, instance, def != null && def.isKnife() ? EquipSlot.KNIFE : EquipSlot.BOW);
    }

    public boolean equip(Player player, SkinInstance instance, EquipSlot slot) {
        return equip(player, instance, slot, success -> { });
    }

    /** Completion feedback is emitted only after the equipment transaction has committed. */
    public boolean equip(Player player, SkinInstance instance, EquipSlot slot, java.util.function.Consumer<Boolean> done) {
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile == null) {
            ctx.messages(player).send(player, "profile.loading");
            done.accept(false);
            return false;
        }
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        // ownership check against the authoritative in-memory copy, never against the client
        if (ctx.commerce().locked(instance.id())) {
            ctx.messages(player).send(player, "commerce.locked"); done.accept(false); return false;
        }
        if (def == null || profile.get(instance.id()) != instance || instance.status() != SkinInstance.Status.OWNED
                || !instance.owner().equals(player.getUniqueId())) {
            ctx.messages(player).send(player, "knife.not-owned");
            ctx.sounds().play(player, "gui.error");
            done.accept(false);
            return false;
        }
        if (!slot.accepts(def) || !slot.enabled(ctx.settings())) {
            ctx.messages(player).send(player, "equip.wrong-slot");
            ctx.sounds().play(player, "gui.error");
            done.accept(false);
            return false;
        }
        if (!ctx.commerce().reserveMutation(instance.id())) { ctx.messages(player).send(player, "commerce.locked"); done.accept(false); return false; }
        if (!equipping.add(player.getUniqueId())) {
            ctx.commerce().releaseMutation(instance.id());
            ctx.messages(player).send(player, "commerce.locked"); done.accept(false); return false;
        }
        ctx.inspect().stop(player);
        // one instance can only sit in one slot
        EquipSlot previous = profile.slotOf(instance.id());
        if (previous != null && previous != slot) {
            profile.setEquipped(previous, null);
            strip(player, previous);
        }
        UUID displaced = profile.equipped(slot);
        profile.setEquipped(slot, instance.id());
        refreshHeld(player);
        ctx.repository().equipOwned(player.getUniqueId(), slot.id(), instance.id()).whenComplete((count, error) ->
                dev.plattnericus.cases.command.CommandFeedback.main(ctx, player, "equip", () -> {
                    ctx.commerce().releaseMutation(instance.id());
                    equipping.remove(player.getUniqueId());
                    if (error != null || count != 1) {
                        // Roll back only the presentation changed by this request.
                        if (instance.id().equals(profile.equipped(slot))) profile.setEquipped(slot, displaced);
                        if (previous != null && previous != slot && profile.equipped(previous) == null) profile.setEquipped(previous, instance.id());
                        refreshHeld(player);
                        if (error != null) dev.plattnericus.cases.command.CommandFeedback.failure(ctx, player, "equip", error);
                        else ctx.messages(player).send(player, "command.state-changed");
                        done.accept(false); return;
                    }
                    ctx.messages(player).send(player, "equip.equipped." + slot.id(), Text.component("skin", ctx.formatter(player).fullName(def, instance)));
                    ctx.sounds().play(player, "knife.equip");
                    done.accept(true);
                }));
        return true;
    }

    public void unequip(Player player) {
        unequip(player, EquipSlot.KNIFE);
    }

    public void unequip(Player player, EquipSlot slot) {
        if (equipping.contains(player.getUniqueId())) { ctx.messages(player).send(player, "commerce.locked"); return; }
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile == null || profile.equipped(slot) == null) {
            return;
        }
        ctx.inspect().stop(player);
        profile.setEquipped(slot, null);
        ctx.repository().setEquipped(player.getUniqueId(), slot.id(), null);
        strip(player, slot);
        ctx.messages(player).send(player, "equip.unequipped." + slot.id());
        ctx.sounds().play(player, "knife.unequip");
    }

    /** Equip if it is not in that slot, otherwise take it off. */
    public void toggle(Player player, SkinInstance instance, EquipSlot slot) {
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile != null && instance.id().equals(profile.equipped(slot))) {
            unequip(player, slot);
        } else {
            equip(player, instance, slot);
        }
    }

    /** Brings the held item in line with the skin equipped for its slot (apply, refresh or strip). */
    public void refreshHeld(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand.isEmpty()) {
            return;
        }
        UUID owner = cosmetics.ownerOf(hand);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            inv.setItemInMainHand(cosmetics.strip(hand));
            return;
        }
        EquipSlot slot = EquipSlot.forItem(hand.getType(), ctx.settings());
        if (slot == null) {
            if (cosmetics.isCosmetic(hand)) {
                inv.setItemInMainHand(cosmetics.strip(hand));
            }
            return;
        }
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile == null) {
            return;
        }
        SkinInstance skin = profile.equippedInstance(slot);
        SkinDefinition def = skin == null ? null : ctx.catalog().skin(skin.skinId());
        if (skin == null || def == null) {
            if (cosmetics.isCosmetic(hand)) {
                inv.setItemInMainHand(cosmetics.strip(hand));
            }
            return;
        }
        ItemStack applied = cosmetics.apply(hand, def, skin, ctx.formatter(player), ctx.messages(player), ctx.settings(),
                ctx.catalog().version());
        if (applied != hand || !applied.equals(inv.getItemInMainHand())) {
            inv.setItemInMainHand(applied);
        }
    }

    /** Restores every cosmetic item of one slot type in the inventory to its original look. */
    public void strip(Player player, EquipSlot slot) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (cosmetics.isCosmetic(item) && EquipSlot.forItem(item.getType(), ctx.settings()) == slot) {
                inv.setItem(i, cosmetics.strip(item));
            }
        }
    }

    /** Restores every cosmetic item in the inventory to its original look. */
    public void stripAll(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (cosmetics.isCosmetic(contents[i])) {
                inv.setItem(i, cosmetics.strip(contents[i]));
            }
        }
    }

    /** The equipped skin shown by the item in the player's hand (sword, bow or crossbow), or null. */
    public SkinInstance heldSkin(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        UUID id = cosmetics.instanceOf(hand);
        if (id == null || !player.getUniqueId().equals(cosmetics.ownerOf(hand))) {
            return null;
        }
        EquipSlot slot = EquipSlot.forItem(hand.getType(), ctx.settings());
        PlayerProfile profile = ctx.profiles().get(player);
        SkinInstance equipped = profile == null || slot == null ? null : profile.equippedInstance(slot);
        return equipped != null && equipped.id().equals(id) ? equipped : null;
    }

    /** The equipped knife if the player is holding a sword showing it. */
    public SkinInstance heldKnife(Player player) {
        SkinInstance skin = heldSkin(player);
        SkinDefinition def = skin == null ? null : ctx.catalog().skin(skin.skinId());
        return def != null && def.isKnife() ? skin : null;
    }

    private void later(Player player) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (player.isOnline()) {
                refreshHeld(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            later(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            later(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!ctx.settings().knives().stripOnDrop()) {
            return;
        }
        Item drop = event.getItemDrop();
        if (cosmetics.isCosmetic(drop.getItemStack())) {
            drop.setItemStack(cosmetics.strip(drop.getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        ListIterator<ItemStack> it = event.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (cosmetics.isCosmetic(stack)) {
                it.set(cosmetics.strip(stack));
            }
        }
    }
}
