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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-side 3D inspect, animated purely through display interpolation: the server sends one target
 * transformation per sample, the client interpolates. Entities are never saved to disk and are
 * removed on every exit path.
 *
 * <p>A skin held in the hand plays a held-pose inspect: the weapon's rig turns in the player's own
 * skin arms, locked to the first-person view, while everyone else (and the owner's F5 view) sees the
 * player model turn it in its real hand. Gloves, inspects from menus and servers without the pack
 * use the articulated profile inspects, assembled in front of the player.
 */
public final class InspectService implements Listener {

    /** Model space (x right, y up, z towards the viewer) to display-local space. */
    private static final Matrix4f BASIS = new Matrix4f().rotateY((float) Math.PI);

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
            if (!reveal) ctx.messages(player).send(player, def == null ? "inspect.no-knife" : "general.no-permission");
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
            ctx.messages(player).send(player, "command.inspect-cooldown");
            return false;
        }
        InspectModels m = models.get();
        if (!reveal && heldPose(player, def, instance, m)) {
            InspectAnimation view = m.chooseViewAnimation(def.weapon(), lastAnimations.get(player.getUniqueId()));
            if (view != null) return startView(player, def, instance, view, bodyHand, m);
        }
        String modelId = def.weapon().inspectModel() == null ? def.weapon().id() : def.weapon().inspectModel();
        KnifeModel model = m.model(modelId);
        if (!def.isKnife() && !m.hasModel(modelId)) model = m.model(def.weapon().category().name().toLowerCase(java.util.Locale.ROOT));
        if (model == null) {
            if (!reveal) ctx.messages(player).send(player, "command.inspect-unavailable");
            return false;
        }
        InspectAnimation animation = reveal ? m.animation("reveal")
                : m.chooseAnimation(def, model, lastAnimations.get(player.getUniqueId()));
        if (animation == null) {
            if (!reveal) ctx.messages(player).send(player, "command.inspect-unavailable");
            return false;
        }
        if (!reveal) lastAnimations.put(player.getUniqueId(), animation.id());
        ctx.gallery().close(player);
        List<ModelPart> parts = InspectRig.blockParts(def.weapon(), model.parts());
        if (ctx.settings().resourcePack().enabled() && m.packModel()) {
            parts = InspectRig.packParts(def.weapon(), m.packModelScale());
        }
        InspectModels.Anchor selectedAnchor = reveal ? m.revealAnchor() : bodyHand ? m.handAnchor() : m.anchor();
        InspectModels.Anchor observerAnchor = m.handAnchor();
        if (InspectRig.paired(def.weapon())) {
            selectedAnchor = new InspectModels.Anchor(selectedAnchor.forward(), 0, selectedAnchor.up());
            observerAnchor = new InspectModels.Anchor(observerAnchor.forward(), 0, observerAnchor.up());
        }
        float scale = reveal ? 1f : bodyHand ? m.handModelScale() : m.modelScale();
        Location anchor = anchor(player, selectedAnchor, bodyHand);
        boolean leftHand = player.getMainHand() == org.bukkit.inventory.MainHand.LEFT;
        Location handAnchor = anchor(player, observerAnchor, true);
        List<InspectSession.PartEntity> spawned = new ArrayList<>();
        Map<String, Material> resolved = resolveMaterials(def, instance, m);
        ItemStack skinItem = SkinIcons.icon(def, ctx.formatter(player).name(def, instance), List.of(), ctx.settings(), false);
        try {
        for (ModelPart part : parts) {
            Display display = spawn(anchor, part, resolved, skinItem, animation, m, scale, leftHand, bodyHand, false);
            if (display != null) {
                spawned.add(new InspectSession.PartEntity(part, display, false));
                player.showEntity(ctx.plugin(), display);
            }
            Display observer = spawn(handAnchor, part, resolved, skinItem, animation, m, m.handModelScale(), leftHand, true, true);
            if (observer != null) {
                spawned.add(new InspectSession.PartEntity(part, observer, true));
                player.hideEntity(ctx.plugin(), observer);
            }
        }
        InspectSession session = new InspectSession(player.getUniqueId(), animation, spawned, reveal, leftHand, bodyHand,
                scale, selectedAnchor, observerAnchor, m.handModelScale(), false);
        session.lastAnchor = anchor;
        session.lastHandAnchor = handAnchor;
        sessions.put(player.getUniqueId(), session);
        if (ctx.settings().inspect().actionBar()) {
            player.sendActionBar(ctx.formatter(player).actionBar(def, instance));
        }
        // first sample is sent one tick after spawning, otherwise the client would skip the interpolation
        session.task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> tick(player, session, def, instance), 1L, 1L);
        return true;
        } catch (RuntimeException failure) {
            sessions.remove(player.getUniqueId());
            for (InspectSession.PartEntity part : spawned) part.entity().remove();
            if (!reveal) ctx.knives().refreshHeld(player);
            ctx.plugin().getLogger().log(java.util.logging.Level.WARNING, "Cannot start inspect for " + def.weapon().id(), failure);
            if (!reveal) dev.plattnericus.cases.command.CommandFeedback.failure(ctx, player, "inspect", failure);
            return false;
        }
    }

    private void tick(Player player, InspectSession s, SkinDefinition def, SkinInstance instance) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != s) {
            stop(player.getUniqueId());
            return;
        }
        int t = s.tick;
        glideCamera(player, s);
        // Shift + right click: the scene starts once the player has stood up and the riders have moved
        // to their new seat (or when they keep crouching). Until then the normal hold stays in view.
        if (s.view != null && t == 0 && s.standWait != 0) {
            if (s.standWait > 0) s.standWait = player.isSneaking() ? s.standWait - 1 : -SETTLE_TICKS;
            else s.standWait++;
            if (s.standWait != 0) return;
        }
        if (s.leftHand != (player.getMainHand() == org.bukkit.inventory.MainHand.LEFT)) {
            stop(player);
            return;
        }
        // The held item is hidden client side only. A cancelled right click makes the server resend the
        // hand slot in the same tick, so the hide is sent from the next tick on and re-asserted regularly.
        if (ctx.settings().inspect().hideHeldItem() && t % 10 == 0 && t <= s.animation.duration()) {
            setHandVisible(player, false, s.reveal);
        }
        // The held-pose scene ends in the hold: the owner's item comes back a tick before the scene goes,
        // so the view model never blinks.
        if (s.view != null && ctx.settings().inspect().hideHeldItem() && t == s.animation.duration() + 1) {
            player.sendEquipmentChange(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand());
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
        // The view-locked scene is re-targeted every tick: the riding offset changes with pitch and pose.
        if (s.view != null) {
            if (t == 0) placeView(player, s, 0, 0);
            else placeView(player, s, Math.min(t + 1, s.animation.duration()), 1);
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
            String group = group(s.animation, pe.part());
            Matrix4f pose = poses.computeIfAbsent(group, key -> s.animation.groupMatrix(key, targetTick));
            Matrix4f target = matrix(pose, body, pe.part(), pe.observers() ? s.handScale : s.scale,
                    s.leftHand, pe.observers() || s.bodyHand);
            // A held joint must finish its prior tween. Resetting only its start time replays
            // that movement even though the target matrix is unchanged.
            Matrix4f previous = s.lastMatrices.put(d.getUniqueId(), target);
            if (duration != 0 && target.equals(previous)) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformationMatrix(target);
        }
    }

    private static Matrix4f matrix(InspectAnimation animation, ModelPart part, int t, float scale, boolean leftHand, boolean bodyHand) {
        return InspectTransform.at(animation, part, t, scale, leftHand, bodyHand);
    }
    private static String group(InspectAnimation animation, ModelPart part) { return InspectTransform.group(animation, part); }
    private static Matrix4f matrix(Matrix4f raw, Matrix4f body, ModelPart part, float scale, boolean leftHand, boolean bodyHand) {
        return InspectTransform.matrix(raw, body, part, scale, leftHand, bodyHand);
    }

    private static void transform(Display display, Matrix4f target, int duration) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformationMatrix(target);
    }

    /**
     * One client camera tick: the camera halves the gap to the eye height of the current pose, starting
     * the tick after the client switched pose. {@code cameraEye} is where the camera is at the end of
     * the client tick that this server tick's placement reaches.
     */
    private static void glideCamera(Player player, InspectSession s) {
        if (s.poseTicks > 0 && (Math.abs(player.getHeight() - s.poseHeight) < 1e-3 || --s.poseTicks == 0)) s.poseTicks = 0;
        double target = eyeHeight(player, s);
        s.cameraEyeBefore = Double.isNaN(s.cameraEye) ? target : s.cameraEye;
        s.cameraEye = s.cameraEyeBefore + (target - s.cameraEyeBefore) * .5;
    }

    /** The owner's eye height as their client has it: a shift toggle is seen before the server's pose. */
    private static double eyeHeight(Player player, InspectSession s) {
        return s.poseTicks > 0 ? s.poseEye : player.getEyeHeight();
    }

    /** The owner's box height (the riders' seat) as their client has it. */
    private static double boxHeight(Player player, InspectSession s) {
        return s.poseTicks > 0 ? s.poseHeight : player.getHeight();
    }

    private static Vector3f ridingOffset(Player player, InspectSession s) {
        return ridingOffset(player, s, false);
    }

    /**
     * Where the riding displays sit relative to the eyes, in view space. Riders sit on top of the
     * player's box, which jumps with the pose (crouching, standing up), while the client's camera
     * glides to the new eye height (see glideCamera). A placement that snaps instead of gliding in
     * ({@code snap}) holds through its whole tick, so it aims at the camera halfway through it.
     */
    private static Vector3f ridingOffset(Player player, InspectSession s, boolean snap) {
        Location eye = player.getEyeLocation();
        double target = eyeHeight(player, s);
        double camera = Double.isNaN(s.cameraEye) ? target : snap ? (s.cameraEyeBefore + s.cameraEye) / 2 : s.cameraEye;
        Vector world = new Vector(0, boxHeight(player, s) - camera, 0);
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) {
            double yaw = Math.toRadians(eye.getYaw());
            right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Vector3f((float) world.dot(right), (float) world.dot(up), (float) -world.dot(forward));
    }

    /**
     * Held-pose inspects need the pack's arms and rigs, and they play for the skin shown in the hand;
     * other inspects (menus, gloves) keep the profile scene in front of the player.
     */
    private boolean heldPose(Player player, SkinDefinition def, SkinInstance instance, InspectModels m) {
        if (!ctx.settings().resourcePack().enabled() || !m.packModel() || !ctx.settings().inspect().heldPose() || !HeldRig.held(def.weapon())) {
            return false;
        }
        SkinInstance held = ctx.knives().heldSkin(player);
        return held != null && held.id().equals(instance.id());
    }

    /** Starts one named held-pose inspect; used by development checks to capture every animation. */
    public boolean startView(Player player, SkinInstance instance, String animationId) {
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        InspectModels m = models.get();
        InspectAnimation animation = m.viewAnimation(animationId);
        if (def == null || animation == null || isInspecting(player)) return false;
        return startView(player, def, instance, animation, bodyHandMode(player), m);
    }

    /**
     * A bow or crossbow must not draw, charge or fire while it is inspected, and holding the right
     * mouse button must not flash the real item back into the hand. A cancelled click makes the
     * server resend the real item, so the use is blocked with a cooldown instead; the inspect copies
     * have their own cooldown group, so their icon shows no sweep. The client gets a copy without a
     * use action right away, so it does not start drawing on its own before the scene appears.
     */
    private void blockUse(Player player, InspectSession s) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BOW && held.getType() != Material.CROSSBOW) return;
        player.setCooldown(held, MOUNT_TICKS_INT + STAND_WAIT_TICKS + s.animation.duration() + 20);
        s.blockedUse = held.clone();
        if (ctx.settings().resourcePack().enabled() && ctx.settings().inspect().hideHeldItem() && ctx.knives().cosmetics().isCosmetic(held)) {
            player.sendEquipmentChange(player, EquipmentSlot.HAND, ctx.knives().cosmetics().ready(held));
        }
    }

    /** Observers see the held-pose inspect at the body hand at life size. */
    private static final float OBSERVER_SCALE = 1.1f;
    /** The furthest the body-hand weapon is held ahead of the fist to stay clear of the model. */
    private static final float MAX_BODY_PUSH = .45f;
    /** How long an inspect started while crouching waits for the player to stand up. */
    private static final int STAND_WAIT_TICKS = 40;
    /** Ticks after standing up before the scene shows: the riders' seat has moved by then. */
    private static final int SETTLE_TICKS = 2;
    /** Ticks between spawning the view-locked riders and showing them. */
    private static final long MOUNT_TICKS = 3L;
    private static final int MOUNT_TICKS_INT = (int) MOUNT_TICKS;
    private static final Matrix4f MIRROR = new Matrix4f().scaling(-1, 1, 1);

    /**
     * Held-pose inspect: the weapon starts and ends in the first-person hold and the owner's skin arms
     * keep holding it, so the inspect reads like the player's own hands turning the weapon.
     */
    private boolean startView(Player player, SkinDefinition def, SkinInstance instance, InspectAnimation animation,
                              boolean bodyHand, InspectModels m) {
        dev.plattnericus.cases.render.SpriteFrame frame;
        List<Vector3f> hull;
        try {
            var assets = ctx.textures().weapon(def.weapon().id(), def.weapon().textureFolder());
            frame = HeldRig.rigFrame(def.weapon(), assets);
            hull = ModelPose.hull(assets, frame);
        } catch (dev.plattnericus.cases.render.TextureException e) {
            return false;
        }
        lastAnimations.put(player.getUniqueId(), animation.id());
        ctx.gallery().close(player);
        HeldRig.Stance stance = HeldRig.stance(def.weapon(), frame);
        boolean leftHand = player.getMainHand() == org.bukkit.inventory.MainHand.LEFT;
        boolean dual = HeldRig.dual(def.weapon());
        boolean slim = dev.plattnericus.cases.knife.PlayerArms.slim(player.getPlayerProfile());
        ItemStack skinItem = SkinIcons.icon(def, ctx.formatter(player).name(def, instance), List.of(), ctx.settings(), false);
        Location hand = modelHand(player, false), offHand = modelHand(player, true);
        List<String> layers = InspectRig.layers(def.weapon()).stream().map(InspectRig.Layer::id).distinct().toList();
        List<InspectSession.ViewDisplay> displays = new ArrayList<>();
        try {
            for (boolean copy : dual ? List.of(false, true) : List.of(false)) {
                // Every rig layer is its own display, so slides, handles and blades can move in the hand.
                for (String layer : layers) {
                    if (!bodyHand) displays.add(new InspectSession.ViewDisplay(rider(player, layerItem(skinItem, layer, HeldRig.NEAR_SCENE), m),
                            InspectSession.Role.WEAPON, layer, copy, false, false));
                    // Everyone else sees the body-hand scene; the owner gets a copy that only shows in third person.
                    ItemDisplay observer = bodyHandDisplay(copy ? offHand : hand, layerItem(skinItem, layer, null), m, true);
                    if (!bodyHand) player.hideEntity(ctx.plugin(), observer);
                    displays.add(new InspectSession.ViewDisplay(observer, InspectSession.Role.WEAPON, layer, copy, true, false));
                    if (!bodyHand) {
                        ItemDisplay own = bodyHandDisplay(copy ? offHand : hand, layerItem(skinItem, layer, HeldRig.FAR_SCENE), m, false);
                        player.showEntity(ctx.plugin(), own);
                        displays.add(new InspectSession.ViewDisplay(own, InspectSession.Role.WEAPON, layer, copy, true, true));
                    }
                }
                if (!bodyHand) {
                    displays.add(new InspectSession.ViewDisplay(rider(player, armItem(player, leftHand != copy, slim), m),
                            InspectSession.Role.MAIN_ARM, null, copy, false, false));
                    if (stance.twoHanded() && !copy) displays.add(new InspectSession.ViewDisplay(
                            rider(player, armItem(player, !leftHand, slim), m), InspectSession.Role.SUPPORT_ARM, null, false, false, false));
                }
            }
        } catch (RuntimeException failure) {
            displays.forEach(d -> d.entity().remove());
            ctx.plugin().getLogger().log(java.util.logging.Level.WARNING, "Cannot start inspect for " + def.weapon().id(), failure);
            return false;
        }
        InspectSession session = new InspectSession(player.getUniqueId(), animation, List.of(), false, leftHand, bodyHand,
                1f, m.anchor(), m.handAnchor(), m.handModelScale(), !bodyHand);
        session.view = new InspectSession.ViewSetup(def.weapon(), frame, stance, HeldRig.bodyStance(def.weapon(), frame), dual,
                OBSERVER_SCALE, List.copyOf(displays), ViewInspect.Variation.random(java.util.concurrent.ThreadLocalRandom.current()), hull);
        session.lastHandAnchor = hand;
        session.standWait = player.isSneaking() && !bodyHand ? STAND_WAIT_TICKS : 0;
        blockUse(player, session);
        sessions.put(player.getUniqueId(), session);
        if (ctx.settings().inspect().actionBar()) player.sendActionBar(ctx.formatter(player).actionBar(def, instance));
        // The riders spawn shrunk to nothing: until the client has seated them on the player they would
        // face the camera from the feet. The scene appears, and the item hides, once they ride.
        session.task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> tick(player, session, def, instance), MOUNT_TICKS, 1L);
        return true;
    }

    /** One tick of a held-pose inspect for the owner's view-locked scene and the observers' body-hand scene. */
    private void placeView(Player player, InspectSession s, int tick, int duration) {
        InspectSession.ViewSetup v = s.view;
        ViewInspect.Sample own = ViewInspect.sample(v.weapon(), v.frame(), v.stance(), s.animation, tick, v.variation());
        ViewInspect.Sample body = ViewInspect.sampleBody(v.weapon(), v.frame(), v.bodyStance(), s.animation, tick, v.variation());
        // The client seats the riders at the new height at once when the pose changes: the correction
        // then snaps instead of gliding in over a tick.
        double seat = boxHeight(player, s);
        boolean seatMoved = !Double.isNaN(s.riderHeight) && Math.abs(seat - s.riderHeight) > 1e-3;
        s.riderHeight = seat;
        Vector3f ride = s.viewLocked ? ridingOffset(player, s, seatMoved) : new Vector3f();
        ModelPose pose = modelPose(player);
        float[] push = {bodyPush(s, pose, tick, false), v.dual() ? bodyPush(s, pose, tick, true) : 0};
        for (InspectSession.ViewDisplay vd : v.displays()) {
            if (!vd.entity().isValid()) continue;
            Matrix4f side = s.leftHand != vd.mirrored() ? MIRROR : new Matrix4f();
            Matrix4f target;
            if (vd.observers()) {
                target = new Matrix4f(BASIS).translate(0, 0, -push[vd.mirrored() ? 1 : 0]).mul(side).scale(v.observerScale())
                        .mul(body.weapon(vd.layer())).translate(.5f, .5f, .5f);
            } else if (vd.role() == InspectSession.Role.WEAPON) {
                target = new Matrix4f().translation(new Vector3f(ride).negate()).mul(side).mul(own.weapon(vd.layer())).translate(.5f, .5f, .5f);
            } else {
                boolean support = vd.role() == InspectSession.Role.SUPPORT_ARM;
                Vector3f hand = side.transformPosition(new Vector3f(support ? own.supportHand() : own.hand())).sub(ride);
                Vector3f elbow = side.transformPosition(new Vector3f(support ? own.supportElbow() : own.elbow())).sub(ride);
                Vector3f outward = support ? new Vector3f(-1, 0, 0) : new Vector3f(own.reference());
                side.transformDirection(outward);
                // Item displays turn their item half a revolution; the last rotation cancels that.
                target = HeldRig.armFrame(hand, elbow, outward).scale(v.stance().armScale()).rotateY((float) Math.PI);
            }
            transform(vd.entity(), target, seatMoved && !vd.observers() ? 0 : duration);
        }
    }

    /**
     * How far ahead of the fist the body-hand weapon is held so that no part of it cuts into the
     * player model's head or torso. It looks a few ticks ahead and eases back, so it never jumps.
     */
    private static float bodyPush(InspectSession s, ModelPose pose, int tick, boolean offHand) {
        InspectSession.ViewSetup v = s.view;
        Matrix4f side = s.leftHand != offHand ? MIRROR : new Matrix4f();
        float needed = 0;
        for (int ahead = 0; ahead <= 3; ahead++) {
            ViewInspect.Sample sample = ViewInspect.sampleBody(v.weapon(), v.frame(), v.bodyStance(), s.animation,
                    Math.min(tick + ahead, s.animation.duration()), v.variation());
            Matrix4f weapon = new Matrix4f(side).scale(v.observerScale()).mul(sample.weapon());
            needed = Math.max(needed, pose.clearance(v.hull(), weapon, offHand));
        }
        int hand = offHand ? 1 : 0;
        s.bodyPush[hand] = Math.max(Math.min(needed, MAX_BODY_PUSH), s.bodyPush[hand] - .03f);
        return s.bodyPush[hand];
    }

    private ItemDisplay bodyHandDisplay(Location hand, ItemStack item, InspectModels m, boolean visible) {
        return hand.getWorld().spawn(hand, ItemDisplay.class, d -> {
            displayDefaults(d, m, visible);
            d.setTeleportDuration(2);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        });
    }

    /**
     * One layer of the skin's inspect rig, marked for the item shader's near-only or away-only scene
     * ({@code scene}), or unmarked for everyone else.
     */
    private static ItemStack layerItem(ItemStack skinItem, String layer, String scene) {
        ItemStack item = skinItem.clone();
        item.editMeta(meta -> {
            org.bukkit.NamespacedKey base = meta.getItemModel();
            if (base != null) meta.setItemModel(new org.bukkit.NamespacedKey(base.getNamespace(),
                    "inspect/" + base.getKey().replaceFirst("^skin/", "") + "/" + layer));
        });
        if (scene != null) item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA,
                io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addString(scene).build());
        return item;
    }

    private ItemDisplay rider(Player player, ItemStack item, InspectModels m) {
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
            displayDefaults(d, m, false);
            d.setTeleportDuration(0);
            d.setBillboard(Display.Billboard.CENTER);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        });
        player.showEntity(ctx.plugin(), display);
        player.addPassenger(display);
        return display;
    }

    private void displayDefaults(Display d, InspectModels m, boolean visible) {
        d.setPersistent(false);
        d.setVisibleByDefault(visible);
        d.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
        d.setInterpolationDuration(0);
        d.setTransformationMatrix(new Matrix4f().scaling(0));
        d.setViewRange(m.viewRange());
        d.setShadowRadius(0);
        if (m.brightness() >= 0) d.setBrightness(new Display.Brightness(m.brightness(), m.brightness()));
    }

    /** A skin arm item: the pack's arm model wearing the player's own skin. */
    private ItemStack armItem(Player player, boolean left, boolean slim) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, new org.bukkit.NamespacedKey(
                ctx.settings().resourcePack().namespace(), "arm/" + (left ? "left" : "right") + "_" + (slim ? "slim" : "wide")));
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE,
                io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
        return item;
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
        if (s.view != null) {
            Location model = modelHand(player, false);
            if (moved(s.lastModelHand, model, m.followThreshold())) {
                s.lastModelHand = model;
                Location offHand = s.view.dual() ? modelHand(player, true) : model;
                for (InspectSession.ViewDisplay vd : s.view.displays()) {
                    if (vd.observers() && vd.entity().isValid()) vd.entity().teleport(vd.mirrored() ? offHand : model);
                }
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
        if (s.view != null) for (InspectSession.ViewDisplay vd : s.view.displays()) {
            vd.entity().remove();
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null && s.blockedUse != null) player.setCooldown(s.blockedUse, 0);
        if (player != null) {
            if (!s.reveal) {
                cooldowns.put(id, (long) Bukkit.getCurrentTick() + ctx.settings().inspect().cooldownTicks());
            }
            if (ctx.settings().inspect().hideHeldItem()) {
                setHandVisible(player, true, false);
            }
            player.updateInventory();
            if (player.isOnline()) ctx.knives().refreshHeld(player);
        }
    }

    /**
     * Hides (or restores) the inspecting player's held item for the player and everyone who can see
     * them. Purely visual: equipment packets only, the server inventory is never touched. The owner
     * receives a copy that keeps its hotbar icon but has no first-person model, so the slot never
     * looks empty; without the pack the owner simply keeps the item in view.
     */
    private void setHandVisible(Player player, boolean visible, boolean keepOwnerVisible) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean ownCopy = ctx.settings().resourcePack().enabled() && ctx.knives().cosmetics().isCosmetic(held);
        InspectSession session = sessions.get(player.getUniqueId());
        // Held-pose inspects show a loaded crossbow to put the model's arms into the crossbow hold.
        boolean aim = session != null && session.view != null;
        ItemStack copy = ownCopy && !visible ? ctx.knives().cosmetics().inspecting(held, aim) : null;
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer == player && (keepOwnerVisible || (!visible && !ownCopy))) continue;
            if (viewer == player || (viewer.canSee(player) && viewer.getLocation().distanceSquared(player.getLocation()) < 128 * 128)) {
                ItemStack shown = visible ? held : copy != null ? copy : ItemStack.empty();
                viewer.sendEquipmentChange(player, EquipmentSlot.HAND, shown);
            }
        }
    }

    /**
     * Where the player model's hand is during a held-pose inspect. Other players see every weapon in
     * Minecraft's crossbow hold: it follows the head and does not swing while walking, so the weapon
     * stays in the fist. {@code offHand} gives the second hand (the other Shadow Dagger).
     */
    private static Location modelHand(Player player, boolean offHand) {
        Vector3f hand = modelPose(player).hand(offHand);
        double yaw = Math.toRadians(player.getBodyYaw());
        Vector f = new Vector(-Math.sin(yaw), 0, Math.cos(yaw)), r = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Location at = player.getLocation().add(r.multiply(hand.x)).add(0, hand.y, 0).add(f.multiply(hand.z));
        at.setYaw(player.getLocation().getYaw());
        at.setPitch(player.getLocation().getPitch());
        return at;
    }

    /** The player model's pose as other players' clients draw it. */
    private static ModelPose modelPose(Player player) {
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        return new ModelPose(player.getLocation().getPitch(), (float) wrap(player.getLocation().getYaw() - player.getBodyYaw()),
                player.isSneaking(), attribute == null ? 1 : (float) attribute.getValue(), player.getMainHand() == org.bukkit.inventory.MainHand.LEFT);
    }

    private static double wrap(double degrees) {
        degrees %= 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
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
        // During an inspect a cooldown blocks the weapon's use (see blockUse); cancelling here would
        // make the server resend the real item and flash it over the scene.
        if (isInspecting(player)) return;
        if (!player.isSneaking() || !player.hasPermission("mccases.inspect")) {
            return;
        }
        SkinInstance knife = ctx.knives().heldSkin(player);
        if (knife != null && start(player, knife, false)) {
            InspectSession s = sessions.get(player.getUniqueId());
            if (s == null || s.view == null || s.blockedUse == null) event.setCancelled(true);
        }
    }

    /**
     * Crouching or standing up during an inspect: the owner's client switches its pose at once and
     * seats the riding scene 0.3 blocks lower or higher, while the server's pose follows a tick later.
     * The next placement already uses the client's new pose, so the scene follows the seat at once.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        InspectSession s = sessions.get(player.getUniqueId());
        if (s == null || s.view == null || !s.viewLocked || player.isFlying() || player.isGliding() || player.isSwimming()
                || player.isInsideVehicle()) return;
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        double scale = attribute == null ? 1 : attribute.getValue();
        s.poseHeight = (event.isSneaking() ? 1.5 : 1.8) * scale;
        s.poseEye = (event.isSneaking() ? 1.27 : 1.62) * scale;
        s.poseTicks = 6;
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
