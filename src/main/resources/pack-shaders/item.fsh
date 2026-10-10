#version 330
#extension GL_ARB_separate_shader_objects : require

#ifdef GLINT
#include <minecraft:globals.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 7) in vec3 metalNormal;
layout(location = 8) in vec3 metalPosition;
layout(location = 9) flat in float metalSurface;

vec3 metalSheen(vec3 base) {
    vec3 normal = normalize(metalNormal);
    vec3 view = normalize(-metalPosition);
    if (dot(normal, view) < 0.0) normal = -normal;
    vec3 reflected = reflect(-view, normal);
    // A restrained environment approximation, not a scene reflection or a global screen filter.
    vec3 environment = mix(vec3(0.19, 0.17, 0.15), vec3(0.63, 0.75, 0.88),
                           smoothstep(-0.35, 0.65, reflected.y));
    float fresnel = pow(1.0 - clamp(dot(normal, view), 0.0, 1.0), 5.0);
    vec3 halfVector = normalize(view + normalize(vec3(-0.35, 0.75, 0.55)));
    float highlight = pow(max(dot(normal, halfVector), 0.0), 42.0);
    return mix(base, base * environment * 1.35, 0.08 + 0.12 * fresnel)
           + vec3(0.12) * highlight;
}
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    color *= vertexColor * ColorModulator;
    #ifndef OIT_ALPHA_ONLY
    if (metalSurface > 0.5) color.rgb = metalSheen(color.rgb);
    #endif

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
