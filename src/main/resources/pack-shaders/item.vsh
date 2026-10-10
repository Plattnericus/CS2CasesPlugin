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

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    // The exporter marks only MCCases metal faces with a reserved near-white tint.
    // Ratios survive vanilla face shading; unrelated item colours keep their original path.
    float red = max(Color.r, 0.00001);
    bool marked = abs(Color.g / red - 253.0 / 254.0) < 0.0007
               && abs(Color.b / red - 252.0 / 254.0) < 0.0007;
    vec4 baseColor = Color;
    if (marked) baseColor.rgb /= vec3(254.0, 253.0, 252.0) / 255.0;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, baseColor);
    #ifndef OIT_ALPHA_ONLY
    metalSurface = marked ? 1.0 : 0.0;
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
