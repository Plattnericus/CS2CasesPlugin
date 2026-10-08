package dev.plattnericus.cases.inspect;

import org.joml.Matrix4f;

/** Shared scene transform, also used by the frame-by-frame camera verification. */
public final class InspectTransform {
    private InspectTransform() { }
    public static String group(InspectAnimation animation, ModelPart part) {
        String group = animation.hasGroup(part.group()) ? part.group() : "body";
        return group.equals("body") && animation.hasGroup("roll") ? "roll" : group;
    }
    public static Matrix4f at(InspectAnimation animation, ModelPart part, int tick, float scale, boolean left, boolean hand) {
        return matrix(animation.groupMatrix(group(animation, part), tick), animation.groupMatrix("body", tick), part, scale, left, hand);
    }
    public static Matrix4f matrix(Matrix4f raw, Matrix4f body, ModelPart part, float scale, boolean left, boolean hand) {
        Matrix4f pose = new Matrix4f(raw);
        float damp = hand ? .9f : .5f;
        pose.m30(pose.m30() - body.m30() * damp).m31(pose.m31() - body.m31() * damp).m32(pose.m32() - body.m32() * damp);
        Matrix4f m = new Matrix4f().rotateY((float) Math.PI).scale(left ? -scale : scale, scale, scale).mul(pose);
        m.translate(part.position()).rotateXYZ((float) Math.toRadians(part.rotation().x), (float) Math.toRadians(part.rotation().y),
                (float) Math.toRadians(part.rotation().z)).scale(part.size());
        if (part.type() == ModelPart.Type.BLOCK) m.translate(-.5f, -.5f, -.5f);
        return m;
    }
}
