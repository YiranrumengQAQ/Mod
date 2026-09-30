package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.gluescompat.GluesCompatState;

import net.minecraft.client.Minecraft;

/**
 * 最后一道闸门：拦截 {@link Minecraft#emergencySaveAndCrash} 与
 * {@link Minecraft#triggerResourcePackRecovery}，在 MobileGlues 兼容模式下
 * 直接取消它们，阻止任何因后处理链/着色器编译失败而触发的强制闪退。
 *
 * <p>崩溃链最终必经：{@code ShaderManager.getPostChain} 抛异常 →
 * {@code tryTriggerRecovery} → {@code Minecraft.triggerResourcePackRecovery} →
 * 再次失败后调 {@code Minecraft.emergencySaveAndCrash}。拦截末尾这两个方法
 * 能覆盖所有 post-chain/shader 失败的退出路径，而不必依赖上游具体方法签名
 * （签名不匹配是 v1.0.1/v1.0.2 中 getPostChain/processBlurEffect 注入未命中的
 * 原因）。
 *
 * <p>副作用：恢复失败的着色器/后处理效果不会真正“恢复”，画面上对应效果会缺失
 * （比如 GUI 背景无模糊、部分后处理特效消失），但游戏不会闪退。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEmergencyCrashMixin {

	@Inject(method = "emergencySaveAndCrash", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowEmergencySaveAndCrash(CallbackInfo ci) {
		// 原始方法带 (CrashReport) 参数，但我们这里不引用参数以兼容任何签名
		GluesCompatState.LOGGER.error(
				"[GluesCompat] 已阻止 Minecraft 强制闪退（emergencySaveAndCrash）。游戏将继续运行，部分后处理/着色器效果可能缺失。");
		ci.cancel();
	}

	@Inject(method = "triggerResourcePackRecovery", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowResourcePackRecovery(CallbackInfo ci) {
		GluesCompatState.LOGGER.warn(
				"[GluesCompat] 已跳过资源包恢复流程（triggerResourcePackRecovery），避免进入恢复→失败→闪退的循环。");
		ci.cancel();
	}
}
