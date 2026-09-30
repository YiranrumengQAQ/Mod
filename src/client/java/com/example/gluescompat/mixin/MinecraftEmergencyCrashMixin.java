package com.example.gluescompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.gluescompat.GluesCompatState;

import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;

/**
 * 最后一道闸门：在 Mojang 26.3 中 emergencySaveAndCrash 存在两个重载：
 * <ul>
 *   <li>{@code emergencySaveAndCrash(CrashReport)} —— 资源包/渲染崩溃路径；</li>
 *   <li>{@code emergencySaveAndCrash(Throwable)}   —— 致命异常路径。</li>
 * </ul>
 * triggerResourcePackRecovery() 则是 tryTriggerRecovery 内部调用的恢复入口。
 *
 * <p>这里只拦截 {@code (CrashReport)} 重载（即“后处理/着色器崩溃”路径），
 * 以及 {@code triggerResourcePackRecovery()}。<b>不</b>拦截 {@code (Throwable)}
 * 重载，以保证真正的 NPE/OOM/存档损坏等致命错误仍会按原版流程保存并退出，
 * 不会把真正的 bug 吞掉导致无限循环或损坏存档。</p>
 *
 * <p>通过检查当前 CrashReport 的异常链 / 错误类别来判断是否为已知的
 * 着色器/后处理失败，避免误伤其他真正崩溃。为了简洁可靠，这里使用
 * Thread 栈 + 触发频率判断：如果调用来自 ShaderManager.tryTriggerRecovery，
 * 就直接取消；否则保留原版崩溃流程。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEmergencyCrashMixin {

	/**
	 * 拦截 {@code Minecraft.emergencySaveAndCrash(CrashReport)}——这是渲染/着色器/后处理
	 * 崩溃的最终出口。我们判断抛出的是不是 PostChain/ShaderManager 的
	 * CompilationException，是则取消闪退并打日志，否则让原版流程继续。
	 */
	@Inject(method = "emergencySaveAndCrash(Lnet/minecraft/CrashReport;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowPostChainCrash(CrashReport report, CallbackInfo ci) {
		if (report == null) return;
		String details = report.getException() != null ? String.valueOf(report.getException()) : "";
		Throwable ex = report.getException();
		if (isKnownShaderPostChainFailure(ex, details)) {
			GluesCompatState.LOGGER.error(
					"[GluesCompat] 已阻止已知的着色器/后处理崩溃（emergencySaveAndCrash）。游戏继续运行，对应视觉效果可能缺失。异常摘要：{}",
					ex == null ? details : ex.getClass().getSimpleName() + ": " + ex.getMessage());
			ci.cancel();
		}
	}

	@Inject(method = "triggerResourcePackRecovery()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void gluescompat$swallowRecovery(CallbackInfo ci) {
		// 同样先判断调用源：只有在确实是后处理失败触发的恢复才取消
		if (calledFromPostChainFailure()) {
			GluesCompatState.LOGGER.warn("[GluesCompat] 已跳过后处理失败的资源包恢复（避免恢复失败→闪退循环）。");
			ci.cancel();
		}
	}

	private static boolean isKnownShaderPostChainFailure(Throwable t, String fallback) {
		if (t == null) {
			// 通过堆栈/消息字符串判断
			String s = fallback == null ? "" : fallback;
			return s.contains("post processing pipeline")
				|| s.contains("Failed to compile post processing")
				|| s.contains("blur");
		}
		// 沿着异常链检查
		Throwable cur = t;
		int depth = 0;
		while (cur != null && depth < 10) {
			String cn = cur.getClass().getName();
			String msg = cur.getMessage();
			if (cn.contains("ShaderManager$CompilationException")) return true;
			if (cn.contains("PostChain") && msg != null && msg.contains("blur")) return true;
			if (msg != null && msg.contains("post processing pipeline")) return true;
			cur = cur.getCause();
			depth++;
		}
		return false;
	}

	private static boolean calledFromPostChainFailure() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		for (StackTraceElement e : stack) {
			String cn = e.getClassName();
			String mn = e.getMethodName();
			// 调用栈中包含 PostChain 或 processBlurEffect → 后处理失败
			if (cn.contains("PostChain") || "processBlurEffect".equals(mn)) return true;
			if (cn.contains("ShaderManager") && "getPostChain".equals(mn)) return true;
		}
		return false;
	}
}
