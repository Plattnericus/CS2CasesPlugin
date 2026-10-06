package dev.plattnericus.cases.inspect;

import org.joml.Vector3f;

/** Translation, euler rotation (degrees, XYZ) and scale of an animation group. */
public record Pose(Vector3f move, Vector3f rotate, Vector3f scale) {

    public static Pose identity() {
        return new Pose(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
    }

    public static Pose lerp(Pose a, Pose b, float t) {
        return new Pose(new Vector3f(a.move).lerp(b.move, t), new Vector3f(a.rotate).lerp(b.rotate, t),
                new Vector3f(a.scale).lerp(b.scale, t));
    }

    float maxAngleDelta(Pose other) {
        Vector3f d = new Vector3f(other.rotate).sub(rotate).absolute();
        return Math.max(d.x, Math.max(d.y, d.z));
    }
}
