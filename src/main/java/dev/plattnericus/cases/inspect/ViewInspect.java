package dev.plattnericus.cases.inspect;

import dev.plattnericus.cases.catalog.WeaponType;
import dev.plattnericus.cases.render.SpriteFrame;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;

/**
 * Held-pose inspect: one tick of a view animation turned into weapon and arm placements.
 *
 * <p>Animations start and end in the first-person hold, so the inspect continues from the held
 * view model without a jump. Groups:
 * <ul>
 *   <li>{@code weapon}: {@code move} shifts the hand in view space (blocks); {@code rotate} turns the
 *   weapon about the grip in its own frame (x along the blade or barrel, y up the artwork, z out of
 *   its flat side) like a wrist; {@code scale} x scales it.</li>
 *   <li>{@code elbow}: {@code move} shifts the main elbow; the shoulder also follows the hand.</li>
 *   <li>{@code grip}: {@code move} x releases the main hand (0 holds, 1 lets go for tosses),
 *   y releases the support hand.</li>
 *   <li>{@code support}: {@code move} slides the support hand along the weapon (canvas units), for
 *   example from the handguard to the magazine.</li>
 *   <li>Rig layers ({@code mechanism}, {@code blade}, {@code handle_a}, {@code handle_b}): {@code move}
 *   shifts the layer along the weapon (canvas units), {@code rotate} turns it about its joint
 *   ({@link HeldRig#hinge}); the rest of the rig stays in the hand.</li>
 * </ul>
 * The forearm twists with the wrist roll, so turning the weapon over also turns the arm.
 */
public final class ViewInspect {

    /** How much of the hand's travel the shoulder carries along. */
    private static final float SHOULDER_FOLLOW = .55f;
    /** How much of the wrist roll the forearm takes over. */
    private static final float FOREARM_TWIST = .6f;
    /** Rig layers an animation may move on their own. */
    private static final List<String> LAYERS = List.of("mechanism", "blade", "handle_a", "handle_b");
    /**
     * The body-hand grip sits this far below and ahead of the model's fist centre (still inside the
     * fist), so the weapon stays under the chin and clear of the face while it turns.
     */
    private static final float BODY_DROP = .08f, BODY_AHEAD = .06f;

    /**
     * One tick: {@code weapon} places the whole rig; {@code layers} places the movable rig layers that
     * this animation moves (slide, bolt, pump, butterfly handles, folding blade), see {@link #weapon(String)}.
     */
    public record Sample(Matrix4f weapon, Map<String, Matrix4f> layers, Vector3f hand, Vector3f elbow, Vector3f reference,
                         Vector3f supportHand, Vector3f supportElbow) {
        /** Placement of one rig layer: the weapon's, moved about its joint when the animation moves it. */
        public Matrix4f weapon(String layer) {
            return layers.getOrDefault(layer, weapon);
        }
    }

    /**
     * Per-playback variety: a little more or less energy and, for half of all inspects, the wrist rolls
     * the other way. Rest poses and full spins are kept, so every variation still starts and ends in the hold.
     */
    public record Variation(float intensity, float roll) {
        public static final Variation NONE = new Variation(1, 1);

        public static Variation random(java.util.random.RandomGenerator random) {
            return new Variation(.88f + random.nextFloat() * .24f, random.nextBoolean() ? 1 : -1);
        }
    }

    private ViewInspect() {
    }

    /**
     * Samples tick {@code t}. {@code origin} moves the whole hold (view space); pass the negated stance
     * grip to put the grip at the origin, for scenes anchored at a body hand.
     */
    public static Sample sample(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, InspectAnimation animation, int t,
                                Vector3fc origin) {
        return sample(weapon, frame, stance, animation, t, origin, false, Variation.NONE);
    }

    public static Sample sample(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, InspectAnimation animation, int t,
                                Variation variation) {
        return sample(weapon, frame, stance, animation, t, new Vector3f(), false, variation);
    }

    /**
     * The same tick at the player model's hand: the grip sits at the origin. The model's arms cannot
     * follow, so the grip stays in the fist: travel shrinks to a small lift in front of the chest (tosses
     * still fly) and the wrist turns are a little calmer next to the body.
     */
    public static Sample sampleBody(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, InspectAnimation animation, int t,
                                    Variation variation) {
        return sample(weapon, frame, stance, animation, t, new Vector3f(stance.grip()).negate(), true, variation);
    }

