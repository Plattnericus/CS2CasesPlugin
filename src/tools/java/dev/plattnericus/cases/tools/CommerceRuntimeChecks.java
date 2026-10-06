package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.commerce.*;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Exercises the public services and inventory listeners with two connected Vanilla clients. */
public final class CommerceRuntimeChecks {
    private CommerceRuntimeChecks() { }
    public static void run(Plugin plugin, CommandSender sender, Player first, Player second, CasesContext ctx) {
        require(first != second && ctx.profiles().get(first) != null && ctx.profiles().get(second) != null, "two loaded players required");
        require(ctx.commerce().available(), "commerce not ready");
        ctx.commerce().cancel(first); ctx.commerce().cancel(second); ctx.gallery().close(first); ctx.gallery().close(second);
        var definition = ctx.catalog().skins().stream().filter(s -> s.isKnife()).findFirst().orElseThrow();
        SkinInstance a = fixture(first, definition.id()), b = fixture(second, definition.id()), c = fixture(first, definition.id());
        var fixtures = new java.util.ArrayList<>(List.of(a, b, c));
        var collectionFixtures = new java.util.ArrayList<SkinInstance>();
        for (int i = 0; i < 17; i++) { var skin = fixture(first, definition.id()); collectionFixtures.add(skin); fixtures.add(skin); }
        ItemStack[] firstInventory = snapshot(first), secondInventory = snapshot(second);
        var firstSlots = slots(first, ctx); var secondSlots = slots(second, ctx);
        long[] before = new long[2];
        Inventory[] tradeViews = new Inventory[2];
        int[] previousRevision = new int[1];
        ItemStack[][] firstPage = new ItemStack[1][];
        SkinInstance[] selected = new SkinInstance[1];
        main(plugin, CompletableFuture.allOf(fixtures.stream().map(ctx.repository()::insert).toArray(CompletableFuture[]::new)))
            .thenRun(() -> {
                for (var skin : fixtures) ctx.profiles().addLoaded(skin.owner(), skin);
                new TradePlayersMenu(ctx, first).open();
                int partnerSlot = -1;
                for (int slot = 9; slot < 45; slot++) if (isHead(first.getOpenInventory().getTopInventory().getItem(slot), second)) { partnerSlot = slot; break; }
                require(partnerSlot >= 0, "partner selection did not show the actual player head");
                click(first, partnerSlot); command(second, "trade accept " + first.getName());
                TradeSession trade = ctx.commerce().trade(first.getUniqueId());
                require(trade != null && trade == ctx.commerce().trade(second.getUniqueId()), "trade request/accept failed");
                require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu
                        && second.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu, "trade menus not open for both clients");
                tradeViews[0] = first.getOpenInventory().getTopInventory(); tradeViews[1] = second.getOpenInventory().getTopInventory();
                require(tradeViews[0].getItem(38).getType() == org.bukkit.Material.GRAY_CONCRETE, "empty trade allowed confirmation");
                require(isHead(tradeViews[0].getItem(2), first) && isHead(tradeViews[0].getItem(6), second)
                        && isHead(tradeViews[1].getItem(2), second) && isHead(tradeViews[1].getItem(6), first), "trade halves showed the wrong player heads");
                require(tradeViews[0].getItem(0).getType() == org.bukkit.Material.BLUE_STAINED_GLASS_PANE
                        && tradeViews[0].getItem(8).getType() == org.bukkit.Material.LIME_STAINED_GLASS_PANE, "halves not visually distinct");
                require(collectionFixtures.stream().anyMatch(skin -> findSkin(tradeViews[0], skin, true) >= 0), "own skins did not appear immediately");
                verifyText(first); verifyText(second);
                ctx.commerce().toggle(first, a.id()); click(second, findSkin(tradeViews[1], b, true));
                require(containsSkin(tradeViews[0], 14, b) && containsSkin(tradeViews[1], 14, a), "counteroffer not visible while selecting skins");
                verifySelection(tradeViews[1].getItem(findSkin(tradeViews[1], b, true)), b, true, ctx);
                verifySelection(tradeViews[0].getItem(14), b, false, ctx);
                verifySelection(tradeViews[1].getItem(14), a, false, ctx);
                require(tradeViews[0].getItem(48).getType() == org.bukkit.Material.SPECTRAL_ARROW, "large collection did not offer a second page");
                firstPage[0] = tradeViews[0].getContents();
                require(tradeViews[1].getItem(38).getType() == org.bukkit.Material.CLOCK, "confirmation did not show review countdown");
                require(first.getOpenInventory().getTopInventory() == tradeViews[0]
                        && second.getOpenInventory().getTopInventory() == tradeViews[1], "live offer update reopened a menu");
                previousRevision[0] = trade.revision();
                require(ctx.commerce().locked(a.id()) && ctx.commerce().locked(b.id()), "offer not reserved");
                require(!ctx.knives().equip(first, a), "reserved skin was equipped");
                new dev.plattnericus.cases.admin.AdminActions(ctx).remove(ctx.profiles().get(first), a);
                require(ctx.profiles().get(first).get(a.id()) == a, "reserved skin was deleted");
                ctx.commerce().confirm(first); require(!trade.confirmed(first.getUniqueId()), "confirmation cooldown bypassed");
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 14);
                require(ctx.commerce().trade(first.getUniqueId()).items(second.getUniqueId()).contains(b.id()), "counteroffer click changed the partner's offer");
                ctx.commerce().toggle(second, b.id());
                require(!containsSkin(tradeViews[0], 14, b) && tradeViews[0].getItem(24).getType() == org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                        "removed counteroffer did not disappear live");
                ctx.commerce().toggle(second, b.id());
                require(containsSkin(tradeViews[0], 14, b), "counteroffer re-add did not update the collection view");
                require(tradeViews[0].getItem(48).getType() == org.bukkit.Material.SPECTRAL_ARROW, "remote change hid collection navigation");
                verifyText(first); verifyText(second);
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 48);
                require(!java.util.Objects.equals(firstPage[0][9], tradeViews[0].getItem(9)), "next collection page did not change visible skins");
                require(containsSkin(tradeViews[0], 14, b) && tradeViews[0].getItem(45).getType() == org.bukkit.Material.SPECTRAL_ARROW,
                        "counteroffer or previous button disappeared on the second page");
                require(first.getOpenInventory().getTopInventory() == tradeViews[0], "collection pagination reopened the trade");
                verifyText(first);
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 45);
                require(java.util.Objects.equals(firstPage[0][9], tradeViews[0].getItem(9)), "previous collection page did not restore the first page");
                int offeredSlot = findSkin(tradeViews[0], a, true);
                if (offeredSlot >= 0) verifySelection(tradeViews[0].getItem(offeredSlot), a, true, ctx);
                selected[0] = collectionFixtures.stream().filter(skin -> findSkin(tradeViews[0], skin, true) >= 0).findFirst().orElseThrow();
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                int slot = findSkin(tradeViews[0], selected[0], true); click(first, slot);
                require(ctx.commerce().trade(first.getUniqueId()).items(first.getUniqueId()).contains(selected[0].id()), "collection click did not select the skin");
                verifySelection(tradeViews[0].getItem(slot), selected[0], true, ctx);
                require(findSkin(tradeViews[1], selected[0], false) >= 0, "collection selection did not appear in the partner's offer panel");
                verifySelection(tradeViews[1].getItem(findSkin(tradeViews[1], selected[0], false)), selected[0], false, ctx);
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, findSkin(tradeViews[0], selected[0], true));
                require(!ctx.commerce().trade(first.getUniqueId()).items(first.getUniqueId()).contains(selected[0].id())
                        && findSkin(tradeViews[1], selected[0], false) < 0, "collection click did not remove the skin for both players");
                verifySelection(tradeViews[0].getItem(findSkin(tradeViews[0], selected[0], true)), selected[0], false, ctx);
                protectMenu(first);
            })
            .thenCompose(v -> delay(plugin, 45))
            .thenRun(() -> {
                TradeSession trade = ctx.commerce().trade(first.getUniqueId());
                require(tradeViews[0].getItem(38).getType() == org.bukkit.Material.LIME_CONCRETE, "countdown did not unlock confirmation automatically");
                ctx.commerce().confirm(first, previousRevision[0]);
                require(!trade.confirmed(first.getUniqueId()), "stale view confirmed a changed offer");
                click(first, 38);
                require(trade.confirmed(first.getUniqueId()), "first confirmation failed");
                require(containsSkin(tradeViews[0], 9, a) && containsSkin(tradeViews[0], 14, b)
                        && collectionFixtures.stream().noneMatch(skin -> findSkin(tradeViews[0], skin, true) >= 0), "accepted view did not show only the exact offered skins");
                verifySelection(tradeViews[0].getItem(9), a, true, ctx);
                verifySelection(tradeViews[0].getItem(14), b, false, ctx);
                require(tradeViews[1].getItem(42).getType() == org.bukkit.Material.LIME_CONCRETE, "confirmation did not update partner status live");
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 38);
                require(!ctx.commerce().trade(first.getUniqueId()).confirmed(first.getUniqueId())
                        && tradeViews[1].getItem(42).getType() == org.bukkit.Material.RED_CONCRETE, "acceptance could not be withdrawn");
                require(collectionFixtures.stream().anyMatch(skin -> findSkin(tradeViews[0], skin, true) >= 0), "withdrawing acceptance did not restore skin selection");
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 38);
                TradeSession trade = ctx.commerce().trade(first.getUniqueId()); require(trade.confirmed(first.getUniqueId()), "accepting again failed");
                ctx.commerce().toggle(second, b.id()); ctx.commerce().toggle(second, b.id());
                require(!trade.confirmed(first.getUniqueId()), "offer edit did not invalidate confirmation");
                require(tradeViews[1].getItem(42).getType() == org.bukkit.Material.RED_CONCRETE
                        && tradeViews[0].getItem(38).getType() == org.bukkit.Material.CLOCK, "changed offer retained confirmed UI state");
                int offeredSlot = findSkin(tradeViews[0], a, true);
                if (offeredSlot >= 0) verifySelection(tradeViews[0].getItem(offeredSlot), a, true, ctx);
                require(first.getOpenInventory().getTopInventory() == tradeViews[0]
                        && second.getOpenInventory().getTopInventory() == tradeViews[1], "status update reopened a menu");
                verifyText(first); verifyText(second);
            })
            .thenCompose(v -> delay(plugin, 45))
            .thenRun(() -> {
                command(first, "trade accept Nobody");
                require(!ctx.commerce().trade(first.getUniqueId()).confirmed(first.getUniqueId()), "accept command ignored the named partner");
                command(first, "trade accept " + second.getName());
                require(ctx.commerce().trade(first.getUniqueId()).confirmed(first.getUniqueId()), "accept command did not accept the active offer");
                click(second, 38);
            })
            .thenCompose(v -> await(plugin, () -> ctx.commerce().trade(first.getUniqueId()) == null && ctx.profiles().get(first).get(b.id()) != null, "trade commit"))
            .thenRun(() -> {
                require(ctx.profiles().get(first).get(a.id()) == null && ctx.profiles().get(second).get(b.id()) == null, "old owners retained traded skins");
                var moved = ctx.profiles().get(second).get(a.id());
                require(moved != null && moved.owner().equals(second.getUniqueId()) && moved.id().equals(a.id())
                        && moved.floatValue() == a.floatValue() && moved.pattern() == a.pattern() && moved.kills() == a.kills(), "live trade changed skin values");
                require(!ctx.commerce().locked(a.id()) && !ctx.commerce().locked(b.id()), "trade left reservations behind");
                sender.sendMessage("PASS SIMPLE TRADE UI: both real player heads and coloured halves; own skins visible immediately; selected green models and checkmarks, removal restores normal models, accepted offers retain highlights; actual select/remove, pagination and one-click Accept; acceptance withdrawal, /trade accept for invitations and active offers; live counteroffers, cooldown, stale confirmation rejection and atomic exchange.");
                sender.sendMessage("PASS: trade reservation/equip/delete guards, cooldown and changed-offer confirmation reset with two connected Vanilla clients.");
            })
            .thenCompose(v -> main(plugin, ctx.commerce().repository().balance(first.getUniqueId(), ctx.commerce().startingBalance())))
            .thenAccept(balance -> before[0] = balance)
            .thenCompose(v -> main(plugin, ctx.commerce().repository().balance(second.getUniqueId(), ctx.commerce().startingBalance())))
            .thenAccept(balance -> { before[1] = balance; require(balance >= 120, "test buyer needs 120 Coins"); })
            .thenRun(() -> {
                var skin = ctx.profiles().get(first).get(b.id()); require(ctx.knives().equip(first, skin), "fixture equip failed");
                ctx.commerce().list(first, skin, 120);
            })
            .thenCompose(v -> await(plugin, () -> ctx.commerce().listings().stream().anyMatch(l -> l.skin().id().equals(b.id())), "market listing"))
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                require(ctx.profiles().get(first).get(b.id()).status() == SkinInstance.Status.LISTED
                        && !ctx.profiles().get(first).isEquipped(b.id()), "listed skin not reserved/unequipped");
                var listing = ctx.commerce().listings().stream().filter(l -> l.skin().id().equals(b.id())).findFirst().orElseThrow();
                new MarketMenu(ctx, first).own().open(); verifyText(first); protectMenu(first);
                new ListingMenu(ctx, second, listing).open(); verifyText(second); click(second, 11);
            })
            .thenCompose(v -> await(plugin, () -> ctx.profiles().get(second).get(b.id()) != null
                    && ctx.commerce().listings().stream().noneMatch(l -> l.skin().id().equals(b.id())), "market purchase"))
            .thenCompose(v -> main(plugin, ctx.commerce().repository().balance(first.getUniqueId(), ctx.commerce().startingBalance())))
            .thenAccept(balance -> require(balance == before[0] + 120, "seller not paid exact price"))
            .thenCompose(v -> main(plugin, ctx.commerce().repository().balance(second.getUniqueId(), ctx.commerce().startingBalance())))
            .thenAccept(balance -> require(balance == before[1] - 120, "buyer not charged exact price"))
            .thenRun(() -> {
                require(ctx.profiles().get(first).get(b.id()) == null, "seller retained sold skin");
                new SkinPickerMenu(ctx, first).open(); verifyText(first);
                new SellMenu(ctx, first, c, 250).open(); verifyText(first);
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> click(first, 29))
            .thenCompose(v -> await(plugin, () -> ctx.commerce().listings().stream().anyMatch(l -> l.skin().id().equals(c.id())), "cancel fixture listing"))
            .thenRun(() -> {
                var listing = ctx.commerce().listings().stream().filter(l -> l.skin().id().equals(c.id())).findFirst().orElseThrow(); ctx.commerce().cancelListing(first, listing);
            })
            .thenCompose(v -> await(plugin, () -> ctx.profiles().get(first).get(c.id()) != null
                    && ctx.profiles().get(first).get(c.id()).status() == SkinInstance.Status.OWNED, "listing withdrawal"))
            .thenRun(() -> {
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName()); ctx.commerce().toggle(first, c.id());
            })
            .thenCompose(v -> delay(plugin, 4))
            .thenRun(() -> {
                click(first, 49);
                require(ctx.commerce().trade(first.getUniqueId()) == null && ctx.commerce().trade(second.getUniqueId()) == null
                        && !ctx.commerce().locked(c.id()) && ctx.profiles().get(first).get(c.id()) != null, "cancel button did not release both players' offers");
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName()); ctx.commerce().toggle(first, c.id());
                first.closeInventory();
                require(ctx.commerce().trade(first.getUniqueId()) == null && ctx.commerce().trade(second.getUniqueId()) == null
                        && !ctx.commerce().locked(c.id()) && ctx.profiles().get(first).get(c.id()) != null, "menu close did not cancel/release trade");
                ctx.commerce().request(first, second); ctx.commerce().decline(second);
                ctx.commerce().accept(second, first.getName()); require(ctx.commerce().trade(first.getUniqueId()) == null, "declined request was accepted");
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName()); ctx.commerce().toggle(first, c.id());
                ctx.commerce().onQuit(new org.bukkit.event.player.PlayerQuitEvent(second, net.kyori.adventure.text.Component.empty()));
                require(ctx.commerce().trade(first.getUniqueId()) == null && !ctx.commerce().locked(c.id()), "quit listener did not cancel/release trade");
                sender.sendMessage("PASS: marketplace menus and icon protection; live listing, cosmetic unequip, buy/publish buttons, exact Coin payment, skin delivery, withdrawal, trade close/quit cancellation and request decline.");
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName()); ctx.commerce().toggle(first, c.id());
            })
            .thenCompose(v -> delay(plugin, 45))
            .thenRun(() -> {
                var button = first.getOpenInventory().getTopInventory().getItem(38);
                String warning = Text.plain(button.getItemMeta().displayName()).toLowerCase(java.util.Locale.ROOT);
                require(warning.contains("verschenken") || warning.contains("give away"), "one-sided gift did not warn that the giver receives nothing");
                click(first, 38); command(second, "trade accept");
            })
            .thenCompose(v -> await(plugin, () -> ctx.commerce().trade(first.getUniqueId()) == null && ctx.profiles().get(second).get(c.id()) != null, "gift delivery"))
            .thenRun(() -> {
                require(ctx.profiles().get(first).get(c.id()) == null && !ctx.commerce().locked(c.id()), "gift kept the old owner/reservation");
                sender.sendMessage("PASS: cancel button, clear gift warning and one-sided gift accepted through the button and /trade accept; original owners/locks cleared.");
            })
            .whenComplete((v, error) -> {
                ctx.commerce().cancel(first); ctx.commerce().cancel(second);
                if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Commerce runtime checks failed", error);
                for (Player player : List.of(first, second)) {
                    var profile = ctx.profiles().get(player); if (profile == null) continue;
                    for (SkinInstance fixture : fixtures) {
                        var skin = profile.get(fixture.id()); if (skin != null && skin.status() == SkinInstance.Status.OWNED)
                            new dev.plattnericus.cases.admin.AdminActions(ctx).remove(profile, skin);
                    }
                    player.closeInventory();
                }
                first.getInventory().setContents(firstInventory); second.getInventory().setContents(secondInventory);
                restoreSlots(first, firstSlots, ctx); restoreSlots(second, secondSlots, ctx);
                if (error == null) sender.sendMessage("PASS: all commerce runtime checks completed; test skins removed and physical inventories restored.");
            });
    }
    private static SkinInstance fixture(Player owner, String id) {
        return new SkinInstance(UUID.randomUUID(), owner.getUniqueId(), id, 0.01, 42, 123456L, true, 7, PatternInfo.NONE,
                "admin", SkinInstance.Origin.ADMIN, System.currentTimeMillis(), false, SkinInstance.Status.OWNED);
    }
    private static java.util.Map<EquipSlot, UUID> slots(Player player, CasesContext ctx) {
        var slots = new java.util.EnumMap<EquipSlot, UUID>(EquipSlot.class);
        for (var slot : EquipSlot.values()) { var id = ctx.profiles().get(player).equipped(slot); if (id != null) slots.put(slot, id); }
        return slots;
    }
    private static void restoreSlots(Player player, java.util.Map<EquipSlot, UUID> slots, CasesContext ctx) {
        for (var slot : EquipSlot.values()) {
            var id = slots.get(slot); var skin = id == null ? null : ctx.profiles().get(player).get(id);
            if (skin == null) ctx.knives().unequip(player, slot); else ctx.knives().equip(player, skin, slot);
        }
    }
    private static ItemStack[] snapshot(Player player) {
        var contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (contents[i] != null) contents[i] = contents[i].clone(); return contents;
    }
    private static void protectMenu(Player player) {
        var click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 4, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click); require(click.isCancelled(), "menu icon was movable");
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(org.bukkit.Material.DIAMOND),
                new ItemStack(org.bukkit.Material.DIAMOND), false, java.util.Map.of(4, new ItemStack(org.bukkit.Material.DIAMOND)));
        Bukkit.getPluginManager().callEvent(drag); require(drag.isCancelled(), "menu drag was allowed");
    }
    private static void click(Player player, int slot) {
        require(slot >= 0, "expected clickable skin/button not found");
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event); require(event.isCancelled(), "menu icon was not protected");
    }
    private static void command(Player player, String command) {
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getName() + " run " + command), "command dispatch failed");
    }
    private static boolean isHead(ItemStack item, Player owner) {
        if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) return false;
        var profile = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE);
        return profile != null && !profile.dynamic() && owner.getUniqueId().equals(profile.uuid())
                && owner.getName().equals(profile.name()) && profile.properties().containsAll(owner.getPlayerProfile().getProperties());
    }
    private static boolean containsSkin(Inventory inventory, int slot, SkinInstance skin) {
        ItemStack item = inventory.getItem(slot);
        return item != null && item.hasItemMeta() && item.getItemMeta().lore() != null
                && item.getItemMeta().lore().stream().anyMatch(line -> Text.plain(line).contains(skin.shortId()));
    }
    private static int findSkin(Inventory inventory, SkinInstance skin, boolean own) {
        for (int slot = 9; slot < 45; slot++) {
            if ((own ? slot % 9 < 4 : slot % 9 > 4) && containsSkin(inventory, slot, skin)) return slot;
        }
        return -1;
    }
    private static void verifySelection(ItemStack item, SkinInstance skin, boolean selected, CasesContext ctx) {
        require(item != null && item.getType() == dev.plattnericus.cases.items.SkinIcons.material(ctx.catalog().skin(skin.skinId())), "selection replaced the weapon icon");
        var meta = item.getItemMeta();
        require(Text.plain(meta.displayName()).startsWith("✓ ") == selected, "selection checkmark incorrect");
        if (ctx.settings().resourcePack().enabled()) {
            var expected = new org.bukkit.NamespacedKey(ctx.settings().resourcePack().namespace(),
                    (selected ? "trade/selected/" : "skin/") + skin.skinId());
            require(expected.equals(meta.getItemModel()), "wrong selected/normal item model: " + meta.getItemModel());
        } else require((meta.hasEnchantmentGlintOverride() && meta.getEnchantmentGlintOverride()) == selected, "vanilla selection fallback incorrect");
    }
    private static void verifyText(Player player) {
        require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu, "expected menu");
        for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue; var meta = item.getItemMeta();
            var lines = new java.util.ArrayList<net.kyori.adventure.text.Component>();
            if (meta.displayName() != null) lines.add(meta.displayName()); if (meta.lore() != null) lines.addAll(meta.lore());
            for (var line : lines) {
                String plain = Text.plain(line);
                require(!plain.matches(".*(?:market|trade|commerce)\\.[a-z-]+.*"), "raw translation key in menu: " + plain);
                String rendered = plain.replace("/market sell <ID> <price>", "");
                require(!rendered.matches(".*<(?:price|seller|currency|count|max|seconds|player|own|other)>.*"), "unresolved offer text: " + plain);
            }
        }
    }
    private static <T> CompletableFuture<T> main(Plugin plugin, CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error == null) result.complete(value); else result.completeExceptionally(error);
        })); return result;
    }
    private static CompletableFuture<Void> delay(Plugin plugin, int ticks) {
        CompletableFuture<Void> result = new CompletableFuture<>(); Bukkit.getScheduler().runTaskLater(plugin, () -> result.complete(null), ticks); return result;
    }
    private static CompletableFuture<Void> await(Plugin plugin, BooleanSupplier condition, String step) {
        CompletableFuture<Void> result = new CompletableFuture<>(); long deadline = System.currentTimeMillis() + 15000;
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (condition.getAsBoolean()) result.complete(null);
                else if (System.currentTimeMillis() > deadline) result.completeExceptionally(new IllegalStateException("Timed out: " + step));
            } catch (Exception failure) { result.completeExceptionally(failure); }
        }, 1, 2);
        result.whenComplete((v, e) -> task.cancel()); return result;
    }
    private static void require(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
}
