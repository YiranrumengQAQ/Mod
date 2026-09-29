# GluesCompat —— MobileGlues / FCL 专用 26.3 着色器兼容与自愈模组

把编译好的 `gluescompat-1.0.0.jar` 丢进 `.minecraft/mods` 文件夹，游戏直接启动，不闪退。
不需要手动降级 MC、不需要折腾驱动、不需要改任何系统参数。

## 解决什么问题

在安卓（FCL / Fold Craft Launcher + MobileGlues 渲染器）等 **OpenGL ES 转译环境** 中启动
Minecraft 26.3 时，游戏在资源加载阶段崩溃：

```
java.lang.RuntimeException: Failed to load required shader programs:
 - minecraft:pipeline/oit_transmittance_terrain_multidraw
 - minecraft:pipeline/cutout_terrain
 - minecraft:pipeline/oit_transmittance_terrain
 - minecraft:pipeline/translucent_terrain_multidraw
 - minecraft:pipeline/translucent_terrain
 - minecraft:pipeline/solid_terrain
 - minecraft:pipeline/oit_depth_bounds_terrain
 - minecraft:pipeline/cutout_terrain_multidraw
 - minecraft:pipeline/solid_terrain_multidraw
 - minecraft:pipeline/oit_depth_bounds_terrain_multidraw
 - minecraft:pipeline/oit_accumulate_terrain
 - minecraft:pipeline/oit_accumulate_terrain_multidraw
	at net.minecraft.client.renderer.ShaderManager.apply(ShaderManager.java:198)
```

原因：26.3 的地形渲染改用 Renderpearl 管线，12 条“必需地形管线”全部使用
`shaders/core/terrain.vsh/.fsh`。原版地形着色器里的 RGSS/最近邻采样
（`textureGrad`、`textureLod`、`dFdx/dFdy`、常量数组+循环）以及若干隐式整型运算，
MobileGlues 的 GLSL 转译器编译不了，于是 `ShaderManager.apply` 直接抛异常崩档。
（实体、方块、GUI、OIT 附属等其余 ~100 条管线在同一台设备上全部编译成功，
说明问题只出在地形着色器本身——这就是本模组的突破口。）

## 模组内部做了什么（闭环自愈，共三层）

1. **着色器降级（核心修复）** — `ShaderManagerSourceMixin`
   在 `ShaderManager.loadShader` 读取着色器源码时，把 `core/terrain` 的顶点/片元源码
   替换为**结构完全等价、语法最保守**的兼容版本：
   - 保留全部 UBO（`Globals`/`Fog`/`Projection`/`TerrainUniform`/`ChunkSection`）、
     采样器（`Sampler0`/`Sampler2`）、顶点输入输出布局与全部宏开关
     （`MULTIDRAW_TERRAIN`、`ALPHA_CUTOUT`、`OIT_*`），反射契约一个字节都不变；
   - 只把 RGSS/最近邻采样换成普通 `texture()` 采样（画质损失极小，等同于关闭纹理锐化）；
   - 12 条管线共用这两个文件，一次替换全部修复（包括 multidraw 与 OIT 变体）。

2. **崩溃拦截（安全网）** — `ShaderManagerApplyMixin`
   即使某些管线在你的驱动上仍然编译失败，`apply` 里
   “必需着色器失败 → 抛 RuntimeException” 的判定也会被拦截：
   失败只记日志，成功编译的管线照常装入缓存，游戏继续启动。

3. **绘制兜底（最后一道闸门）** — `RenderSystemPipelineMixin` + `GluesCompatGuard`
   渲染期查询缺失管线时，原版会抛 `IllegalStateException` 把游戏打崩；
   现在改为返回一个已成功编译的“应急管线”，最坏情况也只是某类内容显示异常，不崩档。

> 关于“multidraw → 基础着色器重定向”：26.3 运行时会按设备能力自动选择
> 非 multidraw 绘制路径（MobileGlues 下 `multiDrawIndirect=false`），
> multidraw 管线只是必须能“编译”——上面的着色器替换已经覆盖，无需再做 ID 重定向。

## 环境自适应，桌面无感

- 自动检测 MobileGlues / FCL / PojavLauncher 环境（`org.lwjgl.opengl.libname`、
  `LIBGL_EGL`、`POJAV_RENDERER`、`FCL_VERSION_CODE` 等），**只有检测到才降级着色器**；
- 普通桌面玩家装了这个模组：行为与原版完全一致（崩溃拦截仍作为安全网存在）；
- 可手动控制：启动参数加 `-Dgluescompat.mode=on|off|auto`（默认 auto）。

## 安装（FCL 用户）

1. 在 FCL 里为 **26.3 安装 Fabric 0.19.5+**（版本设置 → 安装加载器 → Fabric）；
2. 把 `gluescompat-1.0.0.jar` 放进 `.minecraft/mods`（就是崩溃日志里那个
   `/storage/emulated/0/FCL/.minecraft/versions/26.3` 对应的 mods 目录，
   若用全局目录则是 `/storage/emulated/0/FCL/.minecraft/mods`）；
3. 启动。日志里看到 `[GluesCompat] 兼容模式已启用` 即生效。

不需要 Fabric API。不需要其它前置。

## 从源码构建

```bash
./gradlew build
```

产物在 `build/libs/gluescompat-1.0.0.jar`（`-dev`/`-sources` 后缀的不是最终产物）。
仓库 `release/` 目录里附带了 CI 编译好的成品 jar。

## 已知限制

- 地形纹理的 RGSS 锐化采样被简化为普通采样（视觉上几乎无差）；
- 如果你的驱动连降级后的着色器都编译不了（极端情况），游戏依然能启动，
  但地形可能不可见——日志中会有 `[GluesCompat]` 的详细记录；
- 26.3 的 OIT（改进透明度）在部分转译层上可能效果打折，属驱动能力问题。

## 致谢与许可

基于 FabricMC fabric-example-mod 模板改造。源码以 CC0 1.0 发布（见 `LICENSE`）。
崩溃分析与 26.3 渲染结构参考：NeoForge 26.2→26.3 迁移 Primer、
Minecraft 26.3 客户端反编译源、MobileGlues 项目 issue 跟踪。
