package com.example.gluescompat;

import net.fabricmc.api.ClientModInitializer;

public class GluesCompatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (GluesCompatState.isEnabled()) {
			GluesCompatState.LOGGER.info("[GluesCompat] 兼容模式已启用：{}", GluesCompatState.reason());
			GluesCompatState.LOGGER.info(
					"[GluesCompat] 地形着色器已降级为 MobileGlues/OpenGL ES 转译层可编译的版本，并启用崩溃自愈拦截。");
			GluesCompatState.LOGGER.info(
					"[GluesCompat] GUI 菜单背景模糊后处理（minecraft:blur）已禁用；所有后处理/着色器崩溃最终出口已被拦截，游戏不会因此闪退。");
		} else {
			GluesCompatState.LOGGER.info("[GluesCompat] 兼容模式未启用（{}）。仅保留崩溃自愈拦截作为安全网。",
					GluesCompatState.reason());
		}
	}
}
