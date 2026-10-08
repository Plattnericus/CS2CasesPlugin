package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.MenuStates;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional development-only integration checks on a real Paper server. */
public final class RuntimeChecksPlugin extends JavaPlugin {
    @Override public void onEnable() {
        getCommand("mccasesdevcheck").setExecutor((sender, command, label, args) -> {
            if (args.length == 1 && args[0].equals("items")) {
                try { ItemRuntimeChecks.run(this, context()); }
                catch (Exception | AssertionError error) { getLogger().log(java.util.logging.Level.SEVERE, "Item runtime audit failed", error); }
                return true;
            }
            if (args.length < 1 || args.length > 3) return false;
            Player player = Bukkit.getPlayerExact(args[0]);
            if (player == null) { sender.sendMessage("Player is not online."); return true; }
            try {
                CasesContext ctx = context();
                if (args.length == 2 && args[1].equals("gold")) {
                    CommerceRuntimeChecks.gold(this, sender, player, ctx);
                } else if (args.length == 2 && args[1].equals("recovery")) {
                    CommerceRuntimeChecks.queueRecovery(this, sender, player, ctx);
                } else if (args.length == 3 && args[1].equals("commerce")) {
                    Player other = Bukkit.getPlayerExact(args[2]);
                    if (other == null || other.equals(player)) { sender.sendMessage("A second online player is required."); return true; }
                    CommerceRuntimeChecks.run(this, sender, player, other, ctx);
                } else if (args.length == 3 && args[1].equals("observer")) {
                    Player observer = Bukkit.getPlayerExact(args[2]);
                    if (observer == null || observer.equals(player)) { sender.sendMessage("A second online player is required."); return true; }
                    observerAudit(sender, player, observer, ctx);
                } else if (args.length == 2 && args[1].equals("inventory")) {
                    inventoryModesAudit(sender, player, ctx);
                } else if (args.length == 2 && args[1].equals("full")) {
                    fastAudit(sender, player, ctx);
                    check(sender, player, ctx, () -> handAndHotkeyAudit(sender, player, ctx));
                } else check(sender, player, ctx, () -> { });
            }
            catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Runtime checks failed", failure); }
            return true;
        });
    }