    private static Sample sample(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, InspectAnimation animation, int t,
                                 Vector3fc origin, boolean body, Variation variation) {
        Pose w = animation.pose("weapon", t), elbowPose = animation.pose("elbow", t);
        Vector3fc end = animation.pose("weapon", animation.duration()).rotate();
        w = new Pose(new Vector3f(w.move()).mul(.9f + .1f * variation.intensity()), wrist(animation, t, variation), w.scale());
        if (body) {
            Vector3f move = w.move();
            float toss = clamp(animation.pose("grip", t).move().x);
            // Turns about the artwork's up axis bring the muzzle or tip towards the first-person camera;
            // across the chest that is forwards, towards the people watching, not back into the head.
            w = new Pose(new Vector3f(move.x * .2f, move.y * (.3f + .7f * toss) + Math.max(0, move.z) * .15f - BODY_DROP,
                    -Math.abs(move.z) * .1f - BODY_AHEAD), scale(w.rotate(), end, .85f, -.85f, .85f), w.scale());
        }
        Pose grip = animation.pose("grip", t), support = animation.pose("support", t);
        Vector3f gripView = new Vector3f(stance.grip()).add(origin).add(w.move());
        Matrix4f wrist = new Matrix4f().rotateXYZ(rad(w.rotate().x), rad(w.rotate().y), rad(w.rotate().z));
        Matrix4f placement = new Matrix4f().translation(gripView).mul(stance.rotation()).mul(wrist)
                .scale(stance.scale() * Math.max(.05f, w.scale().x));
        Matrix4f gripFrame = HeldRig.gripFrame(weapon, frame);
        Matrix4f weaponMatrix = new Matrix4f(placement).mul(gripFrame);
        Map<String, Matrix4f> layers = new java.util.HashMap<>();
        for (String layer : LAYERS) {
            if (!animation.hasGroup(layer)) continue;
            Pose motion = animation.pose(layer, t);
            double[] joint = HeldRig.hinge(weapon, layer);
            Vector3f hinge = HeldRig.inGripFrame(weapon, frame, joint[0], joint[1]);
            Matrix4f articulation = new Matrix4f().translate(motion.move()).translate(hinge)
                    .rotateXYZ(rad(motion.rotate().x), rad(motion.rotate().y), rad(motion.rotate().z)).translate(hinge.negate());
            layers.put(layer, new Matrix4f(placement).mul(articulation).mul(gripFrame));
        }

        Vector3f restHand = new Vector3f(stance.grip()).add(origin);
        float releaseMain = clamp(grip.move().x), releaseSupport = clamp(grip.move().y);
        // A released hand stays a little below where it held the weapon, ready to catch it again.
        Vector3f hand = new Vector3f(gripView).lerp(new Vector3f(restHand).add(.02f, -.07f, .05f), releaseMain);
        Vector3f elbow = new Vector3f(restHand).add(stance.elbow()).add(elbowPose.move())
                .add(new Vector3f(hand).sub(restHand).mul(SHOULDER_FOLLOW));
        Vector3f forearm = new Vector3f(hand).sub(elbow).normalize();
        Vector3f reference = new Vector3f(1, 0, 0).rotateAxis(rad(w.rotate().x * FOREARM_TWIST * (1 - releaseMain)),
                forearm.x, forearm.y, forearm.z);

        Vector3f supportHand = null, supportElbow = null;
        if (stance.twoHanded()) {
            Vector3f restSupport = placementAtRest(stance, origin).transformPosition(new Vector3f(stance.support()));
            Vector3f attached = placement.transformPosition(new Vector3f(stance.support()).add(support.move()));
            supportHand = attached.lerp(new Vector3f(restSupport).add(-.06f, -.20f, .12f), releaseSupport);
            supportElbow = new Vector3f(restSupport).add(stance.supportElbow())
                    .add(new Vector3f(supportHand).sub(restSupport).mul(SHOULDER_FOLLOW));
        }
        return new Sample(weaponMatrix, Map.copyOf(layers), hand, elbow, reference, supportHand, supportElbow);
    }

    /**
     * Scales wrist turns that come back to zero. Full spins (axes that end on a whole number of turns)
     * keep their angles and only take the sign, so every variation still ends exactly in the hold.
     */
    private static Vector3f scale(Vector3fc rotate, Vector3fc end, float x, float y, float z) {
        return new Vector3f(rotate.x() * (end.x() == 0 ? x : Math.signum(x)), rotate.y() * (end.y() == 0 ? y : Math.signum(y)),
                rotate.z() * (end.z() == 0 ? z : Math.signum(z)));
    }

    /** The wrist turn of tick {@code t} in degrees (x roll about the blade or barrel, y, z), with the variation applied. */
    public static Vector3f wrist(InspectAnimation animation, int t, Variation variation) {
        Vector3fc end = animation.pose("weapon", animation.duration()).rotate();
        return scale(animation.pose("weapon", t).rotate(), end, variation.roll() * variation.intensity(), variation.intensity(),
                variation.intensity());
    }

    /** Whether the main hand holds the weapon at tick {@code t} (it lets go for tosses). */
    public static boolean held(InspectAnimation animation, int t) {
        return animation.pose("grip", t).move().x < .5f;
    }

    private static Matrix4f placementAtRest(HeldRig.Stance stance, Vector3fc origin) {
        return new Matrix4f().translation(new Vector3f(stance.grip()).add(origin)).mul(stance.rotation()).scale(stance.scale());
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
