package dev.plattnericus.cases.inspect;

import dev.plattnericus.cases.catalog.FinishVariant;
import dev.plattnericus.cases.catalog.Palette;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.skin.SkinInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-side 3D inspect. A weapon is assembled from block/item displays in front of the player,
 * oriented to the player's view, and animated purely through display interpolation: the server
 * sends one target transformation per sample, the client interpolates. Entities are never saved
 * to disk and are removed on every exit path.
 */
public final class InspectService implements Listener {

    private final CasesContext ctx;
    private final Supplier<InspectModels> models;
    private final Map<UUID, InspectSession> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, String> lastAnimations = new HashMap<>();
    private final java.util.Set<UUID> bodyHandMode = new java.util.HashSet<>();

    public InspectService(CasesContext ctx, Supplier<InspectModels> models) {
        this.ctx = ctx;
        this.models = models;
    }

    public boolean isInspecting(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Starts an inspect of the given skin. {@code reveal} is the short showcase after a gold drop:
     * no cooldown, centred in view, held item stays visible.
     */
    public boolean start(Player player, SkinInstance instance, boolean reveal) {
        return start(player, instance, reveal, bodyHandMode(player));
    }

    public boolean bodyHandMode(Player player) { return bodyHandMode.contains(player.getUniqueId()); }

    /** The server cannot see F5; an explicit command chooses the hotkey's camera mode for this login. */
    public void setBodyHandMode(Player player, boolean bodyHand) {
        if (bodyHand) bodyHandMode.add(player.getUniqueId());
        else bodyHandMode.remove(player.getUniqueId());
    }

    public boolean start(Player player, SkinInstance instance, boolean reveal, boolean bodyHand) {
        bodyHand = bodyHand && !reveal;
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        if (def == null || !player.hasPermission("mccases.inspect")) {
            return false;
        }
        if (isInspecting(player)) {
            if (!reveal) {
                ctx.messages(player).send(player, "inspect.running");
            }
            return false;
        }
        long now = Bukkit.getCurrentTick();
        Long until = cooldowns.get(player.getUniqueId());
        if (!reveal && until != null && now < until) {
            return false;
        }
        InspectModels m = models.get();
        KnifeModel model = m.model(def.weapon().inspectModel() == null ? def.weapon().id() : def.weapon().inspectModel());
        if (!def.isKnife() && !m.hasModel(def.weapon().id())) model = m.model(def.weapon().category().name().toLowerCase(java.util.Locale.ROOT));
        if (model == null) {
            return false;
        }
        InspectAnimation animation = reveal ? m.animation("reveal")
                : m.chooseAnimation(def, model, lastAnimations.get(player.getUniqueId()));
        if (animation == null) {
            return false;
        }
        if (!reveal) lastAnimations.put(player.getUniqueId(), animation.id());
        ctx.gallery().close(player);
        List<ModelPart> parts = InspectRig.blockParts(def.weapon(), model.parts());
        if (ctx.settings().resourcePack().enabled() && m.packModel()) {
            parts = InspectRig.packParts(def.weapon(), m.packModelScale());
        }
        InspectModels.Anchor selectedAnchor = reveal ? m.revealAnchor() : bodyHand ? m.handAnchor() : m.anchor();
        float scale = reveal ? 1f : bodyHand ? m.handModelScale() : m.modelScale();
        Location anchor = anchor(player, selectedAnchor, bodyHand);
        boolean leftHand = player.getMainHand() == org.bukkit.inventory.MainHand.LEFT;
        Location handAnchor = anchor(player, m.handAnchor(), true);
        List<InspectSession.PartEntity> spawned = new ArrayList<>();
        Map<String, Material> resolved = resolveMaterials(def, instance, m);
        ItemStack skinItem = SkinIcons.icon(def, ctx.formatter(player).name(def, instance), List.of(), ctx.settings(), false);
        for (ModelPart part : parts) {
            Display display = spawn(anchor, part, resolved, skinItem, animation, m, scale, leftHand, bodyHand, false);
            if (display != null) {
                player.showEntity(ctx.plugin(), display);
                spawned.add(new InspectSession.PartEntity(part, display, false));
            }
            Display observer = spawn(handAnchor, part, resolved, skinItem, animation, m, m.handModelScale(), leftHand, true, true);
            if (observer != null) {
                player.hideEntity(ctx.plugin(), observer);
                spawned.add(new InspectSession.PartEntity(part, observer, true));
            }
        }
        InspectSession session = new InspectSession(player.getUniqueId(), animation, spawned, reveal, leftHand, bodyHand,
                scale, selectedAnchor, m.handAnchor(), m.handModelScale());
        session.lastAnchor = anchor;
        session.lastHandAnchor = handAnchor;
        sessions.put(player.getUniqueId(), session);
        if (ctx.settings().inspect().actionBar()) {
            player.sendActionBar(ctx.formatter(player).actionBar(def, instance));
        }
        // first sample is sent one tick after spawning, otherwise the client would skip the interpolation
        session.task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> tick(player, session, def, instance), 1L, 1L);
        return true;
    }

