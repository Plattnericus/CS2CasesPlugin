package dev.plattnericus.cases.commerce;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Main-thread item arithmetic. Storage slots and optional offhand; armor/cursor are excluded. */
public final class EmeraldItems {
    private EmeraldItems() { }
    public static long count(PlayerInventory inv, boolean offhand) {
        long total = 0;
        for (ItemStack item : inv.getStorageContents()) if (emerald(item)) total += item.getAmount();
        if (offhand && emerald(inv.getItemInOffHand())) total += inv.getItemInOffHand().getAmount();
        return total;
    }
    private static boolean emerald(ItemStack item) { return item != null && item.getType() == Material.EMERALD && item.getAmount() > 0; }
    public static boolean take(PlayerInventory inv, long amount, boolean offhand) {
        if (amount <= 0 || count(inv, offhand) < amount) return false;
        long left = amount;
        for (int slot = 0; slot < inv.getStorageContents().length && left > 0; slot++) left = remove(inv, slot, left);
        if (left > 0 && offhand) left = remove(inv, 40, left);
        if (left != 0) throw new IllegalStateException("Inventory changed during synchronous payment");
        return true;
    }
    private static long remove(PlayerInventory inv, int slot, long left) {
        ItemStack item = inv.getItem(slot);
        if (!emerald(item)) return left;
        int taken = (int) Math.min(left, item.getAmount());
        if (item.getAmount() == taken) inv.setItem(slot, null);
        else { item = item.clone(); item.setAmount(item.getAmount() - taken); inv.setItem(slot, item); }
        return left - taken;
    }
    public static int capacity(PlayerInventory inv) {
        ItemStack plain = new ItemStack(Material.EMERALD);
        int capacity = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null || item.isEmpty()) capacity += 64;
            else if (item.isSimilar(plain)) capacity += Math.max(0, Math.min(64, item.getMaxStackSize()) - item.getAmount());
        }
        return capacity;
    }
    /** Fully deferred if the requested delivery no longer fits. Never drops items. */
    public static boolean give(PlayerInventory inv, long amount) {
        if (amount <= 0 || amount > capacity(inv)) return false;
        int left = (int) amount;
        ItemStack plain = new ItemStack(Material.EMERALD);
        for (int slot = 0; slot < inv.getStorageContents().length && left > 0; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.isEmpty() || !item.isSimilar(plain)) continue;
            int added = Math.min(left, Math.max(0, Math.min(64, item.getMaxStackSize()) - item.getAmount()));
            item = item.clone(); item.setAmount(item.getAmount() + added); inv.setItem(slot, item); left -= added;
        }
        for (int slot = 0; slot < inv.getStorageContents().length && left > 0; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.isEmpty()) continue;
            int added = Math.min(left, 64); inv.setItem(slot, new ItemStack(Material.EMERALD, added)); left -= added;
        }
        return left == 0;
    }
}
