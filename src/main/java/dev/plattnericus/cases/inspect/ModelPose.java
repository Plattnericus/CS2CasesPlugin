package dev.plattnericus.cases.inspect;

import dev.plattnericus.cases.render.SpriteFrame;
import dev.plattnericus.cases.render.WeaponAssets;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The player model as other players see it during a held-pose inspect: Minecraft's crossbow hold
 * (AnimationUtils.animateCrossbowHold) with HumanoidModel's crouch adjustment. Body space has its
 * origin at the feet, x to the player's right, y up and z forward, in blocks.
 *
 * <p>The body-hand scene is drawn in the head frame at the fist: x right, y up, z back, turned with
 * the head like the display entity that carries it.
 *
 * @param pitch       head pitch in degrees, positive looking down
 * @param relativeYaw head yaw relative to the body in degrees, positive turned right
 * @param crouching   whether the model crouches
 * @param scale       the player's scale attribute
 * @param leftHanded  whether the main hand is the left one
 */
public record ModelPose(float pitch, float relativeYaw, boolean crouching, float scale, boolean leftHanded) {

    /** How far the weapon keeps away from the head and torso surfaces. */
    private static final float MARGIN = .03f;

    /** Fist centre of the main hand or, with {@code offHand}, of the second hand. */
    public Vector3f hand(boolean offHand) {
        float main = leftHanded ? -1 : 1, arm = offHand ? -main : main;
        double xRot = (offHand ? -1.5 : -Math.PI / 2 + .1) + Math.toRadians(pitch) + (crouching ? .4 : 0);
        double yRot = (offHand ? .6 : -.3) * main + Math.toRadians(relativeYaw);
        Matrix3f rotation = new Matrix3f().rotationZYX(0, (float) yRot, (float) xRot);
        // Arm-local fist centre about the shoulder pivot, in pixels with y down and z back.
        Vector3f fist = rotation.transform(new Vector3f(-arm, 9, 0));
        return body(new Vector3f(-5 * arm, 2 + (crouching ? 3.2f : 0), 0).add(fist));
    }

    /** The head frame (and the body-hand display) in body space. */
    public Matrix3f frame() {
        return new Matrix3f().rotationZYX(0, (float) Math.toRadians(relativeYaw), (float) Math.toRadians(pitch));
    }

    /** A head-frame point (x right, y up, z back) at the given hand, in body space. */
    public Vector3f toBody(Vector3fc point, boolean offHand) {
        return frame().transform(new Vector3f(point.x(), point.y(), -point.z())).add(hand(offHand));
    }

    /** How deep a body-space point lies inside the head or torso (with the margin), or 0 when clear. */
    public float intrusion(Vector3fc point) {
        float depth = 0;
        for (Box box : boxes()) {
            Vector3f local = box.local(point);
            float d = Math.min(Math.min(local.x - box.min.x, box.max.x - local.x),
                    Math.min(Math.min(local.y - box.min.y, box.max.y - local.y), Math.min(local.z - box.min.z, box.max.z - local.z)));
            depth = Math.max(depth, d);
        }
        return depth * scale;
    }

    /**
     * The shortest push along the head frame's forward axis that keeps every hull point of the weapon
     * clear of the head and torso. {@code weapon} maps model space to the head frame at the hand.
     */
    public float clearance(List<Vector3f> hull, Matrix4fc weapon, boolean offHand) {
        List<float[]> blocked = new ArrayList<>();
        Matrix3f frame = frame();
        Vector3f hand = hand(offHand), forward = frame.transform(new Vector3f(0, 0, 1)), point = new Vector3f();
        List<Box> boxes = boxes();
        for (Vector3f p : hull) {
            weapon.transformPosition(p, point);
            frame.transform(point.set(point.x, point.y, -point.z)).add(hand);
            for (Box box : boxes) {
                float[] span = box.span(point, forward);
                if (span != null) blocked.add(span);
            }
        }
        // The smallest push outside every span in which some point would be inside a box.
        blocked.sort((a, b) -> Float.compare(a[0], b[0]));
        float push = 0;
        for (float[] span : blocked) {
            if (span[0] > push) break;
            push = Math.max(push, span[1] + 1e-4f);
        }
        return push;
    }

    /** Head and torso, inflated by the margin. */
    private List<Box> boxes() {
        float drop = crouching ? 2 : 0;
        Box head = new Box(new Vector3f(0, (24 - (crouching ? 4.2f : 0) - drop) * scale / 16, 0), frame(),
                new Vector3f(-.25f, 0, -.25f), new Vector3f(.25f, .5f, .25f));
        // A crouching torso leans forwards about its top: the hips go back.
        Box torso = new Box(new Vector3f(0, (24 - (crouching ? 3.2f : 0) - drop) * scale / 16, 0), new Matrix3f().rotationX(crouching ? .5f : 0),
                new Vector3f(-.25f, -.75f, -.125f), new Vector3f(.25f, 0, .125f));
        return List.of(head, torso);
    }

    /** A model box in blocks (unscaled) around a pivot, turned by {@code rotation}. */
    private final class Box {
        final Vector3f pivot, min, max;
        final Matrix3f inverse;

        Box(Vector3f pivot, Matrix3f rotation, Vector3f min, Vector3f max) {
            this.pivot = pivot;
            this.inverse = rotation.transpose(new Matrix3f());
            this.min = min.sub(MARGIN, MARGIN, MARGIN);
            this.max = max.add(MARGIN, MARGIN, MARGIN);
        }

        Vector3f local(Vector3fc point) {
            return inverse.transform(new Vector3f(point).sub(pivot)).div(scale);
        }

        /** The pushes (start, end) along {@code direction} for which {@code point} is inside, or null. */
        float[] span(Vector3fc point, Vector3fc direction) {
            Vector3f o = local(point), d = inverse.transform(new Vector3f(direction)).div(scale);
            float start = Float.NEGATIVE_INFINITY, end = Float.POSITIVE_INFINITY;
            for (int axis = 0; axis < 3; axis++) {
                float origin = o.get(axis), step = d.get(axis), lo = min.get(axis), hi = max.get(axis);
                if (Math.abs(step) < 1e-6f) {
                    if (origin <= lo || origin >= hi) return null;
                    continue;
                }
                float a = (lo - origin) / step, b = (hi - origin) / step;
                start = Math.max(start, Math.min(a, b));
                end = Math.min(end, Math.max(a, b));
            }
            return end > Math.max(start, 0) ? new float[]{Math.max(start, 0), end} : null;
        }
    }

    /** Opaque points of a weapon in item model space, on a coarse grid over its canvas. */
    public static List<Vector3f> hull(WeaponAssets weapon, SpriteFrame frame) {
        List<Vector3f> points = new ArrayList<>();
        int size = weapon.size(), step = Math.max(1, size / 32);
        for (int y = 0; y < size; y += step) {
            for (int x = 0; x < size; x += step) {
                if (weapon.mask()[y * size + x] <= .5f) continue;
                double[] sprite = frame.map((x + .5) / size, (y + .5) / size);
                points.add(new Vector3f((float) sprite[0], (float) (1 - sprite[1]), .5f));
            }
        }
        return points;
    }

    /** Model pixels (y down from the neck line, z back) to body space; a crouching model sits 2 px lower. */
    private Vector3f body(Vector3f pixels) {
        return new Vector3f(-pixels.x, 24 - pixels.y - (crouching ? 2 : 0), -pixels.z).mul(scale / 16);
    }
}
