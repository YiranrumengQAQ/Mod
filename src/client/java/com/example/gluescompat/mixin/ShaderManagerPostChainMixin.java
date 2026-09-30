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
 * ShaderManager 层拦截。
 *
 * <p>1) {@code getPostChain(ResourceLocation)}：对 namespace 为 "minecraft" 且
 *    path 为 "blur" 的后处理链直接返回 {@code null}，阻止 box_blur/kawase_blur
 *    着色器被加载/编译。</p>
 * <p>2) {@code tryTriggerRecovery()}：HEAD 取消，防止编译失败后进入资源包
 *    恢复 → emergencySaveAndCrash 闪退流程。</p>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerPostChainMixin {

	@Inject(
		method = "getPostChain(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/PostChain;",
		at = @At("HEAD"),
		cancellable = true,
		require = 0
	)
	private void gluescompat$skipBlurPostChain(ResourceLocation id, CallbackInfoReturnable<PostChain> cir) {
		if (id == null) return;
		String ns = id.getNamespace();
		String path = id.getPath();
		if ("minecraft".equals(ns) && "blur".equals(path)) {
			GluesCompatState.LOGGER.info("[GluesCompat] 已跳过 minecraft:blur 后处理链（着色器在当前驱动上无法编译）。");
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "tryTriggerRecovery()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowTryTriggerRecovery(CallbackInfo ci) {
		GluesCompatState.LOGGER.warn("[GluesCompat] 已拦截 ShaderManager.tryTriggerRecovery()（避免后处理/着色器编译失败触发闪退）。");
		ci.cancel();
	}
}
