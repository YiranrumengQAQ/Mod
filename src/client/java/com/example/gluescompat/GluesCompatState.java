package com.example.gluescompat;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 兼容模式总开关。
 *
 * <p>自动检测当前是否运行在 MobileGlues / PojavLauncher / FCL（Fold Craft Launcher）
 * 一类的 OpenGL ES 转译环境中。只有检测到受限 GL 环境时，才会启用地形着色器降级，
 * 因此普通桌面玩家安装本模组不会受到任何影响。</p>
 *
 * <p>可通过启动参数强制指定：{@code -Dgluescompat.mode=on|off|auto}（默认 auto）。</p>
 */
public final class GluesCompatState {
	public static final Logger LOGGER = LoggerFactory.getLogger("gluescompat");

	private static volatile Boolean cached;
	private static volatile String reason = "";

	private GluesCompatState() {
	}

	public static boolean isEnabled() {
		Boolean value = cached;
		if (value == null) {
			synchronized (GluesCompatState.class) {
				value = cached;
				if (value == null) {
					value = detect();
					cached = value;
				}
			}
		}

		return value;
	}

	public static String reason() {
		isEnabled();
		return reason;
	}

	private static boolean detect() {
		// 1. 手动开关
		String mode = System.getProperty("gluescompat.mode", System.getProperty("gluescompat", "auto"))
				.trim().toLowerCase(Locale.ROOT);
		if (mode.equals("on") || mode.equals("force") || mode.equals("true") || mode.equals("enabled")) {
			reason = "通过 -Dgluescompat.mode=on 手动启用";
			return true;
		}
		if (mode.equals("off") || mode.equals("false") || mode.equals("disabled")) {
			reason = "通过 -Dgluescompat.mode=off 手动关闭";
			return false;
		}

		// 2. MobileGlues 特征：FCL / PojavLauncher 会把 GL 库指向 libmobileglues.so
		if (containsAny(System.getProperty("org.lwjgl.opengl.libname"), "mobileglues")
				|| containsAny(System.getProperty("org.lwjgl.egl.libname"), "mobileglues")) {
			reason = "检测到 MobileGlues（org.lwjgl.opengl.libname / org.lwjgl.egl.libname）";
			return true;
		}
		if (containsAny(System.getenv("LIBGL_EGL"), "mobileglues")
				|| containsAny(System.getenv("SDL_OPENGL_LIBRARY"), "mobileglues")
				|| containsAny(System.getenv("SDL_EGL_LIBRARY"), "mobileglues")
				|| containsAny(System.getenv("POJAVEXEC_EGL"), "mobileglues")) {
			reason = "检测到 MobileGlues（环境变量指向 libmobileglues.so）";
			return true;
		}

		// 3. PojavLauncher / FCL 家族的 GLES 渲染器
		String pojavRenderer = System.getenv("POJAV_RENDERER");
		if (pojavRenderer != null && !pojavRenderer.isEmpty()) {
			reason = "检测到 PojavLauncher/FCL 渲染器（POJAV_RENDERER=" + pojavRenderer + "）";
			return true;
		}
		if (System.getenv("FCL_VERSION_CODE") != null) {
			reason = "检测到 FCL 启动器（FCL_VERSION_CODE）";
			return true;
		}

		// 4. 启动器品牌
		String brand = System.getProperty("minecraft.launcher.brand", "");
		if (containsAny(brand, "fold craft", "pojav")) {
			reason = "检测到启动器品牌：" + brand;
			return true;
		}

		// 5. 通用 GLES 转译层标记
		if ("3".equals(System.getenv("LIBGL_ES"))) {
			reason = "检测到 LIBGL_ES=3（OpenGL ES 转译层）";
			return true;
		}

		reason = "未检测到受限 GL 环境，保持原版着色器";
		return false;
	}

	private static boolean containsAny(String value, String... needles) {
		if (value == null || value.isEmpty()) {
			return false;
		}

		String lowered = value.toLowerCase(Locale.ROOT);
		for (String needle : needles) {
			if (lowered.contains(needle)) {
				return true;
			}
		}

		return false;
	}
}
