package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.gluescompat.GluesCompatState;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 后处理链兜底（双保险）：
 *
 * <p>1) {@code getPostChain(ResourceLocation)} 的 HEAD：
 *    对于 id 路径包含 "blur" 的后处理链（即 {@code minecraft:blur} 及其变体），
 *    直接返回 {@code null}，从源头阻止 box_blur / kawase_blur 高斯模糊着色器
 *    在 MobileGlues/ANGLE 的 OpenGL ES 驱动上编译。
 *
 * <p>2) {@code tryTriggerRecovery()} 的 HEAD：
 *    原版在 post chain 编译失败时会调用此方法做资源包恢复，恢复失败就调
 *    {@code Minecraft.emergencySaveAndCrash} 直接崩游戏。这里直接取消该方法，
 *    吞掉后处理链失败的崩溃路径——代价是后处理效果可能缺失，但游戏不会闪退。
 *
 * <p>此 Mixin 与 {@link GameRendererBlurMixin} 互为补充：前者在调用方取消模糊，
 * 后者在 ShaderManager 层返回 null 并阻断崩溃恢复，两者任意一条生效都能解决
 * 主菜单/子界面 GUI 的模糊后处理闪退。</p>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerPostChainMixin {

	@Inject(method = "getPostChain", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$skipBlurPostChain(ResourceLocation id, CallbackInfoReturnable<PostChain> cir) {
		if (id == null) return;
		String path = id.getPath();
		if (path != null && path.contains("blur")) {
			GluesCompatState.LOGGER.info(
					"[GluesCompat] 已跳过后处理链 {}（模糊着色器在当前驱动上无法编译，游戏继续运行）。", id);
			cir.setReturnValue(null);
		}
	}

	/**
	 * 拦截 {@code ShaderManager.tryTriggerRecovery}：原版在 post chain / shader
	 * 编译失败时会走资源包恢复流程，恢复不过来就 emergencySaveAndCrash 闪退。
	 * 在 MobileGlues 兼容模式下直接取消，让游戏继续运行（缺少对应后处理效果）。
	 */
	@Inject(method = "tryTriggerRecovery", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowPostChainRecovery(CallbackInfo ci) {
		GluesCompatState.LOGGER.warn(
				"[GluesCompat] 已吞掉后处理/着色器失败的资源包恢复流程（避免 emergencySaveAndCrash 闪退）。");
		ci.cancel();
	}
}
