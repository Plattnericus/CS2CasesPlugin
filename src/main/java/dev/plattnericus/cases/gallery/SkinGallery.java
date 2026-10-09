package dev.plattnericus.cases.gallery;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.gui.SkinQuery;
import dev.plattnericus.cases.gui.menu.FilterMenu;
import dev.plattnericus.cases.gui.menu.SkinInspectMenu;
import dev.plattnericus.cases.gui.menu.SkinInventoryMenu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The skin inventory as a floating wall of item displays with a dark backdrop, visible to everyone
 * nearby. Only the player who opened it can use it: looking at an entry highlights it and shows a
 * tooltip next to it, right click opens the inspect view, left click equips a knife, sneak + right
 * click toggles favorite, and the page buttons browse. Scroll wheel and number keys select
 * the hotbar normally, including over entries. Walking ten blocks away closes it;
 * paging rebuilds it in the same place.
 * <p>
 * The same wall shows other players' inventories read-only ({@code /skins <player>}).
 */
public final class SkinGallery implements Listener {

    private static final AxisAngle4f NONE = new AxisAngle4f();
    private static final double LINE = 10 * 0.025;
    private static final double MAX_DISTANCE_SQUARED = 10 * 10;

    /**
     * A clickable thing on the wall. {@code tooltipAt} places the tooltip right at the target
     * (control buttons); null puts it into the column beside the wall (skins). {@code actionBar} is
     * shown in addition while hovering.
     */
    private record Target(ItemDisplay icon, float scale, List<Component> tooltip, Location tooltipAt, Component actionBar,
                          Runnable left, Runnable sneakLeft, Runnable right, Runnable sneakRight) {
    }

    private static final class View {
        final UUID viewer;
        final PlayerProfile owner;
        final String ownerName;
        final boolean readOnly;
        final Location base;
        final Vector right;
        final List<Entity> entities = new ArrayList<>();
        final Map<UUID, Target> targets = new HashMap<>();
        final Map<UUID, Cell> cells = new HashMap<>();
        /** Tooltip column right of the backdrop, top aligned with the header. */
        double tooltipX;
        double tooltipTop;
        BukkitTask task;
        Target hovered;
        TextDisplay tooltip;
        int idleTicks;

        View(UUID viewer, PlayerProfile owner, String ownerName, boolean readOnly, Location base, Vector right) {
            this.viewer = viewer;
            this.owner = owner;
            this.ownerName = ownerName;
            this.readOnly = readOnly;
            this.base = base;
            this.right = right;
        }
    }

    /** The two display entities that make up one skin card. Kept alive when its state changes. */
    private record Cell(ItemDisplay icon, TextDisplay label, SkinDefinition definition, SkinInstance instance,
                        float scale) {
    }

    private final CasesContext ctx;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, View> byHitbox = new HashMap<>();

    public SkinGallery(CasesContext ctx) {
        this.ctx = ctx;
    }

    // ------------------------------------------------------------------ opening

