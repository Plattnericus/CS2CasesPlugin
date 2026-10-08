package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.commerce.EmeraldItems;
import dev.plattnericus.cases.commerce.ItemReceipts;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.profile.PendingJournal;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.storage.OpeningRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;

/** Real Paper ItemStack/PDC APIs with controlled inventory slots; no connected client required. */
public final class ItemRuntimeChecks {
    private ItemRuntimeChecks() { }
    public static void run(Plugin plugin, CasesContext ctx) throws Exception {
        ItemStack[] slots = new ItemStack[41];
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(ItemRuntimeChecks.class.getClassLoader(), new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getStorageContents" -> Arrays.copyOf(slots, 36);
            case "getContents" -> slots.clone();
            case "getItem" -> slots[(Integer) args[0]];
            case "setItem" -> { slots[(Integer) args[0]] = (ItemStack) args[1]; yield null; }
            case "getItemInOffHand" -> slots[40] == null ? new ItemStack(Material.AIR) : slots[40];
            case "getSize" -> 41;
            case "getMaxStackSize" -> 64;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        slots[0] = new ItemStack(Material.EMERALD, 64); slots[10] = new ItemStack(Material.EMERALD, 16);
        slots[40] = new ItemStack(Material.EMERALD, 20); slots[36] = new ItemStack(Material.EMERALD, 64);
        require(EmeraldItems.count(inventory, false) == 80 && EmeraldItems.count(inventory, true) == 100, "offhand/storage count or armor exclusion");
        require(!EmeraldItems.take(inventory, 101, true) && EmeraldItems.count(inventory, true) == 100, "insufficient payment mutated inventory");
        require(!EmeraldItems.take(inventory, Long.MAX_VALUE, true) && !EmeraldItems.take(inventory, -1, true), "overflow/negative payment accepted");
        require(EmeraldItems.take(inventory, 90, true) && EmeraldItems.count(inventory, true) == 10 && slots[40].getAmount() == 10, "exact multi-slot debit");
        for (int i = 0; i < 36; i++) slots[i] = new ItemStack(Material.STONE, 64);
        require(EmeraldItems.capacity(inventory) == 0 && !EmeraldItems.give(inventory, 1), "full inventory payment dropped/overwrote items");
        slots[5] = new ItemStack(Material.EMERALD, 60); slots[8] = null;
        require(EmeraldItems.capacity(inventory) == 68 && !EmeraldItems.give(inventory, 69), "capacity bounds");
        require(EmeraldItems.give(inventory, 68) && slots[5].getAmount() == 64 && slots[8].getAmount() == 64 && EmeraldItems.capacity(inventory) == 0, "partial claim did not fill exactly");
        Arrays.fill(slots, null);
        UUID owner = UUID.randomUUID();
        PersistentDataContainer pdc = Bukkit.getItemFactory().getItemMeta(Material.PAPER).getPersistentDataContainer();
        int[] saves = {0};
        Player player = (Player) Proxy.newProxyInstance(ItemRuntimeChecks.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getInventory" -> inventory;
            case "getUniqueId" -> owner;
            case "getPersistentDataContainer" -> pdc;
            case "saveData" -> { saves[0]++; yield null; }
            case "locale" -> java.util.Locale.US;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var receipts = new ItemReceipts(plugin); UUID payment = UUID.randomUUID();
        receipts.save(player, "PAY", payment, 320); receipts.save(player, "PAY", payment, 320);
        require(receipts.has(player, "PAY", payment, 320) && !receipts.has(player, "PAY", payment, 321) && saves[0] == 2, "receipt identity/save mismatch");
        receipts.clear(player, "PAY", payment, 320); require(!receipts.has(player, "PAY", payment, 320), "receipt not cleared");
        var caseDef = ctx.catalog().caseDefinition("kilowatt_case"); var key = ctx.catalog().key(caseDef.keyId());
        slots[0] = ctx.caseItems().caseItem(caseDef, 20, false, ctx.messages()); slots[1] = ctx.caseItems().keyItem(key, 20, false, ctx.messages());
        for (int i = 0; i < 20; i++) require(ctx.caseItems().consumePair(player, caseDef.id(), key.id()) != null, "valid pair rejected");
        require(ctx.caseItems().count(player, CaseItems.TYPE_CASE, caseDef.id()) == 0 && ctx.caseItems().count(player, CaseItems.TYPE_KEY, key.id()) == 0
                && ctx.caseItems().consumePair(player, caseDef.id(), key.id()) == null, "twenty openings did not consume exactly twenty pairs");
        slots[0] = ctx.caseItems().caseItem(caseDef, 2, false, ctx.messages());
        require(ctx.caseItems().consumePair(player, caseDef.id(), key.id()) == null && slots[0].getAmount() == 2, "missing key consumed a case");
        slots[1] = ctx.caseItems().keyItem(key, 1, true, ctx.messages());
        require(ctx.caseItems().consumePair(player, caseDef.id(), key.id()).keyTest(), "test-key provenance lost");
        var journal = new PendingJournal(ctx.keys());
        var reward = new SkinInstance(UUID.randomUUID(), owner, caseDef.pool().values().iterator().next().getFirst().id(), 0.1, 77, 777, true, 0, PatternInfo.NONE, caseDef.id(), SkinInstance.Origin.CASE, 123, false, SkinInstance.Status.PENDING);
        var record = new OpeningRecord(UUID.randomUUID(), owner, "Test", caseDef.id(), reward.id(), reward.skinId(), "rare", 0.1, 77, true, false, 123);
        journal.add(player, reward, record);
        require(journal.entries(player).size() == 1 && journal.opening(player, reward.id()).openingId().equals(record.openingId()), "opening history was not journaled");
        journal.remove(player, reward.id()); journal.add(player, reward);
        require(journal.entries(player).size() == 1 && journal.opening(player, reward.id()) == null, "legacy opening journal incompatible");
        journal.remove(player, reward.id()); require(journal.entries(player).isEmpty(), "journal removal failed");
        plugin.getLogger().info("PASS ITEM RUNTIME: genuine Paper emerald stacks/PDC, storage+offhand payment, armor exclusion, negative/overflow/insufficient funds, full/partial inventory claims, idempotent receipts, 20 exact case/key pairs, test provenance and v1/v2 opening journals. Controlled player proxy; no connected-client gameplay test.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
