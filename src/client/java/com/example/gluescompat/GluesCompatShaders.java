package com.example.gluescompat;

import net.minecraft.resources.Identifier;

/**
 * 26.3 的地形渲染管线（solid/cutout/translucent terrain、它们的 multidraw 变体，
 * 以及全部 OIT 地形管线，共 12 条“必需管线”）全部使用 {@code core/terrain} 着色器。
 *
 * <p>原版地形着色器里的 RGSS/最近邻采样（{@code texture_sampling.glsl}：
 * {@code textureGrad}、{@code textureLod}、{@code dFdx/dFdy}、常量数组 + 循环）
 * 以及一些隐式整型/浮点混算，在 MobileGlues 的 GLSL 转译器下无法编译，
 * 导致 {@code ShaderManager} 抛出 “Failed to load required shader programs” 并崩溃。</p>
 *
 * <p>这里提供结构完全等价、但语法最保守的替换版本：
 * 保留全部 UBO / 采样器 / 顶点输入输出布局（反射契约不变），
 * 只把采样换成普通 {@code texture()}。实体 / 方块 / OIT 附属管线用的着色器
 * 已证明能在 MobileGlues 上编译，因此替换后的地形着色器与它们同构，可以正常编译。</p>
 */
public final class GluesCompatShaders {
	private GluesCompatShaders() {
	}

	public static String replacementFor(Identifier location) {
		String path = location.getPath();
		if (path.equals("shaders/core/terrain.vsh")) {
			return TERRAIN_VSH;
		}
		if (path.equals("shaders/core/terrain.fsh")) {
			return TERRAIN_FSH;
		}

		return null;
	}

	private static final String TERRAIN_VSH = """
			#version 330
			#extension GL_ARB_separate_shader_objects : require

			#include <minecraft:fog.glsl>
			#include <minecraft:globals.glsl>
			#include <minecraft:projection.glsl>
			#include <minecraft:sample_lightmap.glsl>
			#include <minecraft:terrainglobals.glsl>
			#ifndef MULTIDRAW_TERRAIN
			    #include <minecraft:chunksection.glsl>
			#endif

			layout(location = 0) in vec3 Position;
			layout(location = 1) in vec4 Color;
			layout(location = 2) in vec2 UV0;
			layout(location = 3) in ivec2 UV2;
			#ifdef MULTIDRAW_TERRAIN
			layout(location = 4) in ivec3 ChunkPosition;
			layout(location = 5) in float ChunkVisibility;
			#endif

			#ifndef OIT_ALPHA_ONLY
			uniform sampler2D Sampler2;
			#endif

			layout(location = 0) out float sphericalVertexDistance;
			layout(location = 1) out float cylindricalVertexDistance;
			layout(location = 2) out vec4 vertexColor;
			layout(location = 3) out vec2 texCoord0;
			layout(location = 4) out float chunkVisibility;

			void main() {
			    vec3 pos = Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
			    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

			    sphericalVertexDistance = fog_spherical_distance(pos);
			    cylindricalVertexDistance = fog_cylindrical_distance(pos);
			    #ifndef OIT_ALPHA_ONLY
			    vertexColor = Color * sample_lightmap(Sampler2, UV2);
			    #else
			    vertexColor = Color;
			    #endif
			    texCoord0 = UV0;

			    float dist = length(pos);
			    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - 16.0) / 16.0, 0.0, 1.0));
			}
			""";

	private static final String TERRAIN_FSH = """
			#version 330
			#extension GL_ARB_separate_shader_objects : require

			#include <minecraft:fog.glsl>
			#include <minecraft:globals.glsl>
			#include <minecraft:oit.glsl>
			#include <minecraft:terrainglobals.glsl>
			#ifndef MULTIDRAW_TERRAIN
			    #include <minecraft:chunksection.glsl>
			#endif

			uniform sampler2D Sampler0;

			layout(location = 0) in float sphericalVertexDistance;
			layout(location = 1) in float cylindricalVertexDistance;
			layout(location = 2) in vec4 vertexColor;
			layout(location = 3) in vec2 texCoord0;
			layout(location = 4) in float chunkVisibility;

			#ifndef OIT_ALPHA_ONLY
			layout(location = 0) out vec4 fragColor;
			#endif

			vec4 calculateFinalColor(vec4 color) {
			    #ifdef OIT_ACCUMULATE
			    color = sampleColorForAccumulation(color);
			    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
			    #else
			    vec4 fogColor = FogColor;
			    #endif
			    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
			}

			void main() {
			    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
			    #ifndef OIT_ALPHA_ONLY
			    color = mix(FogColor * vec4(1.0, 1.0, 1.0, color.a), color, chunkVisibility);
			    #endif
			    #ifdef ALPHA_CUTOUT
			    if (color.a < ALPHA_CUTOUT) {
			        discard;
			    }
			    #endif

			    #ifdef OIT_ALPHA_ONLY
			    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
			    #else
			    fragColor = calculateFinalColor(color);
			    #endif
			}
			""";
}
