package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.example.gluescompat.GluesCompatGuard;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;

/**
 * 渲染期兜底：原版 {@code RenderSystem.getCompiledPipeline} 在管线缺失时直接抛
 * {@code IllegalStateException("Failed to find or load pipeline ...")}，
 * 会在进入世界渲染地形时把游戏打崩。
 *
 * <p>覆写为：缺失时改走 {@link GluesCompatGuard#emergencyFallbackPipeline}。</p>
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemPipelineMixin {
	/**
	 * @author GluesCompat
	 * @reason 管线缺失时返回应急管线而不是崩溃
	 */
	@Overwrite
	public static CompiledRenderPipeline getCompiledPipeline(RenderPipeline pipeline) {
		CompiledRenderPipeline compiledPipeline = RenderSystem.getCompiledPipelineNullable(pipeline);
		if (compiledPipeline != null) {
			return compiledPipeline;
		}

		return GluesCompatGuard.emergencyFallbackPipeline(pipeline);
	}
}
