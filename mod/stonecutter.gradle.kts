plugins {
	id("dev.kikugie.stonecutter")
}

// Version, für die der Code in src/ gerade steht (IDE, runClient). Siehe settings.gradle.kts.
stonecutter active "1.21.11"

stonecutter parameters {
	// Umbenennungen, die sich per Textersatz erledigen lassen. Stonecutter ersetzt in beide
	// Richtungen – deshalb nur Namen, die im Code nichts anderes bedeuten können.
	replacements {
		// 1.21.11: ResourceLocation heißt Identifier, Klassen in neue Pakete verschoben
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
			replace("dimension().location()", "dimension().identifier()")
			replace("canInteractWithBlock(", "isWithinBlockInteractionRange(")
			replace("canInteractWithEntity(", "isWithinEntityInteractionRange(")
			replace("client.renderer.RenderType;", "client.renderer.rendertype.RenderType;")
			replace("world.entity.projectile.AbstractArrow;", "world.entity.projectile.arrow.AbstractArrow;")
			replace("world.entity.projectile.Arrow;", "world.entity.projectile.arrow.Arrow;")
			replace("world.entity.monster.Zombie;", "world.entity.monster.zombie.Zombie;")
			replace("world.entity.animal.Cow;", "world.entity.animal.cow.Cow;")
		}
		// 26.1: GUI-Zeichnen heißt jetzt „extract“ (Methodennamen siehe render/GuiDraw),
		// Fabric API spricht von Level statt World und KeyMapping statt KeyBinding
		string(current.parsed >= "26.1") {
			replace("GuiGraphics", "GuiGraphicsExtractor")
			replace("renderer.state.BlockOutlineRenderState;", "renderer.state.level.BlockOutlineRenderState;")
			replace("client.keybinding.v1.KeyBindingHelper;", "client.keymapping.v1.KeyMappingHelper;")
			replace("KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
			replace("KeyBindingHelper.getBoundKeyOf(", "KeyMappingHelper.getBoundKeyOf(")
			replace("lifecycle.v1.ClientWorldEvents;", "lifecycle.v1.ClientLevelEvents;")
			replace("ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE", "ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE")
			replace("ClickType", "ContainerInput")
			replace(".handleInventoryMouseClick(", ".handleContainerInput(")
			replace(".getTags().contains(", ".entityTags().contains(")
			replace("renderer.entity.ItemRenderer;", "renderer.feature.ItemFeatureRenderer;")
			replace("ItemRenderer.ENCHANTED_GLINT_", "ItemFeatureRenderer.ENCHANTED_GLINT_")
			// Tageszeit kommt aus den Welt-Uhren
			replace("level.getDayTime()", "level.getOverworldClockTime()")
			// Mixin-Ziele: Zeichenmethoden heißen extract… statt render… (mit Anführungszeichen = nur Mixins)
			replace("\"renderDefaultSprite\"", "\"extractDefaultSprite\"")
			replace("\"renderEffects\"", "\"extractEffects\"")
			replace("\"renderTitle\"", "\"extractTitle\"")
			replace("\"renderOverlayMessage\"", "\"extractOverlayMessage\"")
			replace("\"renderPingIcon\"", "\"extractPingIcon\"")
			replace("\"renderTransparentBackground\"", "\"extractTransparentBackground\"")
			replace("\"renderBlurredBackground\"", "\"extractBlurredBackground\"")
			replace("\"renderLogo(", "\"extractRenderState(")
			replace("Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;", "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;")
			replace("Lnet/minecraft/client/Options;getBackgroundOpacity(F)F", "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F")
		}
		// 26.2: Kamera-Getter ohne „get“; das In-Game-HUD heißt Hud und hängt an Minecraft.gui
		string(current.parsed >= "26.2") {
			replace("gameRenderer.getMainCamera()", "gameRenderer.mainCamera()")
			replace("import net.minecraft.client.gui.Gui;", "import net.minecraft.client.gui.Hud;")
			replace("@Mixin(Gui.class)", "@Mixin(Hud.class)")
			replace("Gui.getMobEffectSprite(", "Hud.getMobEffectSprite(")
			replace(".gui.getChat()", ".gui.hud.getChat()")
			replace(".gui.getGuiTicks()", ".gui.hud.getGuiTicks()")
			replace("options.hideGui", "gui.hud.isHidden()")
			replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()")
			replace(".getToastManager()", ".gui.toastManager()")
		}
		// 26.2: der offene Bildschirm gehört jetzt zu Minecraft.gui (mc.screen → mc.gui.screen()).
		// Nur für Variablen, die Minecraft sind – nicht für context.setScreen in den Gametests.
		val mcVar = """(?<![\w.])(mc\(\)|mc|c|client|minecraft)"""
		regex(current.parsed >= "26.2") {
			replace("""$mcVar\.screen(?![\w(])""", "\$1.gui.screen()", """$mcVar\.gui\.screen\(\)""", "\$1.screen")
		}
		regex(current.parsed >= "26.2") {
			replace("""$mcVar\.setScreen\(""", "\$1.gui.setScreen(", """$mcVar\.gui\.setScreen\(""", "\$1.setScreen(")
		}
		// 26.3: Grafik-Pipelines in eigenem Paket (renderpearl)
		string(current.parsed >= "26.3") {
			replace("com.mojang.blaze3d.pipeline.RenderPipeline;", "com.mojang.renderpearl.api.pipeline.RenderPipeline;")
			replace("Lcom/mojang/blaze3d/pipeline/RenderPipeline;", "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;")
			replace("com.mojang.blaze3d.pipeline.DepthStencilState;", "com.mojang.renderpearl.api.pipeline.DepthStencilState;")
			replace("com.mojang.blaze3d.platform.CompareOp;", "com.mojang.renderpearl.api.pipeline.CompareOp;")
			replace("Lcom/mojang/blaze3d/buffers/GpuBufferSlice;", "Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")
			// SDL statt GLFW: Tastaturtasten sind Scancodes
			replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
			replace("InteractionResult.SwingSource.CLIENT", "InteractionResult.SwingSource.PREDICTED")
			// Drehung mit Quaternion heißt rotate statt mulPose
			replace("poseStack.mulPose(", "poseStack.rotate(")
			replace("LevelRenderer;doEntityOutline()V", "LevelRenderer;blitEntityOutline()V")
			replace("\"addBreakingBlockEffect\"", "\"addBreakingBlockEffects\"")
		}
	}
}
