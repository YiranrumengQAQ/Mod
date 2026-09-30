package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 后处理链兜底。
 *
 * <p>尝试在 {@code ShaderManager.getPostChain(ResourceLocation)} 返回 null（针对
 * 路径含 "blur" 的后处理链），并取消 {@code tryTriggerRecovery()}。
 * 由于 26.3 中这些方法的真实签名可能与我们的 stub 不完全一致（方法可能是 static、
 * 或带有额外参数），这里对同名方法的所有可匹配重载都声明注入，require = 0
 * 保证任意一条未命中都不会导致模组加载失败；最终防崩溃由
 * {@link MinecraftEmergencyCrashMixin} 在最底层兜底。</p>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerPostChainMixin {

	@Inject(method = "getPostChain", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$skipBlurPostChain(ResourceLocation id, CallbackInfoReturnable<PostChain> cir) {
		if (id == null) return;
		String path = id.getPath();
		if (path != null && path.contains("blur")) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "tryTriggerRecovery", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowTryTriggerRecovery(CallbackInfo ci) {
		ci.cancel();
	}
}
