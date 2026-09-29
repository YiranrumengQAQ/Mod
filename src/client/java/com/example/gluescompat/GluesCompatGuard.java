package com.example.gluescompat;

import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;

/**
 * 自愈闭环的最后一道闸门。
 *
 * <p>当某条渲染管线在驱动上实在编译不出来时：
 * <ol>
 *   <li>{@code ShaderManager.apply} 的崩溃判定被 Mixin 拦截（不再抛
 *       “Failed to load required shader programs”）；</li>
 *   <li>渲染阶段查询缺失管线时，本类返回一个“应急管线”（任意已成功编译的管线，
 *       通常是 GUI 管线），避免 {@code IllegalStateException} 把游戏打崩。</li>
 * </ol>
 * 代价是缺失管线对应的内容（最坏情况：地形）可能显示异常，但游戏能启动、能进存档。</p>
 */
public final class GluesCompatGuard {
	private static final Set<String> swallowedFailures = new LinkedHashSet<>();
	private static CompiledRenderPipeline emergencyPipeline;
	private static boolean emergencyLogged;

	private GluesCompatGuard() {
	}

	/**
	 * 由 ShaderManager.apply 的 Mixin 调用：记录被吞掉的“必需管线”失败。
	 */
	public static synchronized void noteSwallowedRequiredFailure(Object pipelineLocation) {
		String id = String.valueOf(pipelineLocation);
		if (swallowedFailures.add(id)) {
			GluesCompatState.LOGGER.error(
					"[GluesCompat] 必需渲染管线编译失败，已被拦截（游戏不会因此崩溃）：{}", id);
			if (swallowedFailures.size() == 1) {
				GluesCompatState.LOGGER.error(
						"[GluesCompat] 当前驱动无法编译部分原版着色器。已自动降级：成功编译的管线照常使用，失败的管线将被跳过。");
			}
		}
	}

	/**
	 * 渲染期兜底：管线确实缺失时，返回一个可用的应急管线；实在没有任何管线可用才抛异常。
	 */
	public static CompiledRenderPipeline emergencyFallbackPipeline(RenderPipeline requested) {
		CompiledRenderPipeline fallback = emergencyPipeline;
		if (fallback == null) {
			fallback = findAnyCompiledPipeline();
			emergencyPipeline = fallback;
		}

		if (fallback != null) {
			if (!emergencyLogged) {
				emergencyLogged = true;
				GluesCompatState.LOGGER.warn(
						"[GluesCompat] 管线 {} 不可用，已用应急管线顶替渲染（画面可能异常，但不会崩溃）。",
						requested.getLocation());
			}

			return fallback;
		}

		throw new IllegalStateException("Failed to find or load pipeline " + requested.getLocation());
	}

	private static CompiledRenderPipeline findAnyCompiledPipeline() {
		try {
			CompiledRenderPipeline gui = RenderSystem.getCompiledPipelineNullable(RenderPipelines.GUI);
			if (gui != null) {
				return gui;
			}

			CompiledRenderPipeline guiTextured = RenderSystem.getCompiledPipelineNullable(RenderPipelines.GUI_TEXTURED);
			if (guiTextured != null) {
				return guiTextured;
			}

			return RenderSystem.getCompiledPipelineNullable(RenderPipelines.SOLID_BLOCK);
		} catch (Throwable t) {
			GluesCompatState.LOGGER.warn("[GluesCompat] 寻找应急管线失败", t);
			return null;
		}
	}
}
