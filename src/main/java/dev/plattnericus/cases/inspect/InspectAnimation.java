package dev.plattnericus.cases.inspect;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Keyframed animation. Each group has keyframes whose {@code ticks} is the time to reach that pose
 * from the previous one; omitted values keep the previous pose. {@link #compile()} turns this into
 * sample times: at each sample every display receives its next target pose together with an
 * interpolation duration, and the client interpolates in between. Easing and rotations larger than
 * 90° are split into additional samples so client side interpolation stays smooth and never takes
 * the short way around.
 */
public final class InspectAnimation {

    public enum Ease { LINEAR, IN, OUT, INOUT }

    public record Keyframe(int ticks, Vector3f move, Vector3f rotate, Vector3f scale, Ease ease) {
    }

    public record Group(String id, String parent, Vector3f pivot, List<Keyframe> keyframes) {
    }

    private record Segment(int start, int end, Pose from, Pose to, Ease ease) {
    }

    private final String id;
    private final Map<String, Group> groups;
    private final Map<Integer, String> sounds;
    private final int substeps;
    private final Map<String, List<Segment>> segments = new HashMap<>();
    private final List<Integer> samples = new ArrayList<>();
    private int duration;

    public InspectAnimation(String id, Map<String, Group> groups, Map<Integer, String> sounds, int substeps) {
        this.id = id;
        this.groups = Map.copyOf(groups);
        this.sounds = Map.copyOf(sounds);
        this.substeps = Math.max(1, substeps);
        compile();
    }

    public String id() {
        return id;
    }

    public int duration() {
        return duration;
    }

    public List<Integer> samples() {
        return samples;
    }

    public Map<Integer, String> sounds() {
        return sounds;
    }

    public boolean hasGroup(String group) {
        return groups.containsKey(group);
    }

    private void compile() {
        TreeSet<Integer> times = new TreeSet<>();
        times.add(0);
        for (Group g : groups.values()) {
            List<Segment> list = new ArrayList<>();
            Pose current = Pose.identity();
            int t = 0;
            boolean first = true;
            for (Keyframe k : g.keyframes()) {
                Pose next = new Pose(
                        k.move() == null ? new Vector3f(current.move()) : new Vector3f(k.move()),
                        k.rotate() == null ? new Vector3f(current.rotate()) : new Vector3f(k.rotate()),
                        k.scale() == null ? new Vector3f(current.scale()) : new Vector3f(k.scale()));
                if (first && k.ticks() == 0) {
                    current = next;
                    first = false;
                    continue;
                }
                first = false;
                int end = t + Math.max(1, k.ticks());
                list.add(new Segment(t, end, current, next, k.ease()));
                // Also sample inherited motion: sparse child keyframes otherwise interpolate a
                // compound rotation along the shortest quaternion path and visibly cut a spin.
                int splits = Math.min(end - t, Math.max((end - t + 1) / 2,
                        Math.max(k.ease() == Ease.LINEAR ? 1 : substeps, (int) Math.ceil(current.maxAngleDelta(next) / 45f))));
                for (int s = 0; s <= splits; s++) {
                    times.add(t + Math.round((end - t) * (s / (float) splits)));
                }
                current = next;
                t = end;
            }
            if (list.isEmpty()) {
                list.add(new Segment(0, 1, current, current, Ease.LINEAR));
            }
            segments.put(g.id(), list);
            duration = Math.max(duration, t);
        }
        times.add(duration);
        samples.addAll(times);
    }

    /** Pose of a group at time {@code t} (ticks). */
    Pose pose(String group, int t) {
        List<Segment> list = segments.get(group);
        if (list == null) {
            return Pose.identity();
        }
        Segment last = list.getLast();
        if (t <= list.getFirst().start()) {
            return list.getFirst().from();
        }
        if (t >= last.end()) {
            return last.to();
        }
        for (Segment s : list) {
            if (t >= s.start() && t <= s.end()) {
                float u = (t - s.start()) / (float) (s.end() - s.start());
                return Pose.lerp(s.from(), s.to(), ease(s.ease(), u));
            }
        }
        return last.to();
    }

    /** World-independent matrix of a group (including its parents) at time {@code t}. */
    public Matrix4f groupMatrix(String group, int t) {
        Group g = groups.get(group);
        if (g == null) {
            return new Matrix4f();
        }
        Matrix4f parent = g.parent() == null || g.parent().equals(group) ? new Matrix4f() : groupMatrix(g.parent(), t);
        Pose p = pose(group, t);
        Vector3f pivot = g.pivot();
        return parent.translate(p.move())
                .translate(pivot)
                .rotateXYZ((float) Math.toRadians(p.rotate().x), (float) Math.toRadians(p.rotate().y), (float) Math.toRadians(p.rotate().z))
                .scale(p.scale())
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static float ease(Ease ease, float u) {
        return switch (ease) {
            case LINEAR -> u;
            case IN -> u * u * u;
            case OUT -> 1 - (float) Math.pow(1 - u, 3);
            case INOUT -> u * u * u * (u * (u * 6 - 15) + 10);
        };
    }
}