    private void inventoryModesAudit(CommandSender sender, Player player, CasesContext ctx) {
        Player other = Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(player) && ctx.profiles().get(p) != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("inventory audit needs another loaded online player"));
        ctx.gallery().close(player); player.closeInventory();
        require(Bukkit.dispatchCommand(player, "inventory"), "inventory command missing");
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                require(ctx.gallery().isOpen(player), "inventory did not open the hologram");
                require(Bukkit.dispatchCommand(player, "inventory " + other.getName()), "other-player inventory command missing");
                require(Bukkit.dispatchCommand(player, "inventory vanilla"), "vanilla inventory command missing");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        require(!ctx.gallery().isOpen(player) && player.getOpenInventory().getTopInventory().getHolder(false)
                                instanceof dev.plattnericus.cases.gui.menu.SkinInventoryMenu, "stale other-player request displaced the vanilla menu");
                        var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 9,
                                org.bukkit.event.inventory.ClickType.NUMBER_KEY, org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP, 0);
                        Bukkit.getPluginManager().callEvent(click); require(click.isCancelled(), "vanilla skin icon could be taken with a number key");
                        var drag = new org.bukkit.event.inventory.InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.DIAMOND),
                                new ItemStack(Material.DIAMOND), false, java.util.Map.of(9, new ItemStack(Material.DIAMOND)));
                        Bukkit.getPluginManager().callEvent(drag); require(drag.isCancelled(), "vanilla skin menu accepted a dragged item");
                        require(Bukkit.dispatchCommand(player, "inventory"), "hologram command missing after vanilla mode");
                        Bukkit.getScheduler().runTask(this, () -> {
                            try { require(ctx.gallery().isOpen(player), "switching back to hologram failed");
                                sender.sendMessage("PASS INVENTORY MODES: /inventory hologram -> /inventory vanilla protected skin menu -> hologram; stale other-player requests rejected, old display removed, number-key and drag protection."); }
                            catch (Exception failure) { report(failure); }
                            finally { ctx.gallery().close(player); player.closeInventory(); }
                        });
                    } catch (Exception failure) { report(failure); ctx.gallery().close(player); player.closeInventory(); }
                }, 2);
            } catch (Exception failure) { report(failure); ctx.gallery().close(player); player.closeInventory(); }
        });
    }

    private static CasesContext context() throws ReflectiveOperationException {
        var plugin = Bukkit.getPluginManager().getPlugin("MCCases");
        var bootstrap = plugin.getClass().getDeclaredField("bootstrap"); bootstrap.setAccessible(true);
        Object service = bootstrap.get(plugin);
        var runtime = service.getClass().getDeclaredField("runtime"); runtime.setAccessible(true);
        return (CasesContext) runtime.get(service);
    }

    private void check(CommandSender sender, Player player, CasesContext ctx, Runnable next) throws ReflectiveOperationException {
        var profile = ctx.profiles().get(player);
        require(profile != null && profile.equippedKnifeInstance() != null, "test needs an owned equipped knife");
        ctx.inspect().stop(player); ctx.gallery().close(player);
        var inventory = player.getInventory();
        ItemStack[] saved = inventory.getContents();
        for (int i = 0; i < saved.length; i++) if (saved[i] != null) saved[i] = saved[i].clone();
        int held = inventory.getHeldItemSlot();
        boolean sneaking = player.isSneaking();
        Runnable restore = () -> {
            ctx.inspect().stop(player); ctx.gallery().close(player);
            inventory.setContents(saved); inventory.setHeldItemSlot(held); player.setSneaking(sneaking);
            player.updateInventory();
        };
        try {
            player.setSneaking(false);
            inventory.setItem(0, new ItemStack(Material.BOW)); inventory.setItem(1, new ItemStack(Material.CROSSBOW));
            inventory.setHeldItemSlot(1);
            require(Bukkit.dispatchCommand(player, "inventory"), "/inventory alias was not registered");
            // Paper queues nested Brigadier commands until the current command has finished.
            Bukkit.getScheduler().runTask(this, () -> {
            try {
            require(ctx.gallery().isOpen(player), "/inventory alias did not open the gallery");
            Object view = galleryView(ctx, player);
            for (boolean sneak : new boolean[]{false, true}) {
                player.setSneaking(sneak);
                for (int slot = 0; slot < 9; slot++) {
                    if (slot == 1) continue;
                    int from = inventory.getHeldItemSlot();
                    PlayerItemHeldEvent switchSlot = new PlayerItemHeldEvent(player, from, slot);
                    Bukkit.getPluginManager().callEvent(switchSlot);
                    require(!switchSlot.isCancelled(), "gallery cancelled hotbar selection: sneak=" + sneak + ", slot=" + slot);
                    inventory.setHeldItemSlot(slot);
                    require(galleryView(ctx, player) == view, "hotbar selection closed or rebuilt the gallery");
                    require(inventory.getHeldItemSlot() == slot, "allowed hotbar selection did not commit the new slot");
                }
            }
            // Simulate the client/container race: the final packet requested slot 8, but the
            // server briefly still reports the previous slot. SkinGallery must reconcile it next tick.
            inventory.setHeldItemSlot(7);
            player.setSneaking(false);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    require(ctx.gallery().isOpen(player), "gallery closed after delayed refresh");
                    require(inventory.getHeldItemSlot() == 8,
                            "latest scrolled slot changed after delayed refresh: " + inventory.getHeldItemSlot());
                    galleryPageScrollAudit(player, ctx, inventory);
                    galleryClickStabilityAudit(player, ctx);
                    galleryDistanceAudit(player, ctx);
                    PlayerItemHeldEvent releasedSwitch = new PlayerItemHeldEvent(player, 1, 0);
                    Bukkit.getPluginManager().callEvent(releasedSwitch);
                    require(!releasedSwitch.isCancelled(), "hotbar remained locked after leaving the gallery");
                    inventory.setHeldItemSlot(0);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try { checkInspectSwitch(sender, player, ctx, profile, inventory, restore, next); }
                        catch (Exception failure) { report(failure); restore.run(); }
                    }, 2);
                } catch (Exception failure) { report(failure); restore.run(); }
            }, 12);
            } catch (Exception failure) { report(failure); restore.run(); }
            });
        } catch (Exception failure) { restore.run(); throw failure; }
    }

    private void checkInspectSwitch(CommandSender sender, Player player, CasesContext ctx,
                                   dev.plattnericus.cases.profile.PlayerProfile profile,
                                   org.bukkit.inventory.PlayerInventory inventory, Runnable restore, Runnable next) {
        require(inventory.getHeldItemSlot() == 0 && inventory.getItemInMainHand().getType() == Material.BOW,
                "bow became a crossbow after cosmetic refresh");
        inventory.setHeldItemSlot(1);
        require(ctx.inspect().start(player, profile.equippedKnifeInstance(), false), "inspect failed to start");
        PlayerItemHeldEvent inspectSwitch = new PlayerItemHeldEvent(player, 1, 0);
        Bukkit.getPluginManager().callEvent(inspectSwitch);
        require(!inspectSwitch.isCancelled(), "inspect cancelled slot change");
        inventory.setHeldItemSlot(0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean passed = false;
            try {
                require(!ctx.inspect().isInspecting(player), "inspect was not removed after slot change");
                require(inventory.getHeldItemSlot() == 0 && inventory.getItemInMainHand().getType() == Material.BOW,
                        "inspect restored the old crossbow slot");
                sender.sendMessage("PASS: /inventory keeps the gallery open while scrolling/using number keys in both sneak states; 8/9.999 blocks stay open, exactly 10 and beyond close with complete cleanup, cancelled moves ignored; crossbow -> bow / inspect slot changes preserve the selected weapon.");
                passed = true;
            } catch (Exception failure) { report(failure); }
            finally { restore.run(); }
            if (passed) next.run();
        }, 2);
    }

    private static Object galleryView(CasesContext ctx, Player player) throws ReflectiveOperationException {
        return ((java.util.Map<?, ?>) field(ctx.gallery(), "views")).get(player.getUniqueId());
    }

    /** A favorite click must update the card in place instead of removing and respawning the wall. */
    private static void galleryClickStabilityAudit(Player player, CasesContext ctx) throws Exception {
        Object view = galleryView(ctx, player);
        var targets = (java.util.Map<?, ?>) field(view, "targets");
        var entry = targets.entrySet().stream().filter(candidate -> {
            try {
                var accessor = candidate.getValue().getClass().getDeclaredMethod("tooltipAt");
                accessor.setAccessible(true);
                return accessor.invoke(candidate.getValue()) == null;
            } catch (ReflectiveOperationException failure) {
                return false;
            }
        }).findFirst().orElseThrow(() -> new IllegalStateException("no skin hitbox available for click audit"));
        var entities = (java.util.List<?>) field(view, "entities");
        var id = (java.util.UUID) entry.getKey();
        var hitbox = entities.stream().map(org.bukkit.entity.Entity.class::cast)
                .filter(entity -> entity.getUniqueId().equals(id)).findFirst().orElseThrow();
        var click = ctx.gallery().getClass().getDeclaredMethod("click", Player.class, org.bukkit.entity.Entity.class, boolean.class);
        click.setAccessible(true);
        boolean sneaking = player.isSneaking();
        player.setSneaking(true);
        click.invoke(ctx.gallery(), player, hitbox, false);
        require(galleryView(ctx, player) == view, "favorite click rebuilt the gallery view");
        require(entities.stream().map(org.bukkit.entity.Entity.class::cast).allMatch(org.bukkit.entity.Entity::isValid),
                "favorite click removed a gallery display entity");
        click.invoke(ctx.gallery(), player, hitbox, false); // restore the original favorite flag
        require(galleryView(ctx, player) == view, "second favorite click rebuilt the gallery view");
        player.setSneaking(sneaking);
    }

    /** Hovering a hologram target turns the same wheel/key packet into a page turn. */
    private static void galleryPageScrollAudit(Player player, CasesContext ctx,
                                               org.bukkit.inventory.PlayerInventory inventory) throws Exception {
        Object view = galleryView(ctx, player);
        var targets = (java.util.Map<?, ?>) field(view, "targets");
        Object target = targets.values().stream().findFirst().orElseThrow();
        var hovered = view.getClass().getDeclaredField("hovered");
        hovered.setAccessible(true);
        hovered.set(view, target);
        var state = ctx.menuStates().get(player.getUniqueId());
        int beforePage = state.page;
        int beforeSlot = inventory.getHeldItemSlot();
        int count = dev.plattnericus.cases.gui.SkinQuery.run(ctx.catalog(),
                ((dev.plattnericus.cases.profile.PlayerProfile) field(view, "owner")).owned(), state).size();
        int perPage = ctx.settings().skinInventory().columns() * ctx.settings().skinInventory().rows();
        int pages = Math.max(1, (count + perPage - 1) / perPage);
        PlayerItemHeldEvent pageScroll = new PlayerItemHeldEvent(player, beforeSlot, (beforeSlot + 1) % 9);
        Bukkit.getPluginManager().callEvent(pageScroll);
        require(pageScroll.isCancelled(), "hologram scroll was treated as a hotbar change");
        require(inventory.getHeldItemSlot() == beforeSlot, "hologram scroll changed the held slot");
        if (pages > 1) {
            require(state.page == Math.min(beforePage + 1, pages - 1), "hologram scroll did not advance the page");
        }
        ctx.gallery().close(player);
        ctx.gallery().open(player, MenuStates.Category.ALL);
    }

    /** Exercise real Paper listeners without moving the client's camera or changing its location. */
    private static void galleryDistanceAudit(Player player, CasesContext ctx) throws ReflectiveOperationException {
        Object view = galleryView(ctx, player);
        var base = ((org.bukkit.Location) field(view, "base")).clone();
        var entities = ((java.util.List<?>) field(view, "entities")).stream().map(org.bukkit.entity.Entity.class::cast).toList();
        var near = base.clone().add(9.998, 0, 0);
        var inside = base.clone().add(9.999, 0, 0);
        var boundary = base.clone().add(10, 0, 0);
        var cancelled = new org.bukkit.event.player.PlayerMoveEvent(player, near, boundary);
        cancelled.setCancelled(true);
        Bukkit.getPluginManager().callEvent(cancelled);
        require(ctx.gallery().isOpen(player), "cancelled movement closed the gallery");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerMoveEvent(player, player.getLocation(), base.clone().add(8.01, 0, 0)));
        require(ctx.gallery().isOpen(player), "gallery still used the old eight-block distance");
        var slightMove = new org.bukkit.event.player.PlayerMoveEvent(player, near, inside);
        require(!slightMove.hasChangedBlock(), "distance test must stay within the same block");
        Bukkit.getPluginManager().callEvent(slightMove);
        require(ctx.gallery().isOpen(player), "gallery closed before ten blocks");
        // Invoke the production listener directly for the fractional-boundary case; Paper may
        // normalize a synthetic sub-block movement event before dispatching it.
        ctx.gallery().onMove(new org.bukkit.event.player.PlayerMoveEvent(player, inside, boundary));
        require(!ctx.gallery().isOpen(player), "gallery remained open at ten blocks");
        require(entities.stream().noneMatch(org.bukkit.entity.Entity::isValid), "gallery entities leaked after distance close");
        var hitboxes = (java.util.Map<?, ?>) field(ctx.gallery(), "byHitbox");
        require(entities.stream().noneMatch(entity -> hitboxes.containsKey(entity.getUniqueId())), "gallery hitboxes leaked after close");
        ctx.gallery().open(player, MenuStates.Category.ALL);
        var newBase = (org.bukkit.Location) field(galleryView(ctx, player), "base");
        ctx.gallery().onMove(new org.bukkit.event.player.PlayerMoveEvent(player, player.getLocation(), newBase.clone().add(11, 0, 0)));
        require(!ctx.gallery().isOpen(player), "gallery remained open beyond ten blocks");
    }

    private void report(Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Runtime checks failed", failure); }
    private static void require(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }

    private static ItemStack[] snapshot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (contents[i] != null) contents[i] = contents[i].clone();
        return contents;
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name); field.setAccessible(true);
        return field.get(instance);
    }

    /** Two vanilla clients: verify the actual client locales and Paper's per-player entity visibility. */
    private void observerAudit(CommandSender sender, Player owner, Player observer, CasesContext ctx) {
        require(!ctx.openings().isOpening(owner), "wait until the current opening finishes");
        boolean fixedEnglish = ctx.messages().language().equals("en")
                && ctx.messages(owner) == ctx.messages() && ctx.messages(observer) == ctx.messages();
        require(fixedEnglish || owner.locale().getLanguage().equals("de") && observer.locale().getLanguage().equals("fr"),
                "client-language audit needs actual German and French Minecraft settings");
        require(fixedEnglish || ctx.messages(owner).language().equals("de") && ctx.messages(observer).language().equals("fr"),
                "plugin did not respect the configured language policy");
        ItemStack[] saved = snapshot(observer);
        int held = observer.getInventory().getHeldItemSlot();
        boolean bodyHand = ctx.inspect().bodyHandMode(owner);
        ctx.inspect().stop(owner); ctx.gallery().close(owner);
        observer.closeInventory(); observer.teleport(owner.getLocation().clone().add(2, 0, 0));
        Runnable restore = () -> {
            ctx.inspect().stop(owner); ctx.inspect().setBodyHandMode(owner, bodyHand);
            observer.closeInventory(); observer.getInventory().setContents(saved);
            observer.getInventory().setHeldItemSlot(held); observer.updateInventory();
        };
        var key = ctx.catalog().key(ctx.catalog().caseDefinition("kilowatt_case").keyId());
        var germanKey = ctx.caseItems().keyItem(key, 3, true, ctx.messages(owner));
        var frenchKey = ctx.caseItems().keyItem(key, 3, true, ctx.messages(observer));
        var identity = ctx.caseItems().identify(germanKey);
        require(germanKey.getItemMeta().displayName().equals(frenchKey.getItemMeta().displayName()) == fixedEnglish, "signed key names ignored language policy");
        observer.getInventory().setItem(0, germanKey);
        // Close/open events use the production coalescing listener, not a manual text replacement.
        new dev.plattnericus.cases.shop.ShopMenu(ctx, observer).open();
                require(observer.getOpenInventory().title().equals(ctx.messages(observer).get("shop.title")), "shop title ignored language policy");
        observer.closeInventory();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var translated = observer.getInventory().getItem(0);
                require(translated.getAmount() == 3 && identity.equals(ctx.caseItems().identify(translated))
                        && translated.getItemMeta().displayName().equals(frenchKey.getItemMeta().displayName()),
                        "production locale refresh lost item amount/signature or ignored language policy");
                var token = observer.getInventory().getItem(ctx.settings().reservedSlot().slot());
                if (ctx.settings().reservedSlot().enabled()) require(token != null && token.getItemMeta().displayName().equals(ctx.messages(observer).item("slot.name")),
                        "reserved inventory token is not French");
                else require(java.util.Arrays.stream(observer.getInventory().getContents()).noneMatch(new dev.plattnericus.cases.slot.ReservedSlotService(ctx)::isToken),
                        "disabled inventory shortcut returned after locale refresh");
                ctx.inspect().setBodyHandMode(owner, false);
                require(ctx.inspect().start(owner, ctx.profiles().get(owner).equippedKnifeInstance(), false), "owner inspect failed");
                Object session = ((java.util.Map<?, ?>) field(ctx.inspect(), "sessions")).get(owner.getUniqueId());
                var eyeAnchor = (org.bukkit.Location) field(session, "lastAnchor");
                var handAnchor = (org.bukkit.Location) field(session, "lastHandAnchor");
                require(eyeAnchor.distance(handAnchor) > .5, "observer is using the owner's eye view");
                require(handAnchor.getPitch() == 0 && Math.abs(handAnchor.getY() - owner.getEyeLocation().getY() + .8) < .00001
                        && Math.abs(handAnchor.getYaw() - owner.getBodyYaw()) < .00001, "observer scene is not body-hand anchored");
                require(Math.abs((Float) field(session, "scale") - 1.24f) < .00001
                        && Math.abs((Float) field(session, "handScale") - 1.3f) < .00001, "asset scales are not doubled");
                java.util.List<org.bukkit.entity.Display> displays = new java.util.ArrayList<>();
                int privateParts = 0, publicParts = 0;
                for (Object part : (java.util.List<?>) field(session, "parts")) {
                    var entityMethod = part.getClass().getDeclaredMethod("entity"); entityMethod.setAccessible(true);
                    var observersMethod = part.getClass().getDeclaredMethod("observers"); observersMethod.setAccessible(true);
                    var entity = (org.bukkit.entity.Display) entityMethod.invoke(part);
                    boolean publicScene = (Boolean) observersMethod.invoke(part);
                    displays.add(entity);
                    require(owner.canSee(entity) != publicScene && observer.canSee(entity) == publicScene,
                            "owner / observer sees the wrong scene or both scenes");
                    if (publicScene) publicParts++; else privateParts++;
                }
                require(privateParts > 0 && privateParts == publicParts, "observer scene parts missing");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        require(ctx.inspect().isInspecting(owner), "inspect stopped before interpolation check");
                        ctx.inspect().stop(owner);
                        require(displays.stream().noneMatch(org.bukkit.entity.Entity::isValid), "display scene leaked after stop");
                        sender.sendMessage("PASS TWO CLIENTS: actual locales " + owner.locale() + " / " + observer.locale()
                                + "; plugin language " + ctx.messages(owner).language() + " / " + ctx.messages(observer).language()
                                + "; menu and signed key refresh; inventory shortcut setting respected; doubled owner/observer scales; separate visible scenes, body yaw/hand anchor, interpolation and complete cleanup.");
                    } catch (Exception failure) { report(failure); }
                    finally { restore.run(); }
                }, 12);
            } catch (Exception failure) { restore.run(); report(failure); }
        }, 12);
    }

    private void fastAudit(CommandSender sender, Player player, CasesContext ctx) throws Exception {
        require(!ctx.openings().isOpening(player), "wait until the player's current opening finishes");
        ctx.inspect().stop(player); ctx.gallery().close(player); player.closeInventory();
        var inv = player.getInventory();
        ItemStack[] saved = snapshot(player);
        int held = inv.getHeldItemSlot();
        var originalJournal = player.getPersistentDataContainer().get(ctx.keys().journal, org.bukkit.persistence.PersistentDataType.LIST.strings());
        var shopFile = new java.io.File(ctx.plugin().getDataFolder(), "shop.yml");
        java.io.File temporary = java.io.File.createTempFile("mccases-shop-audit", ".yml");
        try {
            inv.clear();
            var def = ctx.catalog().caseDefinition("kilowatt_case");
            var key = ctx.catalog().key(def.keyId());
            var items = ctx.caseItems();
            var caseItem = items.caseItem(def, 2, false, ctx.messages(player));
            var keyItem = items.keyItem(key, 2, true, ctx.messages(player));
            require(items.identify(caseItem) != null && items.identify(keyItem).test(), "genuine signed items rejected");
            var forged = caseItem.clone();
            forged.editMeta(m -> m.getPersistentDataContainer().set(ctx.keys().itemId, org.bukkit.persistence.PersistentDataType.STRING, "gamma_case"));
            require(items.identify(forged) == null, "forged item id accepted");
            forged = caseItem.clone();
            forged.editMeta(m -> m.getPersistentDataContainer().set(ctx.keys().testItem, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1));
            require(items.identify(forged) == null, "forged test flag accepted");
            require(items.identify(items.caseIcon(def, ctx.messages(player))) == null, "GUI icon can be spent as a case");
            inv.setItem(0, caseItem);
            require(items.consumePair(player, def.id(), key.id()) == null && inv.getItem(0).getAmount() == 2,
                    "missing key consumed a case");
            inv.setItem(1, keyItem);
            var consumed = items.consumePair(player, def.id(), key.id());
            require(consumed != null && !consumed.caseTest() && consumed.keyTest() && consumed.test()
                    && inv.getItem(0).getAmount() == 1 && inv.getItem(1).getAmount() == 1, "pair consumption or test-key origin");

            inv.setItemInOffHand(keyItem.clone());
            player.openInventory(Bukkit.createInventory(null, org.bukkit.event.inventory.InventoryType.LOOM));
            var offhand = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                    org.bukkit.event.inventory.ClickType.SWAP_OFFHAND, org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP);
            Bukkit.getPluginManager().callEvent(offhand);
            require(offhand.isCancelled(), "workstation accepted a case/key via offhand swap");
            player.closeInventory();
            sender.sendMessage("PASS: signed items, forged markers, atomic pair consumption and workstation offhand protection.");

            var profile = ctx.profiles().get(player);
            var knife = profile.equippedKnifeInstance();
            require(knife != null, "equipped knife needed for cosmetic audit");
            for (Material type : new Material[]{Material.DIAMOND_SWORD, Material.BOW, Material.CROSSBOW}) {
                ItemStack original = new ItemStack(type);
                original.editMeta(m -> {
                    m.displayName(net.kyori.adventure.text.Component.text("Original audit name"));
                    m.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Original audit lore")));
                    m.setEnchantmentGlintOverride(false);
                    m.setItemModel(org.bukkit.NamespacedKey.minecraft("stick"));
                    ((org.bukkit.inventory.meta.Damageable) m).setDamage(7);
                });
                original.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 2);
                var applied = ctx.knives().cosmetics().apply(original.clone(), ctx.catalog().skin(knife.skinId()), knife,
                        ctx.formatter(player), ctx.messages(player), ctx.settings(), ctx.catalog().version());
                require(applied.getType() == type, "cosmetics changed item material");
                require(ctx.knives().cosmetics().strip(applied).equals(original), "cosmetics lost original metadata on " + type);
            }
            require(!ctx.knives().equip(player, knife.copyWithStatus(dev.plattnericus.cases.skin.SkinInstance.Status.OWNED)),
                    "non-authoritative skin copy could equip");
            require(!ctx.knives().equip(player, knife, dev.plattnericus.cases.profile.EquipSlot.BOW), "knife equipped in weapon slot");
            sender.sendMessage("PASS: sword/bow/crossbow cosmetics preserve material, durability, enchantments and original metadata; equip guards.");

            inv.clear();
            var reserved = new dev.plattnericus.cases.slot.ReservedSlotService(ctx);
            reserved.ensure(player);
            int tokenSlot = ctx.settings().reservedSlot().slot();
            if (ctx.settings().reservedSlot().enabled()) {
            var token = inv.getItem(tokenSlot).clone();
            inv.setItem(2, token.clone()); reserved.ensure(player);
            require(inv.getItem(2) == null && reserved.isToken(inv.getItem(tokenSlot)), "duplicate reserved token survived");
            var drop = player.getWorld().dropItem(player.getLocation(), token.clone());
            try {
                var dropEvent = new org.bukkit.event.player.PlayerDropItemEvent(player, drop);
                Bukkit.getPluginManager().callEvent(dropEvent);
                require(dropEvent.isCancelled(), "reserved item can be dropped");
            } finally { drop.remove(); }
            var swap = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player, token, new ItemStack(Material.AIR));
            Bukkit.getPluginManager().callEvent(swap);
            require(swap.isCancelled(), "reserved item can be hand-swapped");
            } else {
                ItemStack legacy = new ItemStack(Material.NETHER_STAR);
                legacy.editMeta(meta -> meta.getPersistentDataContainer().set(ctx.keys().menuToken, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1));
                inv.setItem(2, legacy.clone()); inv.setItemInOffHand(legacy.clone()); player.setItemOnCursor(legacy.clone());
                ItemStack ordinary = new ItemStack(Material.NETHER_STAR);
                inv.setItem(tokenSlot, new ItemStack(Material.DIAMOND)); inv.setItem(3, ordinary.clone());
                reserved.ensure(player);
                require(java.util.Arrays.stream(inv.getContents()).noneMatch(reserved::isToken)
                        && !reserved.isToken(player.getItemOnCursor()), "disabled shortcut left legacy tokens behind");
                require(inv.getItem(tokenSlot).getType() == Material.DIAMOND && ordinary.equals(inv.getItem(3)), "shortcut cleanup removed a normal item or occupied the free slot");
                var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 17,
                        org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
                Bukkit.getPluginManager().callEvent(click);
                require(!click.isCancelled() && !ctx.gallery().isOpen(player), "disabled shortcut still locked a slot or opened the gallery");
                inv.clear();
                sender.sendMessage("PASS: command-only skin inventory; legacy marked stars/cursor/offhand removed; ordinary stars retained and former reserved slot usable.");
            }

            var offer = ctx.shop().offers().stream().filter(o -> o.type().equals("case") && o.id().equals(def.id())).findFirst().orElseThrow();
            require(offer.price() < 64, "audit assumes ordinary configured case price");
            inv.setItem(0, new ItemStack(ctx.shop().currency(), 64));
            require(ctx.shop().buy(player, offer, 1) == dev.plattnericus.cases.shop.ShopService.Result.OK
                    && ctx.shop().balance(player) == 64 - offer.price() && items.count(player, "case", def.id()) == 1, "single shop purchase");
            require(ctx.shop().buy(player, offer, 5) == dev.plattnericus.cases.shop.ShopService.Result.OK
                    && ctx.shop().balance(player) == 64 - offer.price() * 6 && items.count(player, "case", def.id()) == 6, "five-item shop purchase");
            require(ctx.shop().buy(player, offer, 0) == dev.plattnericus.cases.shop.ShopService.Result.UNAVAILABLE, "zero-price quantity accepted");
            inv.clear(); reserved.ensure(player);
            var renamed = new ItemStack(ctx.shop().currency(), 64);
            renamed.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Renamed currency")));
            inv.setItem(0, renamed);
            require(ctx.shop().balance(player) == 0 && ctx.shop().buy(player, offer, 1) == dev.plattnericus.cases.shop.ShopService.Result.NOT_ENOUGH,
                    "modified currency accepted");
            for (int i = 0; i < 36; i++) if (!ctx.settings().reservedSlot().enabled() || i != tokenSlot) inv.setItem(i, new ItemStack(Material.STONE, 64));
            inv.setItem(0, new ItemStack(ctx.shop().currency(), offer.price() + 1));
            var before = snapshot(player);
            require(ctx.shop().buy(player, offer, 1) == dev.plattnericus.cases.shop.ShopService.Result.INVENTORY_FULL
                    && java.util.Arrays.equals(before, inv.getContents()), "failed shop purchase charged/lost items");
            inv.setItem(0, new ItemStack(ctx.shop().currency(), offer.price()));
            require(ctx.shop().buy(player, offer, 1) == dev.plattnericus.cases.shop.ShopService.Result.OK
                    && items.count(player, "case", def.id()) == 1 && ctx.shop().balance(player) == 0, "payment-freed slot rejected");
            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(shopFile);
            config.set("cases.prices." + def.id(), Integer.MAX_VALUE); config.save(temporary);
            ctx.shop().load(temporary, getLogger()::warning);
            var expensive = ctx.shop().offers().stream().filter(o -> o.type().equals("case") && o.id().equals(def.id())).findFirst().orElseThrow();
            require(ctx.shop().buy(player, expensive, 5) == dev.plattnericus.cases.shop.ShopService.Result.NOT_ENOUGH, "price multiplication overflow");
            config.set("cases.exclude", java.util.List.of(def.id())); config.save(temporary);
            ctx.shop().load(temporary, getLogger()::warning);
            require(ctx.shop().buy(player, offer, 1) == dev.plattnericus.cases.shop.ShopService.Result.UNAVAILABLE, "stale/disabled offer still purchasable");
            sender.sendMessage("PASS: shop quantity/payment/stacking, full inventory rollback, payment-freed slot, modified currency, overflow and stale offers; reserved-item guards.");

            var journal = ctx.profiles().journal();
            var pending = knife.copyWithStatus(dev.plattnericus.cases.skin.SkinInstance.Status.PENDING);
            int entries = journal.entries(player).size();
            journal.add(player, pending);
            require(journal.entries(player).size() == entries + 1, "pending journal add/read");
            journal.remove(player, pending.id());
            require(journal.entries(player).size() == entries, "pending journal remove");
            var badEntries = new java.util.ArrayList<String>();
            badEntries.add("{}");
            badEntries.add(dev.plattnericus.cases.skin.InstanceCodec.encode(pending).replace("\"origin\":\"" + pending.origin().name() + "\",", ""));
            player.getPersistentDataContainer().set(ctx.keys().journal, org.bukkit.persistence.PersistentDataType.LIST.strings(), badEntries);
            require(journal.entries(player).isEmpty(), "malformed journal entries broke recovery");
            new dev.plattnericus.cases.gui.menu.CasesMenu(ctx, player).open();
            var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0, org.bukkit.event.inventory.ClickType.LEFT,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(click);
            require(click.isCancelled(), "menu icons can be removed");
            new dev.plattnericus.cases.shop.ShopMenu(ctx, player).open();
            new dev.plattnericus.cases.gui.menu.SkinInspectMenu(ctx, player, knife, player::closeInventory).open();
            new dev.plattnericus.cases.gui.menu.SkinInventoryMenu(ctx, player).open();
            var dealer = player.getWorld().getEntities().stream().filter(ctx.shop()::isShop).findFirst().orElseThrow();
            var interaction = new org.bukkit.event.player.PlayerInteractEntityEvent(player, dealer, org.bukkit.inventory.EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(interaction);
            require(interaction.isCancelled() && player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.shop.ShopMenu,
                    "dealer interaction did not open protected shop");
            var damage = new org.bukkit.event.entity.EntityDamageEvent(dealer, org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM,
                    org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 1);
            Bukkit.getPluginManager().callEvent(damage);
            require(damage.isCancelled(), "dealer can be damaged");
            sender.sendMessage("PASS: journal mutations and malformed-entry recovery; case/shop/skin/list menus render and protect icons; dealer interaction and damage protection.");
        } finally {
            player.closeInventory(); ctx.gallery().close(player); ctx.inspect().stop(player);
            ctx.shop().load(shopFile, getLogger()::warning);
            java.nio.file.Files.deleteIfExists(temporary.toPath());
            if (originalJournal == null) player.getPersistentDataContainer().remove(ctx.keys().journal);
            else player.getPersistentDataContainer().set(ctx.keys().journal, org.bukkit.persistence.PersistentDataType.LIST.strings(), originalJournal);
            inv.setContents(saved); inv.setHeldItemSlot(held); player.updateInventory();
        }
    }

    private void repositoryAudit(CommandSender sender, Player player, CasesContext ctx) {
        var owner = java.util.UUID.randomUUID();
        var id = java.util.UUID.randomUUID();
        var def = ctx.catalog().caseDefinition("kilowatt_case");
        var skin = def.pool().values().iterator().next().getFirst();
        var instance = new dev.plattnericus.cases.skin.SkinInstance(id, owner, skin.id(), 0.2, 42, 123, true, 0,
                dev.plattnericus.cases.skin.PatternInfo.NONE, def.id(), dev.plattnericus.cases.skin.SkinInstance.Origin.TEST,
                System.currentTimeMillis(), false, dev.plattnericus.cases.skin.SkinInstance.Status.PENDING);
        var record = new dev.plattnericus.cases.storage.OpeningRecord(java.util.UUID.randomUUID(), owner, "MCCasesAudit",
                def.id(), id, skin.id(), skin.rarity().id(), 0.2, 42, true, true, instance.createdAt());
        var repo = ctx.repository();
        repo.persistOpening(instance, record).thenCompose(v -> repo.persistOpening(instance, record))
                .thenCompose(v -> repo.loadActive(owner)).thenCompose(active -> {
                    require(active.size() == 1 && active.getFirst().status() == dev.plattnericus.cases.skin.SkinInstance.Status.PENDING, "duplicate persistence / pending state");
                    return repo.history(owner, 10);
                }).thenCompose(history -> {
                    require(history.size() == 1 && history.getFirst().instanceId().equals(id), "duplicate/missing opening audit");
                    return repo.finalizePending(owner);
                }).thenCompose(recovered -> {
                    require(recovered == 1, "pending reward did not recover");
                    return java.util.concurrent.CompletableFuture.allOf(repo.setFavorite(id, true), repo.addKills(id, 2), repo.addKills(id, 3), repo.setEquipped(owner, "bow", id));
                }).thenCompose(v -> ctx.profiles().snapshot(owner)).thenCompose(profile -> {
                    var stored = profile.get(id);
                    require(stored != null && stored.favorite() && stored.kills() == 5 && profile.owned().size() == 1
                            && id.equals(profile.equipped(dev.plattnericus.cases.profile.EquipSlot.BOW)), "favorite/atomic kills/equip/profile snapshot");
                    return java.util.concurrent.CompletableFuture.allOf(repo.setStatus(id, dev.plattnericus.cases.skin.SkinInstance.Status.REMOVED), repo.setEquipped(owner, "bow", null));
                }).thenCompose(v -> repo.loadActive(owner)).whenComplete((active, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) { report(new IllegalStateException("SQLite integration audit failed", error)); return; }
                    try {
                        require(active.isEmpty(), "removed skin remained active");
                        sender.sendMessage("PASS: SQLite opening transaction/idempotency, PENDING recovery, favorite, concurrent StatTrak increments, equip persistence and offline profile snapshot.");
                        openingAudit(sender, player, ctx);
                    } catch (Exception failure) { report(failure); }
                }));
    }

    private void previewAudit(CommandSender sender, Player player, CasesContext ctx) {
        try {
            var knife = ctx.profiles().get(player).equippedKnifeInstance();
            var skin = ctx.catalog().skin(knife.skinId());
            ItemStack[] saved = snapshot(player);
            var callback = new java.util.concurrent.atomic.AtomicInteger();
            ctx.previews().show(player, skin, knife.pattern(), knife.floatValue(), knife.wearSeed(),
                    net.kyori.adventure.text.Component.text("Preview audit"), callback::incrementAndGet);
            require(ctx.previews().isPreviewing(player), "resource-pack item preview did not open");
            ctx.previews().end(player, true);
            require(!ctx.previews().isPreviewing(player) && callback.get() == 1
                    && java.util.Arrays.equals(saved, player.getInventory().getContents()), "preview end altered inventory / callback");
            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(ctx.plugin().getDataFolder(), "config.yml"));
            config.set("preview.mode", "map");
            var settings = dev.plattnericus.cases.config.PluginSettings.load(config, getLogger()::warning);
            var pool = new dev.plattnericus.cases.map.MapViewPool(getDataFolder(), getLogger(), dev.plattnericus.cases.map.MapPaletteSource.load().table());
            pool.load();
            var preview = new dev.plattnericus.cases.map.MapPreviewService(this, pool, ctx::render, () -> settings, ctx::messages, ctx::sounds);
            preview.show(player, skin, knife.pattern(), knife.floatValue(), knife.wearSeed(),
                    net.kyori.adventure.text.Component.text("Cancelled map preview audit"), callback::incrementAndGet);
            preview.end(player, false); // cancel while asynchronous rendering is still pending
            ctx.render().mapPreview(skin, knife.pattern(), knife.floatValue(), knife.wearSeed()).whenComplete((pixels, error) ->
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            require(error == null && !preview.isPreviewing(player), "cancelled render reopened stale map preview");
                            preview.show(player, skin, knife.pattern(), knife.floatValue(), knife.wearSeed(),
                                    net.kyori.adventure.text.Component.text("Map preview audit"), callback::incrementAndGet);
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                try {
                                    require(preview.isPreviewing(player), "map preview did not open");
                                    preview.end(player, true);
                                    require(callback.get() == 2 && java.util.Arrays.equals(saved, player.getInventory().getContents()), "map preview inventory / callback");
                                    sender.sendMessage("PASS: resource-pack item and map previews, asynchronous cancellation, callback and real inventory preserved.");
                                    repositoryAudit(sender, player, ctx);
                                } catch (Exception failure) { report(failure); }
                                finally { preview.shutdown(); pool.release(player.getUniqueId()); }
                            }, 2);
                        } catch (Exception failure) { preview.shutdown(); pool.release(player.getUniqueId()); report(failure); }
                    }, 2));
        } catch (Exception failure) { ctx.previews().end(player, false); report(failure); }
    }

    private void handAndHotkeyAudit(CommandSender sender, Player player, CasesContext ctx) {
        ItemStack[] saved = snapshot(player);
        int held = player.getInventory().getHeldItemSlot();
        boolean sneaking = player.isSneaking();
        boolean bodyHand = ctx.inspect().bodyHandMode(player);
        Runnable restore = () -> {
            ctx.inspect().stop(player); player.getInventory().setContents(saved);
            player.getInventory().setHeldItemSlot(held); player.setSneaking(sneaking); player.updateInventory();
            ctx.inspect().setBodyHandMode(player, bodyHand);
        };
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var knife = ctx.profiles().get(player).equippedKnifeInstance();
                player.setSneaking(false);
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD)); player.getInventory().setHeldItemSlot(0);
                ctx.knives().refreshHeld(player);
                ctx.inspect().setBodyHandMode(player, true);
                require(ctx.inspect().start(player, knife, false), "selected F5 body-hand inspect did not start");
                var sessions = ctx.inspect().getClass().getDeclaredField("sessions"); sessions.setAccessible(true);
                Object session = ((java.util.Map<?, ?>) sessions.get(ctx.inspect())).get(player.getUniqueId());
                var field = session.getClass().getDeclaredField("lastAnchor"); field.setAccessible(true);
                var anchor = (org.bukkit.Location) field.get(session);
                require(anchor.getPitch() == 0 && Math.abs(anchor.getY() - player.getEyeLocation().getY() + .8) < .00001,
                        "F5 inspect is not anchored to the physical hand / follows head pitch");
                ctx.inspect().stop(player);
                ctx.inspect().setBodyHandMode(player, false);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    boolean passed = false;
                    try {
                        var swap = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player,
                                player.getInventory().getItemInOffHand(), player.getInventory().getItemInMainHand());
                        Bukkit.getPluginManager().callEvent(swap);
                        require(swap.isCancelled() && ctx.inspect().isInspecting(player), "F did not inspect the held skin");
                        ctx.inspect().stop(player); player.setSneaking(true);
                        var normalSwap = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player,
                                player.getInventory().getItemInOffHand(), player.getInventory().getItemInMainHand());
                        Bukkit.getPluginManager().callEvent(normalSwap);
                        require(!normalSwap.isCancelled(), "sneak + F no longer swaps normally");
                        sender.sendMessage("PASS: F5 body-hand anchor, head-pitch independence, F inspect action and sneak + F normal swap.");
                        passed = true;
                    } catch (Exception failure) { report(failure); }
                    finally { restore.run(); }
                    if (passed) previewAudit(sender, player, ctx);
                }, 10);
            } catch (Exception failure) { restore.run(); report(failure); }
        }, 10);
    }

    private void openingAudit(CommandSender sender, Player player, CasesContext ctx) {
        require(player.isOnline() && !ctx.openings().isOpening(player), "player unavailable for opening audit");
        ctx.inspect().stop(player); ctx.gallery().close(player); player.closeInventory();
        ItemStack[] saved = snapshot(player);
        int held = player.getInventory().getHeldItemSlot();
        try {
        var def = ctx.catalog().caseDefinition("kilowatt_case");
        var key = ctx.catalog().key(def.keyId());
        var before = new java.util.HashSet<>(ctx.profiles().get(player).all().stream().map(dev.plattnericus.cases.skin.SkinInstance::id).toList());
        player.getInventory().clear();
        new dev.plattnericus.cases.slot.ReservedSlotService(ctx).ensure(player);
        player.getInventory().setItem(0, ctx.caseItems().caseItem(def, 2, false, ctx.messages(player)));
        ctx.openings().open(player, def, false, false);
        require(!ctx.openings().isOpening(player) && ctx.caseItems().count(player, "case", def.id()) == 2, "missing-key opening consumed items");
        player.getInventory().setItem(1, ctx.caseItems().keyItem(key, 2, true, ctx.messages(player)));
        ctx.openings().open(player, def, false, false);
        ctx.openings().open(player, def, false, false); // independent ordinary opening, while the first session is active
        new org.bukkit.scheduler.BukkitRunnable() {
            private int elapsed;
            @Override public void run() {
                elapsed += 10;
                try {
                    require(player.isOnline(), "player disconnected during opening audit");
                    require(elapsed < 600, "opening did not finish within 30 seconds");
                    if (ctx.openings().isOpening(player)) return;
                    var added = ctx.profiles().get(player).owned().stream().filter(s -> !before.contains(s.id())).toList();
                    require(added.size() == 2 && added.getFirst().origin() == dev.plattnericus.cases.skin.SkinInstance.Origin.TEST,
                            "opening reward count/test-key origin");
                    require(ctx.caseItems().count(player, "case", def.id()) == 0 && ctx.caseItems().count(player, "key", key.id()) == 0,
                            "two openings did not consume exactly two pairs");
                    require(ctx.profiles().journal().entries(player).isEmpty(), "completed opening left journal pending");
                    var rewardId = added.getFirst().id();
                    cancel(); restore();
                    ctx.repository().loadActive(player.getUniqueId()).thenCombine(ctx.repository().history(player.getUniqueId(), 100), (active, history) -> {
                        require(active.stream().anyMatch(s -> s.id().equals(rewardId) && s.status() == dev.plattnericus.cases.skin.SkinInstance.Status.OWNED), "opening reward not durable");
                        require(history.stream().filter(r -> r.instanceId().equals(rewardId) && r.test()).count() == 1, "opening audit not durable/idempotent or test marker lost");
                        return true;
                    }).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(RuntimeChecksPlugin.this, () -> {
                        if (error != null) report(new IllegalStateException("opening durability failed", error));
                        else sender.sendMessage("PASS FULL AUDIT: actual parallel case reels/reveals, independent requests, exact pair consumption, test-key origin, journal cleared and OWNED reward + audit saved. Original inventory restored.");
                    }));
                } catch (Exception failure) { cancel(); restore(); report(failure); }
            }
            private void restore() {
                ctx.inspect().stop(player); ctx.gallery().close(player); player.closeInventory();
                player.getInventory().setContents(saved); player.getInventory().setHeldItemSlot(held); player.updateInventory();
            }
        }.runTaskTimer(this, 10, 10);
        } catch (Exception failure) {
            ctx.inspect().stop(player); ctx.gallery().close(player); player.closeInventory();
            player.getInventory().setContents(saved); player.getInventory().setHeldItemSlot(held); player.updateInventory();
            report(failure);
        }
    }
}
