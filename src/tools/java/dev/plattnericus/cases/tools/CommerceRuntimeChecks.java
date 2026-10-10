package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.commerce.*;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Updated two-client integration checks. Run only on an isolated development server. */
public final class CommerceRuntimeChecks {
    private CommerceRuntimeChecks() { }
    public static void run(Plugin plugin, CommandSender sender, Player first, Player second, CasesContext ctx) {
        require(first != second && ctx.profiles().get(first) != null && ctx.profiles().get(second) != null, "two loaded players required");
        require(ctx.commerce().available(), "commerce not ready");
        var def = ctx.catalog().skins().stream().filter(s -> s.isKnife()).findFirst().orElseThrow();
        var fixtures = new java.util.ArrayList<SkinInstance>();
        for (int i = 0; i < 81; i++) fixtures.add(fixture(first, def.id()));
        SkinInstance a = fixtures.get(0), sale = fixtures.get(1), b = fixture(second, def.id(), SkinInstance.Origin.ADMIN_TRADE_IN); fixtures.add(b);
        ItemStack[] savedFirst = snapshot(first), savedSecond = snapshot(second);
        long[] openingBaseline = new long[2];
        long claimBaseline = ctx.commerce().payments().pending(first.getUniqueId());
        UUID[] listingId = new UUID[1];
        Runnable cleanup = () -> {
            ctx.commerce().cancel(first); ctx.commerce().cancel(second); first.closeInventory(); second.closeInventory();
            first.getInventory().setContents(savedFirst); second.getInventory().setContents(savedSecond);
            for (var fixture : fixtures) {
                var one = ctx.profiles().get(first).get(fixture.id()); var two = ctx.profiles().get(second).get(fixture.id());
                UUID owner = one != null ? first.getUniqueId() : second.getUniqueId();
                ctx.profiles().removeLoaded(owner, fixture.id()); ctx.repository().removeOwned(owner, fixture.id());
            }
        };
        main(plugin, CompletableFuture.allOf(fixtures.stream().map(ctx.repository()::insert).toArray(CompletableFuture[]::new)))
            .thenRun(() -> {
                first.getInventory().clear(); second.getInventory().clear();
                second.getInventory().setItem(0, new ItemStack(Material.EMERALD, 64));
                for (int i = 1; i < 6; i++) second.getInventory().setItem(i, new ItemStack(Material.EMERALD, 64));
                second.getInventory().setItemInOffHand(new ItemStack(Material.EMERALD, 16));
                fixtures.forEach(s -> ctx.profiles().addLoaded(s.owner(), s));
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName());
                require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu && second.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu, "trade menus missing");
                require(first.getOpenInventory().getTopInventory().getSize() == 54 && first.getOpenInventory().getTopInventory().getItem(2).getType() == Material.PLAYER_HEAD, "large trade menu/head missing");
                click(first, 46);
                require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof SkinPickerMenu && ctx.commerce().trade(first.getUniqueId()) != null, "collection navigation cancelled trade");
                require(first.getOpenInventory().getTopInventory().getSize() == 54, "picker not 54 slots");
                ctx.commerce().toggle(first, a.id()); ctx.commerce().toggle(second, b.id());
                require(ctx.commerce().locked(a.id()) && ctx.commerce().locked(b.id()), "offer not reserved");
                require(!ctx.knives().equip(first, a), "reserved skin equipped");
                require(Text.plain(second.getOpenInventory().getTopInventory().getItem(14).getItemMeta().displayName()).contains(def.weapon().name()), "partner offer not synchronized");
            }).thenCompose(v -> RuntimeUiChecks.clicks(plugin, first, 5, 12)).thenRun(() -> {
                require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof SkinPickerMenu
                        && ctx.commerce().locked(a.id()) && ctx.commerce().locked(b.id()), "sort choice cancelled trade or released offers");
            }).thenCompose(v -> RuntimeUiChecks.clicks(plugin, first, 1, 9, 3, 10)).thenRun(() -> {
                require(ctx.commerce().trade(first.getUniqueId()).items(first.getUniqueId()).contains(a.id()), "direct filters lost trade selection");
            }).thenCompose(v -> RuntimeUiChecks.clicks(plugin, first, 50)).thenRun(() -> {
                require(ctx.commerce().trade(first.getUniqueId()).items(first.getUniqueId()).contains(a.id()), "page switch lost selection");
                require(first.getOpenInventory().getTopInventory().getItem(48).getType() == Material.SPECTRAL_ARROW, "large collection page navigation failed");
                var illegal = new InventoryClickEvent(first.getOpenInventory(), InventoryType.SlotType.CONTAINER, 9, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
                Bukkit.getPluginManager().callEvent(illegal); require(illegal.isCancelled(), "hotbar GUI extraction not cancelled");
                var drag = new InventoryDragEvent(first.getOpenInventory(), new ItemStack(Material.DIAMOND), new ItemStack(Material.DIAMOND), false, java.util.Map.of(9, new ItemStack(Material.DIAMOND)));
                Bukkit.getPluginManager().callEvent(drag); require(drag.isCancelled(), "GUI drag accepted");
            }).thenCompose(v -> delay(plugin, 3)).thenRun(() -> {
                click(first, 45); require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu && ctx.commerce().trade(first.getUniqueId()) != null, "back cancelled trade");
            }).thenCompose(v -> delay(plugin, 45)).thenRun(() -> {
                ctx.commerce().confirm(first); require(!ctx.commerce().trade(first.getUniqueId()).ready(), "one confirmation completed trade");
                ctx.commerce().confirm(second);
            }).thenCompose(v -> until(plugin, () -> ctx.commerce().trade(first.getUniqueId()) == null))
            .thenRun(() -> {
                require(ctx.profiles().get(second).get(a.id()) != null && ctx.profiles().get(first).get(b.id()) != null, "atomic exchange failed");
                var caseSkin = ctx.profiles().get(second).get(a.id()); var tradeInSkin = ctx.profiles().get(first).get(b.id());
                require(caseSkin.traded() && tradeInSkin.traded() && tradeInSkin.origin().admin(), "trade provenance/admin lineage lost");
                String caseSource = ctx.catalog().caseDefinition(a.sourceCase()).name();
                require(ctx.formatter(second).lore(def, caseSkin, false).stream().map(Text::plain).anyMatch(line -> line.contains("Knife traded (" + caseSource + ")")), "case source not visible after live trade");
                require(ctx.formatter(first).lore(def, tradeInSkin, false).stream().map(Text::plain).anyMatch(line -> line.contains("Knife traded (TRADE IN)")), "trade-in source not visible after live trade");
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName());
                ctx.commerce().toggle(first, b.id());
            }).thenCompose(v -> RuntimeUiChecks.clicks(plugin, first, 46, 5)).thenRun(() -> {
                require(first.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.ChoiceMenu, "trade sort submenu missing");
                ctx.commerce().cancel(second);
                require(!(first.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.ChoiceMenu)
                        && !ctx.commerce().locked(b.id()), "partner cancellation left a stale sort submenu or reserved skin");
                ctx.commerce().list(first, sale, 320);
            }).thenCompose(v -> until(plugin, () -> ctx.commerce().listings().stream().anyMatch(l -> l.skin().id().equals(sale.id()))))
            .thenRun(() -> {
                var listing = ctx.commerce().listings().stream().filter(l -> l.skin().id().equals(sale.id())).findFirst().orElseThrow(); listingId[0] = listing.id();
                new ListingMenu(ctx, second, listing).open(); require(second.getOpenInventory().getTopInventory().getSize() == 54, "listing preview not large");
                ctx.commerce().buy(second, listing);
            }).thenCompose(v -> until(plugin, () -> ctx.profiles().get(second).get(sale.id()) != null && !ctx.commerce().payments().busy(second.getUniqueId())))
            .thenRun(() -> {
                require(ctx.commerce().inventoryBalance(second) == 80 && ctx.commerce().listing(listingId[0]) == null, "400-320 emerald payment or delisting failed");
                var transferred = ctx.profiles().get(second).get(sale.id());
                require(transferred.floatValue() == sale.floatValue() && transferred.pattern() == sale.pattern() && transferred.kills() == sale.kills(), "purchase changed instance details");
                for (int i = 0; i < 36; i++) first.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
                first.getInventory().setItem(5, new ItemStack(Material.EMERALD, 63)); first.getInventory().setItem(8, null);
                ctx.commerce().payments().claim(first);
            }).thenCompose(v -> until(plugin, () -> !ctx.commerce().payments().busy(first.getUniqueId()) && ctx.commerce().inventoryBalance(first) == 128))
            .thenCompose(v -> main(plugin, ctx.commerce().repository().emeralds().claims(first.getUniqueId())))
            .thenAccept(claims -> { require(claims.stream().mapToLong(c -> c.remaining()).sum() == claimBaseline + 255, "partial payout not exactly 65"); ctx.commerce().payments().claim(first); })
            .thenCompose(v -> delay(plugin, 10)).thenRun(() -> {
                require(ctx.commerce().inventoryBalance(first) == 128, "full inventory payout duplicated items");
                first.closeInventory(); second.closeInventory(); first.getInventory().clear(); second.getInventory().clear();
                var caseDef = ctx.catalog().caseDefinition("kilowatt_case"); var key = ctx.catalog().key(caseDef.keyId());
                openingBaseline[0] = ctx.profiles().get(first).all().size(); openingBaseline[1] = ctx.profiles().get(second).all().size();
                for (Player p : List.of(first, second)) {
                    p.getInventory().setItem(0, ctx.caseItems().caseItem(caseDef, 3, false, ctx.messages(p)));
                    p.getInventory().setItem(1, ctx.caseItems().keyItem(key, 3, true, ctx.messages(p)));
                    for (int i = 0; i < 3; i++) ctx.openings().open(p, caseDef, false, false);
                    require(ctx.openings().activeCount(p) == 3, "parallel opening overwritten or blocked");
                }
                ctx.commerce().request(first, second); ctx.commerce().accept(second, first.getName());
                require(ctx.commerce().trade(first.getUniqueId()) != null, "ongoing openings blocked an independent trade");
                ctx.commerce().cancel(first);
            }).thenCompose(v -> until(plugin, () -> ctx.openings().activeCount(first) == 0 && ctx.openings().activeCount(second) == 0))
            .thenRun(() -> {
                require(ctx.profiles().get(first).all().size() == openingBaseline[0] + 3 && ctx.profiles().get(second).all().size() == openingBaseline[1] + 3, "six parallel case rewards lost/duplicated");
                var caseDef = ctx.catalog().caseDefinition("kilowatt_case");
                for (Player p : List.of(first, second)) require(ctx.caseItems().count(p, "case", caseDef.id()) == 0 && ctx.caseItems().count(p, "key", caseDef.keyId()) == 0 && ctx.profiles().journal().entries(p).isEmpty(), "case/key consumption or journal completion failed");
                sender.sendMessage("PASS TWO-CLIENT COMMERCE: 54-slot collection pages and protected icons, selections/reservations retained, synchronized offers, two confirmations, exact Emerald purchase, partial/full-inventory claims, six independent openings across two players and concurrent direct trading.");
            }).whenComplete((v, error) -> {
                if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Two-client commerce audit failed", error);
                cleanup.run();
            });
    }
    public static void gold(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) throws ReflectiveOperationException {
        var field = dev.plattnericus.cases.storage.CommerceRepository.class.getDeclaredField("db"); field.setAccessible(true);
        var db = (dev.plattnericus.cases.storage.Database) field.get(ctx.commerce().repository());
        contract(plugin, player, ctx, db, SkinInstance.Origin.ADMIN, "kilowatt_case", true)
                .thenCompose(v -> contract(plugin, player, ctx, db, SkinInstance.Origin.CASE, "kilowatt_case", true))
                .thenCompose(v -> contract(plugin, player, ctx, db, SkinInstance.Origin.CASE, "kilowatt_case", false))
                .thenCompose(v -> contract(plugin, player, ctx, db, SkinInstance.Origin.CASE, "glove_case", true))
                .whenComplete((v, error) -> {
                    if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tradeup runtime audit failed", error);
                    else {
                        String message = "PASS TRADEUP RUNTIME: actual /tradeup command and protected selection/confirmation GUIs, ten normal inputs, five Covert inputs, knife and glove outputs, atomic SQL consumption, suppressed ADMIN announcement and exactly one claimed announcement for each eligible gold contract.";
                        sender.sendMessage(message); plugin.getLogger().info(message);
                    }
                });
    }
    private static CompletableFuture<Void> contract(Plugin plugin, Player player, CasesContext ctx, dev.plattnericus.cases.storage.Database db, SkinInstance.Origin origin, String caseId, boolean gold) {
        return contract(plugin, player, ctx, db, origin, caseId, gold, reward -> { });
    }
    public static void randomTradeUps(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) throws ReflectiveOperationException {
        var field = dev.plattnericus.cases.storage.CommerceRepository.class.getDeclaredField("db"); field.setAccessible(true);
        var db = (dev.plattnericus.cases.storage.Database) field.get(ctx.commerce().repository());
        var results = new java.util.HashSet<String>();
        CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
        for (int i = 0; i < 32; i++) work = work.thenCompose(v -> contract(plugin, player, ctx, db,
                SkinInstance.Origin.CASE, "kilowatt_case", false, reward -> results.add(reward.skinId())));
        work.whenComplete((v, error) -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Random trade-up audit failed", error);
            else {
                require(results.size() > 1, "32 independent contracts all returned the same skin");
                String message = "PASS RANDOM TRADEUP: 32 real ten-input contracts through selection and confirmation; " + results.size()
                        + " different eligible skins, correct provenance and atomic SQL consumption for every result.";
                sender.sendMessage(message); plugin.getLogger().info(message);
            }
        });
    }
    private static CompletableFuture<Void> contract(Plugin plugin, Player player, CasesContext ctx, dev.plattnericus.cases.storage.Database db, SkinInstance.Origin origin, String caseId, boolean gold, java.util.function.Consumer<SkinInstance> observe) {
        player.closeInventory(); var source = ctx.catalog().caseDefinition(caseId);
        var input = source.pool().values().stream().flatMap(List::stream).filter(def -> {
            var tier = dev.plattnericus.cases.tradein.TradeInRules.target(ctx.catalog(), def);
            return tier != null && tier.rareSpecial() == gold;
        }).findFirst().orElseThrow();
        var tier = dev.plattnericus.cases.tradein.TradeInRules.target(ctx.catalog(), input);
        int amount = dev.plattnericus.cases.tradein.TradeInRules.required(tier);
        var before = ctx.profiles().get(player).all().stream().map(SkinInstance::id).collect(java.util.stream.Collectors.toSet());
        var inputs = java.util.stream.IntStream.range(0, amount).mapToObj(i -> new SkinInstance(UUID.randomUUID(), player.getUniqueId(), input.id(), 0.2, 100 + i, i, false, 0, PatternInfo.NONE, source.id(), origin, System.currentTimeMillis() + i, false, SkinInstance.Status.OWNED)).toList();
        java.util.function.Supplier<SkinInstance> result = () -> ctx.profiles().get(player).owned().stream().filter(s -> !before.contains(s.id()) && !inputs.stream().anyMatch(i -> i.id().equals(s.id())) && ctx.catalog().skin(s.skinId()).rarity().id().equals(tier.id())).findFirst().orElse(null);
        var work = main(plugin, CompletableFuture.allOf(inputs.stream().map(ctx.repository()::insert).toArray(CompletableFuture[]::new))).thenRun(() -> {
            inputs.forEach(s -> ctx.profiles().addLoaded(player.getUniqueId(), s));
            require(Bukkit.dispatchCommand(player, "tradeup"), "/tradeup alias failed");
            require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.tradein.TradeInMenu, "/tradeup did not open selection menu");
        });
        for (int i = 0; i < amount; i++) {
            final int slot = GuiItems.CONTENT[i];
            work = work.thenCompose(v -> delay(plugin, 2)).thenRun(() -> click(player, slot));
        }
        return work.thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            require(ctx.tradeIns().selected(player).size() == amount, "GUI selection count wrong");
            click(player, 49); require(player.getOpenInventory().getTopInventory().getSize() == 27, "confirmation missing");
            require(result.get() == null && inputs.stream().allMatch(s -> ctx.profiles().get(player).get(s.id()) != null), "contract consumed before final confirmation");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> click(player, 11))
          .thenCompose(v -> until(plugin, () -> !ctx.tradeIns().busy(player) && result.get() != null)).thenCompose(v -> delay(plugin, 10))
          .thenCompose(v -> { UUID rewardId = result.get().id(); return main(plugin, db.run(c -> {
              try (var ps = c.prepareStatement("SELECT announced FROM " + db.table("trade_contracts") + " WHERE instance_id=?")) {
                  ps.setString(1, rewardId.toString()); try (var rows = ps.executeQuery()) { require(rows.next(), "contract not persisted"); return rows.getInt(1); }
              }
          })); }).thenAccept(announced -> {
              require(announced == (gold && origin == SkinInstance.Origin.CASE ? 1 : 0), "incorrect broadcast eligibility");
              require(inputs.stream().noneMatch(s -> ctx.profiles().get(player).get(s.id()) != null), "inputs not consumed");
              var reward = result.get(); require(reward.origin() == (origin.admin() ? SkinInstance.Origin.ADMIN_TRADE_IN : SkinInstance.Origin.TRADE_IN), "output origin wrong");
              require(source.skins(tier).contains(ctx.catalog().skin(reward.skinId())) && source.id().equals(reward.sourceCase()), "output outside legal random pool");
              for (boolean precise : new boolean[]{false, true}) require(ctx.formatter(player).lore(ctx.catalog().skin(reward.skinId()), reward, precise).stream()
                      .map(Text::plain).anyMatch(line -> line.contains("TRADE IN")), "trade-in output missing inventory provenance");
              if (caseId.equals("glove_case")) require(ctx.catalog().skin(reward.skinId()).weapon().category() == dev.plattnericus.cases.catalog.WeaponCategory.GLOVE && !reward.statTrak(), "glove contract output wrong");
              observe.accept(reward);
              ctx.profiles().removeLoaded(player.getUniqueId(), reward.id()); ctx.repository().removeOwned(player.getUniqueId(), reward.id()); player.closeInventory();
          });
    }
    public static void queueRecovery(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) {
        queueRecovery(plugin, sender, player, ctx, 2);
    }
    public static void queueRecovery(Plugin plugin, CommandSender sender, Player player, CasesContext ctx, int amount) {
        var source = ctx.catalog().caseDefinition("kilowatt_case");
        var before = ctx.profiles().get(player).all().stream().map(SkinInstance::id).collect(java.util.stream.Collectors.toSet());
        player.closeInventory(); player.getInventory().setItem(0, ctx.caseItems().caseItem(source, amount, false, ctx.messages(player)));
        player.getInventory().setItem(1, ctx.caseItems().keyItem(ctx.catalog().key(source.keyId()), amount, true, ctx.messages(player)));
        if (amount == 9) ctx.openings().openNine(player, source);
        else for (int i = 0; i < amount; i++) ctx.openings().open(player, source, false, false);
        until(plugin, () -> ctx.profiles().get(player).all().stream().filter(s -> !before.contains(s.id()) && s.status() == SkinInstance.Status.PENDING).count() == amount)
                .thenRun(() -> { String message = "READY RECOVERY: " + amount + " persisted PENDING rewards; disconnect, restart and reconnect to verify exact recovery.";
                    sender.sendMessage(message); plugin.getLogger().info(message); });
    }
    private static SkinInstance fixture(Player p, String skin) {
        return fixture(p, skin, SkinInstance.Origin.CASE);
    }
    private static SkinInstance fixture(Player p, String skin, SkinInstance.Origin origin) {
        return new SkinInstance(UUID.randomUUID(), p.getUniqueId(), skin, 0.012345, 271, 88123, true, 42, new PatternInfo("phase2", "Phase 2", "Pink Galaxy", 2, 0xff1234, 99.5), "chroma_case", origin, System.currentTimeMillis(), false, SkinInstance.Status.OWNED);
    }
    private static ItemStack[] snapshot(Player p) { return java.util.Arrays.stream(p.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new); }
    private static void click(Player p, int slot) { Bukkit.getPluginManager().callEvent(new InventoryClickEvent(p.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL)); }
    private static CompletableFuture<Void> delay(Plugin plugin, long ticks) { var future = new CompletableFuture<Void>(); Bukkit.getScheduler().runTaskLater(plugin, () -> future.complete(null), ticks); return future; }
    private static <T> CompletableFuture<T> main(Plugin plugin, CompletableFuture<T> work) {
        var future = new CompletableFuture<T>(); work.whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> { if (error != null) future.completeExceptionally(error); else future.complete(result); })); return future;
    }
    private static CompletableFuture<Void> until(Plugin plugin, BooleanSupplier condition) {
        var future = new CompletableFuture<Void>();
        new org.bukkit.scheduler.BukkitRunnable() { private int elapsed; @Override public void run() {
            try { if (condition.getAsBoolean()) { cancel(); future.complete(null); } else if ((elapsed += 2) > 600) { cancel(); future.completeExceptionally(new AssertionError("runtime operation timed out")); } }
            catch (Exception | AssertionError error) { cancel(); future.completeExceptionally(error); }
        } }.runTaskTimer(plugin, 2, 2); return future;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
