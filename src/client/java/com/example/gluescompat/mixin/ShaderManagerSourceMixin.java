package com.example.gluescompat.mixin;

import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.example.gluescompat.GluesCompatShaders;
import com.example.gluescompat.GluesCompatState;
import com.google.common.collect.ImmutableMap;
import com.mojang.renderpearl.api.pipeline.ShaderType;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * 着色器降级：在 {@code ShaderManager.loadShader} 读取着色器源码时，
 * 把 {@code core/terrain} 的顶点/片元源码替换为 MobileGlues 可编译的保守版本。
 *
 * <p>26.3 中 12 条“必需地形管线”（含 multidraw 与 OIT 变体）共用这两个着色器文件，
 * 因此替换这两个文件即可同时修复全部 12 条管线，无需逐个重定向管线 ID。</p>
 *
 * <p>兼容模式关闭（普通桌面环境）时直接返回原版源码，零影响。</p>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerSourceMixin {
	@Redirect(
			method = "loadShader",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;"
			),
			require = 0
	)
	private static String gluescompat$replaceTerrainShaderSource(
			Resource resource,
			Identifier location,
			Resource resourceArg,
			ShaderType type,
			ImmutableMap.Builder<?, ?> output
	) throws IOException {
		if (GluesCompatState.isEnabled()) {
			String replacement = GluesCompatShaders.replacementFor(location);
			if (replacement != null) {
				GluesCompatState.LOGGER.info("[GluesCompat] 已替换地形着色器源码：{}", location);
				return replacement;
			}
		}

		return resource.readAllAsString();
	}
}