    /** Opens the viewer's own skin inventory in the configured style (wall or inventory menu). */
    public void open(Player player, MenuStates.Category category) {
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile == null) {
            ctx.messages(player).send(player, "profile.loading");
            return;
        }
        open(player, profile, player.getName(), false, category);
    }

    /** Opens another player's skin inventory read-only. */
    public void openOther(Player viewer, PlayerProfile owner, String ownerName) {
        open(viewer, owner, ownerName, true, ctx.menuStates().get(viewer.getUniqueId()).category);
    }

    private void open(Player player, PlayerProfile owner, String ownerName, boolean readOnly, MenuStates.Category category) {
        MenuStates.State state = ctx.menuStates().get(player.getUniqueId());
        state.inventoryRequestVersion++;
        if (category != null) {
            state.category = category;
            state.page = 0;
        }
        if (!ctx.settings().skinInventory().world()) {
            player.closeInventory();
            new SkinInventoryMenu(ctx, player, owner, ownerName, readOnly).open();
            return;
        }
        close(player);
        player.closeInventory();
        build(player, newView(player, owner, ownerName, readOnly));
    }

    /** Fixes the wall's position once: in front of the player, facing them. */
    private View newView(Player player, PlayerProfile owner, String ownerName, boolean readOnly) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location base = eye.clone().add(forward.multiply(ctx.settings().skinInventory().distance()));
        base.setYaw(eye.getYaw() + 180);
        base.setPitch(0);
        return new View(player.getUniqueId(), owner, ownerName, readOnly, base, right);
    }

    public boolean isOpen(Player player) {
        return views.containsKey(player.getUniqueId());
    }

    public void refreshLanguage(Player player) {
        if (isOpen(player)) rebuild(player);
    }

    // ------------------------------------------------------------------ building

    private void build(Player player, View view) {
        views.put(player.getUniqueId(), view);
        PluginSettings.SkinInventory cfg = ctx.settings().skinInventory();
        MenuStates.State state = ctx.menuStates().get(player.getUniqueId());
        List<SkinQuery.Entry> entries = SkinQuery.run(ctx.catalog(), view.owner.owned(), state);
        int perPage = cfg.columns() * cfg.rows();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        state.page = Math.max(0, Math.min(state.page, pages - 1));

        float scale = (float) cfg.itemScale();
        double sp = cfg.spacing();
        double rowSp = scale + 0.3;
        double top = (cfg.rows() - 1) / 2.0 * rowSp + 0.2;
        double controlY = top - cfg.rows() * rowSp + 0.05;
        double headerY = top + scale / 2 + 0.12;

        // backdrop from the header down to the control row
        double width = cfg.columns() * sp + 0.25;
        double bottom = controlY - 0.2;
        double height = headerY + 0.2 - bottom;
        view.tooltipX = width / 2 + 0.7;
        view.tooltipTop = headerY + 0.2;
        view.entities.add(panel(view, Material.BLACK_STAINED_GLASS, -width / 2, bottom, width, height, -0.08f));
        view.entities.add(panel(view, Material.GOLD_BLOCK, -width / 2, headerY + 0.2, width, 0.012, -0.075f));

        String category = ctx.messages(player).raw("gui.skins.tab." + state.category.name().toLowerCase(Locale.ROOT) + ".name")
                .replaceAll("<[^>]+>", "");
        Component header = ctx.messages(player).get(view.readOnly ? "gallery.header-other" : "gallery.header",
                Text.unparsed("player", view.ownerName), Text.unparsed("category", category),
                Text.unparsed("page", state.page + 1), Text.unparsed("pages", pages), Text.unparsed("count", entries.size()));
        view.entities.add(text(at(view, 0, headerY), header, 0.45f, false));

        for (int i = 0; i < perPage; i++) {
            int idx = state.page * perPage + i;
            if (idx >= entries.size()) {
                break;
            }
            int col = i % cfg.columns();
            int row = i / cfg.columns();
            cell(player, view, (col - (cfg.columns() - 1) / 2.0) * sp, top - row * rowSp, entries.get(idx));
        }
        if (entries.isEmpty()) {
            view.entities.add(text(at(view, 0, top - rowSp / 2), ctx.messages(player).get("gallery.empty"), 0.45f, false));
        }
        controls(player, view, controlY, state);
        view.task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> tick(player, view), 2L, 2L);
    }

    private void cell(Player player, View view, double x, double y, SkinQuery.Entry e) {
        float scale = (float) ctx.settings().skinInventory().itemScale();
        SkinDefinition def = e.definition();
        SkinInstance inst = e.instance();
        boolean equipped = view.owner.isEquipped(inst.id());
        ItemStack stack = SkinIcons.icon(def, ctx.formatter(player).name(def, inst), List.of(), ctx.settings(), equipped);
        ItemDisplay icon = item(at(view, x, y), stack, scale);
        view.entities.add(icon);
        Component label = cellLabel(player, def, inst, equipped);
        // text grows upwards from its origin: put the origin below the icon so both lines sit under it
        // Keep the card captions compact: the full localized name is still available in the
        // hover tooltip, while this two-line label must remain readable in a six-column wall.
        float labelScale = 0.19f;
        TextDisplay labelDisplay = text(at(view, x, y - scale / 2 - 0.04 - 2 * LINE * labelScale), label, labelScale, false);
        labelDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        labelDisplay.setLineWidth(82);
        view.entities.add(labelDisplay);
        view.cells.put(inst.id(), new Cell(icon, labelDisplay, def, inst, scale));

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(ctx.formatter(player).fullName(def, inst));
        tooltip.addAll(ctx.formatter(player).lore(def, inst, true));
        if (!view.readOnly) {
            tooltip.addAll(ctx.messages(player).itemList(def.isKnife() ? "gallery.hint-knife" : "gallery.hint-weapon"));
        }
        Runnable inspect = () -> {
            close(player);
            new SkinInspectMenu(ctx, player, inst, view.owner, view.readOnly,
                    () -> build(player, newViewLike(view))).open();
        };
        Runnable left = inspect;
        Runnable sneakLeft = inspect;
        Runnable favorite = inspect;
        if (!view.readOnly) {
            if (def.isKnife()) {
                left = () -> {
                    ctx.knives().toggle(player, inst, EquipSlot.KNIFE);
                    refreshCells(player, view);
                };
                sneakLeft = left;
            } else {
                left = () -> {
                    ctx.knives().toggle(player, inst, EquipSlot.BOW);
                    refreshCells(player, view);
                };
                sneakLeft = () -> {
                    ctx.knives().toggle(player, inst, EquipSlot.CROSSBOW);
                    refreshCells(player, view);
                };
            }
            favorite = () -> {
                ctx.profiles().setFavorite(player, inst, !inst.favorite());
                refreshCells(player, view);
            };
        }
        hitbox(view, at(view, x, y - scale / 2), scale * 1.05f,
                new Target(icon, scale, tooltip, null, ctx.formatter(player).actionBar(def, inst), left, sneakLeft, inspect, favorite));
    }

    private Component cellLabel(Player player, SkinDefinition def, SkinInstance inst, boolean equipped) {
        String wear = def.hasWear() ? ctx.formatter(player).wear(inst).shortName() + " "
                + ctx.formatter(player).floatText(inst.floatValue(), false) : "";
        String weapon = def.weapon().name();
        if (inst.statTrak()) {
            weapon = "ST " + weapon;
        }
        Component compactName = Text.item("<rarity>" + Text.escape(weapon),
                Text.color("rarity", def.rarity().color()));
        return compactName.append(Component.newline())
                .append(Text.mm("<gray>" + Text.escape(wear)
                        + (inst.favorite() ? " <yellow>★" : "") + (equipped ? " <green>✔" : "")));
    }

    /** Updates changed card data in place. Removing and respawning the wall causes a visible flash. */
    private void refreshCells(Player player, View view) {
        if (views.get(player.getUniqueId()) != view || !player.isOnline()) {
            return;
        }
        for (Cell cell : view.cells.values()) {
            boolean equipped = view.owner.isEquipped(cell.instance().id());
            cell.icon().setItemStack(SkinIcons.icon(cell.definition(),
                    ctx.formatter(player).name(cell.definition(), cell.instance()), List.of(),
                    ctx.settings(), equipped));
            cell.label().text(cellLabel(player, cell.definition(), cell.instance(), equipped));
        }
    }

    private void controls(Player player, View view, double y, MenuStates.State state) {
        String[] keys = {"previous", "category", "sort", "filter", "list", "close", "next"};
        Material[] icons = {Material.ARROW, Material.CHEST, Material.HOPPER, Material.SPYGLASS, Material.BOOK,
                Material.BARRIER, Material.SPECTRAL_ARROW};
        Runnable[] actions = {
                () -> page(player, -1),
                () -> cycleCategory(player),
                () -> cycleSort(player),
                () -> {
                    close(player);
                    new FilterMenu(ctx, player, () -> build(player, newViewLike(view))).open();
                },
                () -> {
                    close(player);
                    new SkinInventoryMenu(ctx, player, view.owner, view.ownerName, view.readOnly).open();
                },
                () -> close(player),
                () -> page(player, 1)
        };
        double cs = Math.min(ctx.settings().skinInventory().spacing(), 0.42);
        for (int i = 0; i < keys.length; i++) {
            double x = (i - (keys.length - 1) / 2.0) * cs;
            Component label = ctx.messages(player).get("gallery.button." + keys[i],
                    Text.unparsed("sort", ctx.messages(player).raw("gui.skins.sorts." + state.sort.name().toLowerCase(Locale.ROOT))));
            ItemDisplay icon = item(at(view, x, y), new ItemStack(icons[i]), 0.2f);
            view.entities.add(icon);
            Runnable a = actions[i];
            hitbox(view, at(view, x, y - 0.11), 0.22f,
                    new Target(icon, 0.2f, List.of(label), at(view, x, y + 0.14), label, a, a, a, a));
        }
    }

    /** A fresh view at the same place (used when coming back from a menu). */
    private View newViewLike(View old) {
        return new View(old.viewer, old.owner, old.ownerName, old.readOnly, old.base, old.right);
    }

    private Location at(View view, double x, double y) {
        Location l = view.base.clone().add(view.right.clone().multiply(x)).add(0, y, 0);
        l.setYaw(view.base.getYaw());
        l.setPitch(0);
        return l;
    }

    private void common(Display d) {
        d.setPersistent(false);
        d.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
    }

    private BlockDisplay panel(View view, Material material, double x, double y, double w, double h, float z) {
        // the display sits at the wall centre; its own x axis points to the viewer's right
        return view.base.getWorld().spawn(at(view, 0, 0), BlockDisplay.class, d -> {
            common(d);
            d.setBlock(material.createBlockData());
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, z), NONE,
                    new Vector3f((float) w, (float) h, 0.02f), NONE));
        });
    }

    private ItemDisplay item(Location at, ItemStack stack, float scale) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            common(e);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), NONE, new Vector3f(scale, scale, scale), NONE));
        });
    }

    private TextDisplay text(Location at, Component text, float scale, boolean background) {
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            common(e);
            e.text(text);
            e.setBackgroundColor(background ? Color.fromARGB(220, 16, 18, 24) : Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(!background);
            e.setTransformation(new Transformation(new Vector3f(), NONE, new Vector3f(scale, scale, scale), NONE));
        });
    }

    private void hitbox(View view, Location bottom, float size, Target target) {
        Interaction box = bottom.getWorld().spawn(bottom, Interaction.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
            e.setInteractionWidth(size);
            e.setInteractionHeight(size);
            e.setResponsive(true);
        });
        view.entities.add(box);
        view.targets.put(box.getUniqueId(), target);
        byHitbox.put(box.getUniqueId(), view);
    }

    // ------------------------------------------------------------------ interaction

    private void tick(Player player, View view) {
        if (!player.isOnline() || views.get(player.getUniqueId()) != view) {
            return;
        }
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, eye.getDirection(), 6, 0.0,
                e -> view.targets.containsKey(e.getUniqueId()));
        Target target = hit == null || hit.getHitEntity() == null ? null : view.targets.get(hit.getHitEntity().getUniqueId());
        if (target != view.hovered) {
            if (view.hovered != null) {
                highlight(view.hovered, false);
            }
            if (view.tooltip != null) {
                view.tooltip.remove();
                view.tooltip = null;
            }
            if (target != null) {
                highlight(target, true);
                view.tooltip = tooltip(view, target);
                ctx.sounds().play(player, "gui.hover");
            }
            view.hovered = target;
        }
        if (target != null) {
            player.sendActionBar(target.actionBar());
            view.idleTicks = 0;
        } else if ((view.idleTicks += 2) > ctx.settings().skinInventory().timeoutSeconds() * 20) {
            close(player);
        }
    }

    /**
     * Tooltip panel in a fixed column beside the wall, so it never covers an icon. Text displays grow
     * upwards from their origin, so the origin is placed one tooltip height below the top edge.
     */
    private TextDisplay tooltip(View view, Target target) {
        Component text = Component.join(JoinConfiguration.newlines(), target.tooltip());
        float scale = 0.22f;
        int lines = target.tooltip().size();
        Location at = target.tooltipAt() != null ? target.tooltipAt()
                : at(view, view.tooltipX, view.tooltipTop - lines * LINE * scale - 0.02);
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            common(e);
            e.text(text);
            e.setAlignment(TextDisplay.TextAlignment.LEFT);
            e.setBackgroundColor(Color.fromARGB(235, 16, 8, 24));
            e.setLineWidth(400);
            // in front of the wall so neighbouring icons do not cover it
            e.setTransformation(new Transformation(new Vector3f(), NONE, new Vector3f(scale, scale, scale), NONE));
        });
    }

    private static void highlight(Target t, boolean on) {
        float s = on ? t.scale() * 1.18f : t.scale();
        t.icon().setInterpolationDelay(0);
        t.icon().setInterpolationDuration(3);
        t.icon().setTransformation(new Transformation(new Vector3f(0, 0, on ? 0.05f : 0), NONE, new Vector3f(s, s, s), NONE));
        t.icon().setGlowing(on);
        if (on) {
            t.icon().setGlowColorOverride(Color.fromRGB(0xE4AE39));
        }
    }

    private void click(Player player, Entity entity, boolean left) {
        View view = byHitbox.get(entity.getUniqueId());
        if (view == null || !view.viewer.equals(player.getUniqueId())) {
            return;
        }
        Target t = view.targets.get(entity.getUniqueId());
        ctx.sounds().play(player, "gui.click");
        if (left) {
            (player.isSneaking() ? t.sneakLeft() : t.left()).run();
        } else if (player.isSneaking()) {
            t.sneakRight().run();
        } else {
            t.right().run();
        }
    }

    private void page(Player player, int delta) {
        View view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        MenuStates.State s = ctx.menuStates().get(player.getUniqueId());
        PluginSettings.SkinInventory cfg = ctx.settings().skinInventory();
        int perPage = cfg.columns() * cfg.rows();
        int count = SkinQuery.run(ctx.catalog(), view.owner.owned(), s).size();
        int pages = Math.max(1, (count + perPage - 1) / perPage);
        int target = Math.max(0, Math.min(pages - 1, s.page + delta));
        if (target == s.page) {
            ctx.sounds().play(player, "gui.error");
            return;
        }
        s.page = target;
        ctx.sounds().play(player, "gui.page");
        rebuild(player);
    }

    private void cycleCategory(Player player) {
        MenuStates.State s = ctx.menuStates().get(player.getUniqueId());
        MenuStates.Category[] all = MenuStates.Category.values();
        s.category = all[(s.category.ordinal() + 1) % all.length];
        s.page = 0;
        rebuild(player);
    }

    private void cycleSort(Player player) {
        MenuStates.State s = ctx.menuStates().get(player.getUniqueId());
        MenuStates.Sort[] all = MenuStates.Sort.values();
        s.sort = all[(s.sort.ordinal() + 1) % all.length];
        rebuild(player);
    }

    /** Rebuilds the contents in exactly the same place. */
    private void rebuild(Player player) {
        View old = views.get(player.getUniqueId());
        if (old == null) {
            return;
        }
        remove(old);
        if (player.isOnline()) {
            build(player, newViewLike(old));
        }
    }

    public void close(Player player) {
        View view = views.get(player.getUniqueId());
        if (view != null) {
            remove(view);
            player.sendActionBar(Component.empty());
        }
    }

    private void remove(View view) {
        views.remove(view.viewer, view);
        if (view.task != null) {
            view.task.cancel();
        }
        if (view.tooltip != null) {
            view.tooltip.remove();
        }
        for (Entity e : view.entities) {
            byHitbox.remove(e.getUniqueId());
            e.remove();
        }
        view.entities.clear();
        view.cells.clear();
    }

    public void shutdown() {
        for (View v : views.values().toArray(View[]::new)) {
            remove(v);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (byHitbox.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                click(event.getPlayer(), event.getRightClicked(), false);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        if (byHitbox.containsKey(event.getAttacked().getUniqueId())) {
            event.setCancelled(true);
            click(event.getPlayer(), event.getAttacked(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        View v = views.get(event.getPlayer().getUniqueId());
        if (v != null && (!v.base.getWorld().equals(event.getTo().getWorld())
                || v.base.distanceSquared(event.getTo()) >= MAX_DISTANCE_SQUARED)) {
            close(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            close(p);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        close(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        close(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        close(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
    }
}
