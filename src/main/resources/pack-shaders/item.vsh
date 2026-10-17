#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 7) out vec3 metalNormal;
layout(location = 8) out vec3 metalPosition;
layout(location = 9) flat out float metalSurface;
#endif
// MCCases inspect scenes: 1 shows only near the camera (first person), 2 only away from it (F5).
layout(location = 10) flat out int mccasesScene;
layout(location = 11) out vec3 mccasesScenePosition;

// The exporter marks MCCases faces with reserved near-white tints, told apart by their colour ratios,
// which survive vanilla face shading. Unrelated item colours keep their original path.
const vec3 MCCASES_METAL = vec3(254.0, 253.0, 252.0);
const vec3 MCCASES_NEAR = vec3(254.0, 252.0, 253.0);
const vec3 MCCASES_FAR = vec3(253.0, 254.0, 252.0);
const vec3 MCCASES_NEAR_METAL = vec3(252.0, 253.0, 254.0);
const vec3 MCCASES_FAR_METAL = vec3(253.0, 252.0, 254.0);

bool mccases_marked(vec4 color, vec3 mark) {
    float red = max(color.r, 0.00001);
    return abs(color.g / red - mark.g / mark.r) < 0.0007 && abs(color.b / red - mark.b / mark.r) < 0.0007;
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vec3 mark = vec3(0.0);
    bool metal = false;
    int scene = 0;
    if (mccases_marked(Color, MCCASES_METAL)) { mark = MCCASES_METAL; metal = true; }
    else if (mccases_marked(Color, MCCASES_NEAR)) { mark = MCCASES_NEAR; scene = 1; }
    else if (mccases_marked(Color, MCCASES_FAR)) { mark = MCCASES_FAR; scene = 2; }
    else if (mccases_marked(Color, MCCASES_NEAR_METAL)) { mark = MCCASES_NEAR_METAL; metal = true; scene = 1; }
    else if (mccases_marked(Color, MCCASES_FAR_METAL)) { mark = MCCASES_FAR_METAL; metal = true; scene = 2; }
    vec4 baseColor = Color;
    if (mark.r > 0.0) baseColor.rgb /= mark / 255.0;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, baseColor);
    // The fragment stage cuts the scene at the split distance, so an item crossing it is cut cleanly.
    mccasesScene = scene;
    mccasesScenePosition = Position;
    #ifndef OIT_ALPHA_ONLY
    metalSurface = metal ? 1.0 : 0.0;
    metalNormal = mat3(ModelViewMat) * Normal;
    metalPosition = (ModelViewMat * vec4(Position, 1.0)).xyz;
    #endif
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif
}
