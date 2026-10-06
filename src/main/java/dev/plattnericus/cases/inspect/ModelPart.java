package dev.plattnericus.cases.inspect;

import org.joml.Vector3f;

/**
 * One display entity of a knife model. {@code material} is a vanilla material name or a
 * placeholder ($primary, $secondary, $accent, $metal, $dark, $handle, $edge, $item).
 */
public record ModelPart(String id, Type type, String material, Vector3f position, Vector3f size, Vector3f rotation,
                        String group, boolean glow) {

    public enum Type { BLOCK, ITEM }
}
