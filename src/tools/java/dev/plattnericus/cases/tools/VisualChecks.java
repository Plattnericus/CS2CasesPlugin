package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.skin.SkinInstance;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Development fixture for client screenshots: holds one skin of a weapon in hotbar slot 0 on a
 * sword, bow or crossbow. Skins are granted once as admin fixtures and reused afterwards; the
 * command answers {@code PENDING} until the granted skin is stored and equipped.
 */
final class VisualChecks {
    private static final Set<UUID> granting = new HashSet<>();

    private VisualChecks() {
    }

    /** Plays one named held-pose inspect on the held skin. */
    static void inspect(CommandSender sender, Player player, CasesContext ctx, String animation) {
        SkinInstance held = ctx.knives().heldSkin(player);
        ctx.inspect().stop(player);
        boolean started = held != null && ctx.inspect().startView(player, held, animation);
        sender.sendMessage(started ? "VISUAL INSPECT " + animation : "VISUAL FAILED " + animation);
    }

    static void hold(CommandSender sender, Player player, CasesContext ctx, String weaponId, String slotName) {
        SkinDefinition skin = ctx.catalog().skins().stream().filter(s -> s.weapon().id().equals(weaponId))
                .min(Comparator.comparing(SkinDefinition::id)).orElse(null);
        if (skin == null) { sender.sendMessage("VISUAL UNKNOWN " + weaponId); return; }
        EquipSlot slot = skin.isKnife() ? EquipSlot.KNIFE : slotName.equals("crossbow") ? EquipSlot.CROSSBOW : EquipSlot.BOW;
        var profile = ctx.profiles().get(player);
        if (profile == null) { sender.sendMessage("VISUAL PENDING profile"); return; }
        SkinInstance owned = profile.all().stream().filter(i -> i.skinId().equals(skin.id()) && i.status() == SkinInstance.Status.OWNED)
                .findFirst().orElse(null);
        if (owned == null) {
            if (granting.add(player.getUniqueId())) {
                new dev.plattnericus.cases.admin.AdminActions(ctx).giveSkin(player.getUniqueId(), skin, .02, 7, false,
                        inst -> granting.remove(player.getUniqueId()));
            }
            sender.sendMessage("VISUAL PENDING grant " + skin.id());
            return;
        }
        ctx.inspect().stop(player);
        if (!owned.id().equals(profile.equipped(slot))) {
            ctx.knives().equip(player, owned, slot);
            sender.sendMessage("VISUAL PENDING equip " + skin.id());
            return;
        }
        Material base = slot == EquipSlot.KNIFE ? Material.IRON_SWORD : slot == EquipSlot.CROSSBOW ? Material.CROSSBOW : Material.BOW;
        if (player.getInventory().getItem(0) == null || player.getInventory().getItem(0).getType() != base
                || !owned.id().equals(ctx.knives().cosmetics().instanceOf(player.getInventory().getItem(0)))) {
            player.getInventory().setItem(0, new ItemStack(base));
        }
        player.getInventory().setItem(8, new ItemStack(Material.ARROW, 64));
        player.getInventory().setHeldItemSlot(0);
        ctx.knives().refreshHeld(player);
        sender.sendMessage("VISUAL READY " + weaponId + " " + skin.id() + " " + slot.name().toLowerCase(java.util.Locale.ROOT));
    }
}
