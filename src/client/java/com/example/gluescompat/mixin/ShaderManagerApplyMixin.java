package com.example.gluescompat.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.example.gluescompat.GluesCompatGuard;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

/**
 * 崩溃拦截：{@code ShaderManager.apply} 在“必需着色器程序”编译失败时会抛出
 * {@code RuntimeException("Failed to load required shader programs: ...")} 并直接崩档。
 *
 * <p>这里把“必需管线失败”的收集动作吞掉（记入日志），让 apply 继续把
 * 已成功编译的管线装入缓存。游戏照常启动，缺失的管线由
 * {@link GluesCompatGuard#emergencyFallbackPipeline} 在渲染期兜底。</p>
 *
 * <p>两个 redirect 分别覆盖字节码中可能的 {@code List.add} / {@code ArrayList.add}
 * 调用形态，均设置 {@code require = 0}：匹配不到时不报错，静默退化为原版行为。</p>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerApplyMixin {
	@Redirect(
			method = "apply",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
					ordinal = 0
			),
			require = 0
	)
	private boolean gluescompat$swallowRequiredFailureList(List<Identifier> failedLoads, Object pipelineLocation) {
		GluesCompatGuard.noteSwallowedRequiredFailure(pipelineLocation);
		return true;
	}

	@Redirect(
			method = "apply",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z",
					ordinal = 0
			),
			require = 0
	)
	private boolean gluescompat$swallowRequiredFailureArrayList(ArrayList<Identifier> failedLoads, Object pipelineLocation) {
		GluesCompatGuard.noteSwallowedRequiredFailure(pipelineLocation);
		return true;
	}
}
