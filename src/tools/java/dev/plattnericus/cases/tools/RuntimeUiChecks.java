package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.menu.CasePreviewMenu;
import dev.plattnericus.cases.skin.*;
import dev.plattnericus.cases.tradein.*;
import dev.plattnericus.cases.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Exercises production menu and item listeners on an isolated real Paper server. */
public final class RuntimeUiChecks {
    private RuntimeUiChecks() { }
    public static void choices(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) {
        ctx.gallery().close(player); ctx.inspect().stop(player); player.closeInventory();
        var state = ctx.menuStates().get(player.getUniqueId()); var originalSort = state.sort;
        new dev.plattnericus.cases.gui.menu.SkinInventoryMenu(ctx, player).open();
        CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
        for (var sort : dev.plattnericus.cases.gui.MenuStates.Sort.values()) {
            work = work.thenCompose(v -> clicks(plugin, player, 47, 10 + sort.ordinal())).thenRun(() ->
                require(state.sort == sort && player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.menu.SkinInventoryMenu,
                    "inventory sort choice did not return or persist"));
        }
        work = work.thenRun(() -> {
            player.closeInventory(); ctx.gallery().open(player, null);
            try {
                var method = ctx.gallery().getClass().getDeclaredMethod("cycleSort", Player.class); method.setAccessible(true); method.invoke(ctx.gallery(), player);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.ChoiceMenu, "gallery sort choices missing");
        }).thenCompose(v -> clicks(plugin, player, 10)).thenRun(() -> {
            require(state.sort == dev.plattnericus.cases.gui.MenuStates.Sort.values()[0], "gallery sort not applied");
            ctx.gallery().close(player); state.sort = originalSort;
            new dev.plattnericus.cases.commerce.MarketMenu(ctx, player).open();
        });
        for (int i = 0; i < 5; i++) {
            int sort = i;
            work = work.thenCompose(v -> clicks(plugin, player, 47, 11 + sort)).thenRun(() ->
                require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.commerce.MarketMenu, "market sort did not return"));
        }
        work = work.thenCompose(v -> clicks(plugin, player, 51, 9, 8, 10)).thenRun(() -> {
            require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.commerce.MarketMenu, "market direct filters did not return");
            new dev.plattnericus.cases.commerce.SkinPickerMenu(ctx, player).open();
        });
        for (int i = 0; i < 4; i++) {
            int sort = i;
            work = work.thenCompose(v -> clicks(plugin, player, 5, 11 + sort)).thenRun(() ->
                require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.commerce.SkinPickerMenu, "sale sort did not return"));
        }
        work.thenCompose(v -> clicks(plugin, player, 1, 9, 3, 10)).thenRun(() -> {
            player.closeInventory();
            pass(plugin, sender, "PASS CHOICE MENUS: all inventory/gallery/market/sale sort choices, direct category/rarity filters and safe return paths.");
        }).whenComplete((v, error) -> {
            state.sort = originalSort; ctx.gallery().close(player); player.closeInventory();
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Choice menu audit failed", error);
        });
    }
    public static void spam(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) {
        require(ctx.openings().activeCount(player) == 0, "opening already active");
        ctx.gallery().close(player); ctx.inspect().stop(player); player.closeInventory();
        ItemStack[] saved = Arrays.stream(player.getInventory().getContents()).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        int held = player.getInventory().getHeldItemSlot();
        Set<UUID> before = new HashSet<>(ctx.profiles().get(player).all().stream().map(SkinInstance::id).toList());
        var def = ctx.catalog().caseDefinition("kilowatt_case"); var key = ctx.catalog().key(def.keyId());
        player.getInventory().clear(); player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(0, ctx.caseItems().caseItem(def, 12, true, ctx.messages(player)));
        player.getInventory().setItem(1, ctx.caseItems().keyItem(key, 12, true, ctx.messages(player)));
        new CasePreviewMenu(ctx, player, def, null).open();
        var work = delay(plugin, 2).thenRun(() -> {
            click(player, GuiItems.SLOT_CENTER);
            require(ctx.openings().activeCount(player) == 1, "single case not accepted");
            require(!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasePreviewMenu), "single opening left menu open");
        });
        for (int i = 0; i < 12; i++) {
            final int expected = Math.min(9, i + 2);
            work = work.thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
                for (int duplicate = 0; duplicate < 3; duplicate++) Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,
                        Action.RIGHT_CLICK_AIR, player.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND));
                require(ctx.openings().activeCount(player) == expected && ctx.openings().queuedCount(player) == 0, "spam admission or per-tick duplicate guard failed");
                require(!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasePreviewMenu), "spam reopened launch menu");
            });
        }
        work.thenCompose(v -> until(plugin, () -> ctx.openings().activeCount(player) == 0)).thenRun(() -> {
            require(ctx.profiles().get(player).owned().stream().filter(s -> !before.contains(s.id())).count() == 9, "spam reward count wrong");
            require(ctx.caseItems().count(player, "case", def.id()) == 3 && ctx.caseItems().count(player, "key", key.id()) == 3, "spam consumed more than nine pairs");
            require(ctx.profiles().journal().entries(player).isEmpty(), "spam receipt leaked");
            pass(plugin, sender, "PASS SPAM: single launch closes menu; repeated signed-item clicks add 1..9 sessions, same-tick duplicates ignored, excess clicks consume nothing, nine rewards and exactly nine signed pairs.");
        }).whenComplete((v, error) -> {
            player.closeInventory(); player.getInventory().setContents(saved); player.getInventory().setHeldItemSlot(held);
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Spam UI audit failed", error);
        });
    }

    public static void tradeIn(Plugin plugin, CommandSender sender, Player player, CasesContext ctx) {
        require(ctx.tradeIns().allowAdmin(), "admin fixtures must be enabled on isolated server");
        ctx.gallery().close(player); ctx.inspect().stop(player); player.closeInventory(); ctx.tradeIns().cancel(player);
        var source = ctx.catalog().caseDefinition("kilowatt_case");
        var defs = source.pool().values().stream().flatMap(List::stream).filter(s -> s.rarity().id().equals("mil_spec"))
                .sorted(Comparator.comparing(s -> s.weapon().name())).toList();
        require(defs.size() >= 3, "three eligible weapon types needed");
        var fixtures = new ArrayList<SkinInstance>(); long created = System.currentTimeMillis() + 100000;
        for (int i = 0; i < 48; i++) fixtures.add(new SkinInstance(UUID.randomUUID(), player.getUniqueId(), defs.get(i % 3).id(),
                (i + 1) / 100.0, i, i, false, 0, PatternInfo.NONE, "admin", SkinInstance.Origin.ADMIN,
                created + i, i < 3, SkinInstance.Status.OWNED));
        var weapon = defs.get(1).weapon();
        // Filter/sort expectations concern these fixtures, not skins left by other audits.
        var originalSkins = List.copyOf(ctx.profiles().get(player).all());
        var originalEquipment = new EnumMap<dev.plattnericus.cases.profile.EquipSlot, UUID>(dev.plattnericus.cases.profile.EquipSlot.class);
        for (var slot : dev.plattnericus.cases.profile.EquipSlot.values()) {
            UUID id = ctx.profiles().get(player).equipped(slot);
            if (id != null) originalEquipment.put(slot, id);
        }
        originalSkins.forEach(s -> ctx.profiles().removeLoaded(player.getUniqueId(), s.id()));
        CompletableFuture<Void> work = main(plugin, CompletableFuture.allOf(fixtures.stream().map(ctx.repository()::insert).toArray(CompletableFuture[]::new)))
                .thenRun(() -> {
                    fixtures.forEach(s -> ctx.profiles().addLoaded(player.getUniqueId(), s));
                    require(Bukkit.dispatchCommand(player, "tradeup"), "tradeup command unavailable");
                    require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeInMenu, "selection menu missing");
                });
        work = work.thenCompose(v -> clicks(plugin, player, 3, 13))
                .thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
                    require(player.getOpenInventory().getTopInventory().getItem(9).getType() != ctx.settings().opening().filler(), "normal filter hid normal inputs");
                }).thenCompose(v -> clicks(plugin, player, 3, 14)).thenRun(() -> {
                    require(player.getOpenInventory().getTopInventory().getItem(31).getType() == Material.GRAY_DYE
                            && player.getOpenInventory().getTopInventory().getItem(9).getType() == ctx.settings().opening().filler(), "StatTrak-only filter showed normal inputs");
                    click(player, 5);
                }).thenCompose(v -> clicks(plugin, player, 2, 12))
                .thenCompose(v -> clicks(plugin, player, 2, 13))
                .thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
                    require(player.getOpenInventory().getTopInventory().getItem(31).getType() == Material.GRAY_DYE
                            && player.getOpenInventory().getTopInventory().getItem(9).getType() == ctx.settings().opening().filler(), "rarity filter showed wrong-tier inputs");
                    click(player, 5);
                });
        for (int i = 0; i < TradeInSelection.Sort.values().length; i++) {
            final var expected = TradeInSelection.Sort.values()[(i + 1) % TradeInSelection.Sort.values().length];
            work = work.thenCompose(v -> clicks(plugin, player, 0, 11 + expected.ordinal())).thenRun(() -> {
                var menu = player.getOpenInventory().getTopInventory().getHolder(false);
                try { var field = TradeInMenu.class.getDeclaredField("sort"); field.setAccessible(true); require(field.get(menu) == expected, "sort choice did not apply"); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                var first = TradeInSelection.sorted(ctx.catalog(), fixtures, expected).getFirst();
                var name = Text.plain(player.getOpenInventory().getTopInventory().getItem(9).getItemMeta().displayName());
                require(name.equals(Text.plain(ctx.formatter(player).fullName(ctx.catalog().skin(first.skinId()), first))), "displayed order does not follow sorting");
            });
        }
        work.thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 50); require(player.getOpenInventory().getTopInventory().getItem(48).getType() == Material.SPECTRAL_ARROW, "second page unavailable");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> click(player, 1)).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            int slot = Arrays.stream(GuiItems.CONTENT).filter(s -> {
                var item = player.getOpenInventory().getTopInventory().getItem(s);
                return item != null && Text.plain(item.getItemMeta().displayName()).equals(weapon.name());
            }).findFirst().orElseThrow(() -> new AssertionError("direct weapon choice missing"));
            click(player, slot);
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            require(player.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeInMenu, "weapon choice did not return to selection");
            for (int slot : GuiItems.CONTENT) {
                var item = player.getOpenInventory().getTopInventory().getItem(slot);
                if (!item.getItemMeta().isHideTooltip()) require(Text.plain(item.getItemMeta().displayName()).contains(weapon.name()), "weapon filter leaked another weapon");
            }
            click(player, 7);
            var selected = ctx.tradeIns().selected(player);
            require(selected.size() == 10, "matching inputs did not auto-fill");
            for (UUID id : selected) {
                var skin = ctx.profiles().get(player).get(id);
                require(!skin.favorite() && ctx.catalog().skin(skin.skinId()).weapon().id().equals(weapon.id()), "fill ignored weapon filter or favorites");
            }
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 6);
            require(player.getOpenInventory().getTopInventory().getHolder(false).getClass().getSimpleName().equals("RewardsMenu")
                    && ctx.tradeIns().selected(player).size() == 10, "reward preview lost selected inputs");
            require(player.getOpenInventory().getTopInventory().getItem(9).getItemMeta().lore().stream().map(Text::plain)
                    .anyMatch(line -> line.startsWith("Chance: ")), "reward preview missing exact odds");
        }).thenCompose(v -> clicks(plugin, player, 45)).thenRun(() -> {
            require(ctx.tradeIns().selected(player).size() == 10, "back from reward preview cleared inputs");
        }).thenCompose(v -> clicks(plugin, player, 0, 22)).thenRun(() -> {
            require(ctx.tradeIns().selected(player).size() == 10, "back from sort choices cleared inputs");
            var held = new PlayerItemHeldEvent(player, player.getInventory().getHeldItemSlot(), (player.getInventory().getHeldItemSlot() + 1) % 9);
            Bukkit.getPluginManager().callEvent(held); require(held.isCancelled(), "tradein menu permitted scroll");
        }).thenCompose(v -> clicks(plugin, player, 49)).thenRun(() -> {
            require(player.getOpenInventory().getTopInventory().getSize() == 27 && ctx.tradeIns().selected(player).size() == 10, "review lost inputs");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 15); require(ctx.tradeIns().selected(player).size() == 10, "back from review cleared inputs");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 1); require(ctx.tradeIns().selected(player).size() == 10, "weapon chooser cleared selection");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> click(player, 4)).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 5); require(ctx.tradeIns().selected(player).size() == 10, "reset filters cleared selection");
        }).thenCompose(v -> delay(plugin, 2)).thenRun(() -> {
            click(player, 8); require(ctx.tradeIns().selected(player).isEmpty(), "clear selection failed");
        }).thenCompose(v -> clicks(plugin, player, 7)).thenRun(() -> {
            require(ctx.tradeIns().selected(player).size() == 10, "refill failed");
        }).thenCompose(v -> clicks(plugin, player, 0)).thenRun(() -> {
            player.closeInventory(); require(ctx.tradeIns().selected(player).isEmpty(), "closing sort choices leaked reservations");
            Bukkit.dispatchCommand(player, "tradeup");
        }).thenCompose(v -> clicks(plugin, player, 7, 6)).thenRun(() -> {
            player.closeInventory();
            require(ctx.tradeIns().selected(player).isEmpty(), "closing reward preview leaked reservations");
            pass(plugin, sender, "PASS TRADEIN UI: 48 direct-admin grants, five direct sorts, pagination, weapon/rarity/StatTrak filters, matching auto selection, exact reward odds, safe back navigation, closing submenus releases reservations, review and scroll protection.");
        }).whenComplete((v, error) -> {
            player.closeInventory(); ctx.tradeIns().cancel(player);
            fixtures.forEach(s -> { ctx.profiles().removeLoaded(player.getUniqueId(), s.id()); ctx.repository().removeOwned(player.getUniqueId(), s.id()); });
            originalSkins.forEach(s -> ctx.profiles().addLoaded(player.getUniqueId(), s));
            originalEquipment.forEach((slot, id) -> ctx.profiles().get(player).setEquipped(slot, id));
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Trade-in UI audit failed", error);
        });
    }

    static CompletableFuture<Void> clicks(Plugin plugin, Player player, int... slots) {
        CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
        for (int slot : slots) work = work.thenCompose(v -> delay(plugin, 2)).thenRun(() -> click(player, slot));
        return work;
    }
    private static void click(Player p, int slot) { Bukkit.getPluginManager().callEvent(new InventoryClickEvent(p.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL)); }
    private static CompletableFuture<Void> delay(Plugin plugin, int ticks) { var future = new CompletableFuture<Void>(); Bukkit.getScheduler().runTaskLater(plugin, () -> future.complete(null), ticks); return future; }
    private static <T> CompletableFuture<T> main(Plugin plugin, CompletableFuture<T> work) {
        var future = new CompletableFuture<T>(); work.whenComplete((v, e) -> Bukkit.getScheduler().runTask(plugin, () -> { if (e == null) future.complete(v); else future.completeExceptionally(e); })); return future;
    }
    private static CompletableFuture<Void> until(Plugin plugin, java.util.function.BooleanSupplier condition) {
        var future = new CompletableFuture<Void>(); new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed;
            @Override public void run() {
                if (condition.getAsBoolean()) { cancel(); future.complete(null); }
                else if ((elapsed += 2) > 600) { cancel(); future.completeExceptionally(new AssertionError("UI audit timed out")); }
            }
        }.runTaskTimer(plugin, 2, 2); return future;
    }
    private static void pass(Plugin plugin, CommandSender sender, String text) { sender.sendMessage(text); plugin.getLogger().info(text); }
    private static void require(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
