package dev.plattnericus.cases.inspect;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/** A running inspect scene: its display entities, timeline position and last anchor. */
final class InspectSession {

    record PartEntity(ModelPart part, Display entity, boolean observers) {
    }

    enum Role { WEAPON, MAIN_ARM, SUPPORT_ARM }

    /**
     * A display of a held-pose inspect. Weapons show one rig layer each ({@code layer}); {@code mirrored}
     * belongs to the second hand of dual weapons.
     */
    record ViewDisplay(Display entity, Role role, String layer, boolean mirrored, boolean observers, boolean ownerCopy) {
    }

    /** Everything a held-pose inspect needs to sample its tick. */
    record ViewSetup(dev.plattnericus.cases.catalog.WeaponType weapon, dev.plattnericus.cases.render.SpriteFrame frame,
                     HeldRig.Stance stance, HeldRig.Stance bodyStance, boolean dual, float observerScale, List<ViewDisplay> displays,
                     ViewInspect.Variation variation, List<org.joml.Vector3f> hull) {
    }

    final UUID player;
    final InspectAnimation animation;
    final List<PartEntity> parts;
    final boolean reveal;
    final boolean leftHand;
    final boolean bodyHand;
    final float scale;
    final InspectModels.Anchor anchor;
    final InspectModels.Anchor handAnchor;
    final float handScale;
    final java.util.Map<UUID, org.joml.Matrix4f> lastMatrices = new java.util.HashMap<>();
    /** The held-pose owner scene rides the player and faces the camera: it stays locked to the view without lag. */
    final boolean viewLocked;
    /** Set for held-pose inspects; then {@link #parts} is empty. */
    ViewSetup view;
    BukkitTask task;
    int tick;
    int sampleIndex;
    Location lastAnchor;
    Location lastHandAnchor;
    Location lastModelHand;
    /** The owner's camera height above the feet as their client glides it after a pose change. */
    double cameraEye = Double.NaN, cameraEyeBefore = Double.NaN;
    /** The player's box height when the riders were last placed; it jumps with the pose. */
    double riderHeight = Double.NaN;
    /**
     * Box and eye height of the pose the owner's client switched to with the shift key, until the
     * server's own pose catches up ({@code poseTicks} counts down as a safety net).
     */
    double poseHeight = Double.NaN, poseEye = Double.NaN;
    int poseTicks;
    /**
     * Ticks a held-pose inspect started while crouching still waits for the player to stand up, so
     * the scene never shows while the client seats it at the new height before the server knows.
     */
    int standWait;
    /** The held bow or crossbow whose use is on cooldown for the inspect, or null. */
    org.bukkit.inventory.ItemStack blockedUse;
    /** How far the body-hand weapon of each hand is held ahead to stay clear of the head and torso. */
    final float[] bodyPush = new float[2];

    InspectSession(UUID player, InspectAnimation animation, List<PartEntity> parts, boolean reveal, boolean leftHand,
                   boolean bodyHand, float scale, InspectModels.Anchor anchor, InspectModels.Anchor handAnchor, float handScale,
                   boolean viewLocked) {
        this.player = player;
        this.animation = animation;
        this.parts = parts;
        this.reveal = reveal;
        this.leftHand = leftHand;
        this.bodyHand = bodyHand;
        this.scale = scale;
        this.anchor = anchor;
        this.handAnchor = handAnchor;
        this.handScale = handScale;
        this.viewLocked = viewLocked;
    }
}
