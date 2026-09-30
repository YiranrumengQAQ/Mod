package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

/**
 * 在 {@link GameRenderer#processBlurEffect(float)} 的 HEAD 处直接取消，
 * 跳过 minecraft:blur 高斯模糊后处理链的加载与渲染。
 *
 * <p>MobileGlues/ANGLE + OpenGL ES 转译层不支持 box_blur/kawase_blur
 * 所使用的 GLSL 语法，该后处理编译失败会沿着 getPostChain →
 * tryTriggerRecovery → emergencySaveAndCrash 直接崩溃。</p>
 *
 * <p>这里是“调用方取消”这一层保险：只要 Mixin 命中，processBlurEffect
 * 一进入就立即返回，完全不会走到 ShaderManager.getPostChain。
 * 若本 Mixin 因映射版本差异未命中，ShaderManagerPostChainMixin 与
 * MinecraftEmergencyCrashMixin 会在更下游兜底。</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {

	/**
	 * 匹配 {@code processBlurEffect(float partialTick)}。
	 * desc 写完整以避免同名重载/签名变化导致静默匹配失败。
	 */
	@Inject(method = "processBlurEffect(F)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$disableBlurPostEffect(float partialTick, CallbackInfo ci) {
		ci.cancel();
	}
}
