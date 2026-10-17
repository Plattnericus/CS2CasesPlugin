// MCCases skin arms.
//
// Item models cannot reference a player's skin texture directly. The pack therefore renders
// Minecraft's player-head special model, whose skin comes from the item's profile component,
// stretched into a forearm-shaped box. Vanilla box UVs always have one texel per model pixel on
// every face, so real heads, bodies and mobs keep a texel aspect close to 1. The MCCases arm boxes
// are deliberately anisotropic. This fragment step measures that aspect from screen-space
// derivatives and, only for the reserved signatures, samples the matching arm/sleeve faces of the
// same skin instead of the head faces. Mirrored boxes are left arms.
//
// Signatures (x, y, z scale of the 8x8x8 head before any pose):
//   wide arm  0.500, 1.500, 0.575  -> front 3.00, side 2.61, end 1.15
//   slim arm  0.375, 1.750, 0.500  -> front 4.67, side 3.50, end 1.33
// The view model compensates bow and crossbow use transforms halfway through the draw, so the
// remaining vanilla stretch stays within 10%. The bands below allow for that.

const vec4 MCCASES_HEAD_RECTS[6] = vec4[6](
    vec4(8.0, 0.0, 8.0, 8.0),   // top of the box (DOWN polygon)
    vec4(16.0, 0.0, 8.0, 8.0),  // bottom of the box (UP polygon)
    vec4(0.0, 8.0, 8.0, 8.0),   // west
    vec4(8.0, 8.0, 8.0, 8.0),   // north
    vec4(16.0, 8.0, 8.0, 8.0),  // east
    vec4(24.0, 8.0, 8.0, 8.0)   // south
);

int mccases_head_face(vec2 local) {
    for (int i = 0; i < 6; i++) {
        vec4 r = MCCASES_HEAD_RECTS[i];
        if (local.x >= r.x && local.x < r.x + r.z && local.y >= r.y && local.y < r.y + r.w) return i;
    }
    return -1;
}

// Box UV rectangle of one face, matching ModelPart.Cube for a box at (u, v) with size (w, h, d).
vec4 mccases_box_rect(int face, vec2 offset, float w, float h, float d) {
    if (face == 0) return vec4(offset.x + d, offset.y, w, d);
    if (face == 1) return vec4(offset.x + d + w, offset.y, w, d);
    if (face == 2) return vec4(offset.x, offset.y + d, d, h);
    if (face == 3) return vec4(offset.x + d, offset.y + d, w, h);
    if (face == 4) return vec4(offset.x + d + w, offset.y + d, d, h);
    return vec4(offset.x + d + d + w, offset.y + d, w, h);
}

// Returns 0 for ordinary fragments, 1 for a remapped arm fragment and 2 for an unmeasurable
// arm-candidate sliver that should be discarded. uv receives the arm or sleeve texture coordinate.
int mccases_skin_arm(inout vec2 uv, vec3 position, vec3 normal, sampler2D skin) {
    vec2 size = vec2(textureSize(skin, 0));
    vec2 texel = uv * size;
    vec3 px = dFdx(position);
    vec3 py = dFdy(position);
    vec2 tx = dFdx(texel);
    vec2 ty = dFdy(texel);
    if (size != vec2(64.0) || texel.y >= 16.0 || dot(position, position) > 36.0) return 0;
    bool sleeve = texel.x >= 32.0;
    int face = mccases_head_face(vec2(sleeve ? texel.x - 32.0 : texel.x, texel.y));
    if (face < 0) return 0;

    float det = tx.x * ty.y - tx.y * ty.x;
    float reliability = abs(det) / max(length(tx) * length(ty), 1e-12);
    if (reliability < 0.03) return dot(position, position) < 4.0 ? 2 : 0;
    vec3 pu = (px * ty.y - py * tx.y) / det;
    vec3 pv = (py * tx.x - px * ty.x) / det;
    float ratio = length(pv) / max(length(pu), 1e-12);

    bool slim;
    if (face <= 1) {
        if (ratio >= 1.08 && ratio < 1.24) slim = false;
        else if (ratio >= 1.24 && ratio < 1.45) slim = true;
        else return 0;
    } else if (face == 2 || face == 4) {
        if (ratio >= 2.30 && ratio < 3.00) slim = false;
        else if (ratio >= 3.00 && ratio < 3.95) slim = true;
        else return 0;
    } else {
        if (ratio >= 2.62 && ratio < 3.80) slim = false;
        else if (ratio >= 3.80 && ratio < 5.30) slim = true;
        else return 0;
    }

    // ModelPart faces wind their UVs the same way on every box, except the UP polygon.
    float handedness = dot(cross(pu, pv), normal);
    bool mirrored = face == 1 ? handedness < 0.0 : handedness > 0.0;
    vec4 head = MCCASES_HEAD_RECTS[face];
    vec2 relative = (vec2(sleeve ? texel.x - 32.0 : texel.x, texel.y) - head.xy) / head.zw;
    int target = face;
    if (mirrored) {
        relative.x = 1.0 - relative.x;
        if (face == 2) target = 4;
        else if (face == 4) target = 2;
    }
    vec2 offset = mirrored ? (sleeve ? vec2(48.0, 48.0) : vec2(32.0, 48.0))
                           : (sleeve ? vec2(40.0, 32.0) : vec2(40.0, 16.0));
    vec4 rect = mccases_box_rect(target, offset, slim ? 3.0 : 4.0, 12.0, 4.0);
    vec2 arm = rect.xy + clamp(relative, 0.0, 0.9999) * rect.zw;
    uv = (floor(arm) + 0.5) / size;
    // Skin arms only exist in first person; in third person the player model shows the real arms.
    if (dot(position, position) > 5.76) return 2;
    return 1;
}