    private void tick(Player player, InspectSession s, SkinDefinition def, SkinInstance instance) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != s) {
            stop(player.getUniqueId());
            return;
        }
        int t = s.tick;
        if (s.leftHand != (player.getMainHand() == org.bukkit.inventory.MainHand.LEFT)) {
            stop(player);
            return;
        }
        // The held item is hidden client side only. A cancelled right click makes the server resend the
        // hand slot in the same tick, so the hide is sent from the next tick on and re-asserted regularly.
        if (ctx.settings().inspect().hideHeldItem() && t % 10 == 0) {
            setHandVisible(player, false, s.reveal);
        }
        String sound = s.animation.sounds().get(t);
        if (sound != null) {
            ctx.sounds().play(player, sound);
        }
        List<Integer> samples = s.animation.samples();
        while (s.sampleIndex < samples.size() && samples.get(s.sampleIndex) <= t) {
            int next = s.sampleIndex + 1 < samples.size() ? samples.get(s.sampleIndex + 1) : -1;
            if (next > t) {
                apply(s, next, next - t);
            }
            s.sampleIndex++;
        }
        follow(player, s);
        if (ctx.settings().inspect().actionBar() && t > 0 && t % 30 == 0) {
            player.sendActionBar(ctx.formatter(player).actionBar(def, instance));
        }
        if (t >= s.animation.duration() + 2) {
            stop(player);
            return;
        }
        s.tick++;
    }

    private void apply(InspectSession s, int targetTick, int duration) {
        // Both scenes and all model parts share the same group poses for this sample.
        Map<String, Matrix4f> poses = new HashMap<>();
        Matrix4f body = s.animation.groupMatrix("body", targetTick);
        poses.put("body", body);
        for (InspectSession.PartEntity pe : s.parts) {
            Display d = pe.entity();
            if (!d.isValid()) {
                continue;
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            String group = group(s.animation, pe.part());
            Matrix4f pose = poses.computeIfAbsent(group, key -> s.animation.groupMatrix(key, targetTick));
            d.setTransformationMatrix(matrix(pose, body, pe.part(), pe.observers() ? s.handScale : s.scale,
                    s.leftHand, pe.observers() || s.bodyHand));
        }
    }

    private static Matrix4f matrix(InspectAnimation animation, ModelPart part, int t, float scale, boolean leftHand, boolean bodyHand) {
        return InspectTransform.at(animation, part, t, scale, leftHand, bodyHand);
    }
    private static String group(InspectAnimation animation, ModelPart part) { return InspectTransform.group(animation, part); }
    private static Matrix4f matrix(Matrix4f raw, Matrix4f body, ModelPart part, float scale, boolean leftHand, boolean bodyHand) {
        return InspectTransform.matrix(raw, body, part, scale, leftHand, bodyHand);
    }

    /** Keeps the scene in front of the player; only teleports when the view actually changed. */
    private void follow(Player player, InspectSession s) {
        InspectModels m = models.get();
        Location hand = anchor(player, s.handAnchor, true);
        Location target = s.bodyHand ? hand : anchor(player, s.anchor, false);
        boolean ownerMoved = moved(s.lastAnchor, target, m.followThreshold());
        boolean handMoved = moved(s.lastHandAnchor, hand, m.followThreshold());
        if (!ownerMoved && !handMoved) return;
        if (handMoved) s.lastHandAnchor = hand;
        if (ownerMoved) s.lastAnchor = target;
        for (InspectSession.PartEntity pe : s.parts) {
            if ((pe.observers() ? handMoved : ownerMoved) && pe.entity().isValid()) {
                pe.entity().teleport(pe.observers() ? hand : target);
            }
        }
    }

    private static boolean moved(Location last, Location target, double threshold) {
        return last == null || !last.getWorld().equals(target.getWorld()) || last.distanceSquared(target) >= threshold * threshold
                || Math.abs(last.getYaw() - target.getYaw()) >= .6f || Math.abs(last.getPitch() - target.getPitch()) >= .6f;
    }

    private static Location anchor(Player player, InspectModels.Anchor a, boolean bodyHand) {
        Location eye = player.getEyeLocation();
        if (bodyHand) { eye.setPitch(0); eye.setYaw(player.getBodyYaw()); }
        Vector forward = eye.getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up);
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector trueUp = right.clone().crossProduct(forward).normalize();
        double handSide = player.getMainHand() == org.bukkit.inventory.MainHand.LEFT ? -1 : 1;
        Location loc = eye.clone().add(forward.multiply(a.forward())).add(right.multiply(a.right() * handSide)).add(trueUp.multiply(a.up()));
        loc.setYaw(eye.getYaw());
        loc.setPitch(eye.getPitch());
        return loc;
    }

    private Display spawn(Location at, ModelPart part, Map<String, Material> resolved, ItemStack skinItem,
                          InspectAnimation animation, InspectModels m, float scale, boolean leftHand, boolean bodyHand, boolean observers) {
        World world = at.getWorld();
        Matrix4f initial = matrix(animation, part, 0, scale, leftHand, bodyHand);
        java.util.function.Consumer<Display> common = d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(observers);
            d.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(0);
            d.setTransformationMatrix(initial);
            d.setViewRange(m.viewRange());
            d.setShadowRadius(0);
            if (m.brightness() >= 0) {
                d.setBrightness(new Display.Brightness(m.brightness(), m.brightness()));
            }
            d.setGlowing(part.glow());
        };
        if (part.type() == ModelPart.Type.ITEM) {
            ItemStack item;
            if (part.material().equals("$item")) {
                item = skinItem;
            } else if (part.material().startsWith("$rig:")) {
                item = skinItem.clone();
                item.editMeta(meta -> {
                    org.bukkit.NamespacedKey base = meta.getItemModel();
                    if (base != null) meta.setItemModel(new org.bukkit.NamespacedKey(base.getNamespace(),
                            "inspect/" + base.getKey().replaceFirst("^skin/", "") + "/" + part.material().substring(5)));
                });
            } else {
                Material mat = material(part.material(), resolved, Material.IRON_SWORD);
                item = new ItemStack(mat.isItem() ? mat : Material.IRON_SWORD);
            }
            return world.spawn(at, ItemDisplay.class, d -> {
                common.accept(d);
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            });
        }
        Material block = material(part.material(), resolved, Material.IRON_BLOCK);
        if (!block.isBlock()) {
            block = Material.IRON_BLOCK;
        }
        Material finalBlock = block;
        return world.spawn(at, BlockDisplay.class, d -> {
            common.accept(d);
            d.setBlock(finalBlock.createBlockData());
        });
    }

    private Map<String, Material> resolveMaterials(SkinDefinition def, SkinInstance inst, InspectModels m) {
        Palette palette = def.finish().palette();
        FinishVariant variant = def.finish().variant(inst.patternInfo().variantId());
        if (variant != null && variant.palette() != null) {
            palette = variant.palette();
        }
        Map<String, Material> out = new HashMap<>();
        out.put("$primary", m.nearestBlock(palette.primary()));
        out.put("$secondary", m.nearestBlock(palette.color(palette.size() - 1)));
        out.put("$accent", m.nearestBlock(palette.color(0)));
        for (String alias : new String[]{"metal", "dark", "handle", "edge"}) {
            Material a = m.alias(alias);
            out.put("$" + alias, a != null ? a : Material.IRON_BLOCK);
        }
        return out;
    }

    private static Material material(String spec, Map<String, Material> resolved, Material fallback) {
        if (spec.startsWith("$")) {
            return resolved.getOrDefault(spec, fallback);
        }
        Material m = Material.matchMaterial(spec);
        return m == null ? fallback : m;
    }

    // ------------------------------------------------------------------ stopping

    public void stop(Player player) {
        stop(player.getUniqueId());
    }

    private void stop(UUID id) {
        InspectSession s = sessions.remove(id);
        if (s == null) {
            return;
        }
        if (s.task != null) {
            s.task.cancel();
        }
        for (InspectSession.PartEntity pe : s.parts) {
            pe.entity().remove();
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            if (!s.reveal) {
                cooldowns.put(id, (long) Bukkit.getCurrentTick() + ctx.settings().inspect().cooldownTicks());
            }
            if (ctx.settings().inspect().hideHeldItem()) {
                setHandVisible(player, true, false);
            }
            player.updateInventory();
        }
    }

    /**
     * Hides (or restores) the inspecting player's held item for the player and everyone who can see
     * them. Purely visual: equipment packets only, the server inventory is never touched.
     */
    private static void setHandVisible(Player player, boolean visible, boolean keepOwnerVisible) {
        ItemStack shown = visible ? player.getInventory().getItemInMainHand() : ItemStack.empty();
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer == player && keepOwnerVisible) continue;
            if (viewer == player || (viewer.canSee(player) && viewer.getLocation().distanceSquared(player.getLocation()) < 128 * 128)) {
                viewer.sendEquipmentChange(player, EquipmentSlot.HAND, shown);
            }
        }
    }

    public void shutdown() {
        for (UUID id : sessions.keySet().toArray(UUID[]::new)) {
            stop(id);
        }
        bodyHandMode.clear();
    }

    /** Safety net: removes plugin displays that survived in a chunk (e.g. after a hard crash). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof Display && e.getPersistentDataContainer().has(ctx.keys().displayEntity, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ctx.settings().inspect().sneakRightClick()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("mccases.inspect")) {
            return;
        }
        SkinInstance knife = ctx.knives().heldSkin(player);
        if (knife != null && start(player, knife, false)) {
            event.setCancelled(true);
        }
    }

    /** The client sends the rebindable Swap Hands action, not a raw keyboard key. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInspectKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!ctx.settings().inspect().swapHandKey() || !player.hasPermission("mccases.inspect") || player.isSneaking()) return;
        ctx.knives().refreshHeld(player);
        SkinInstance skin = ctx.knives().heldSkin(player);
        if (skin == null || ctx.openings().isOpening(player)) return;
        // Also consume repeat presses during cooldown/an active inspect, so the weapon stays held.
        event.setCancelled(true);
        if (!isInspecting(player)) start(player, skin, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        InspectSession s = sessions.get(player.getUniqueId());
        if (s != null && !s.reveal) {
            // The new slot is committed after this event. Restoring earlier resends the old hand.
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                if (sessions.get(player.getUniqueId()) == s) stop(player);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
        lastAnimations.remove(event.getPlayer().getUniqueId());
        bodyHandMode.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getEntity());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        stop(event.getPlayer());
    }
}
