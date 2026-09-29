package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

/**
 * 拦截 {@link GameRenderer#processBlurEffect}：直接取消模糊后处理渲染。
 *
 * <p>MobileGlues/OpenGL ES 转译层不支持 minecraft:blur 后处理链
 * （box_blur / kawase_blur）所使用的 GLSL 语法，编译失败会触发
 * {@code ShaderManager.tryTriggerRecovery} 进而崩溃到主菜单。
 *
 * <p>直接 HEAD 取消是最省事且能提升帧率的做法——菜单/GUI 背景不再做高斯模糊。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {

	@Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true, require = 1)
	private void gluescompat$disableBlurPostEffect(CallbackInfo ci) {
		ci.cancel();
	}
}
