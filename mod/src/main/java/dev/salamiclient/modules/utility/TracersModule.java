package dev.salamiclient.modules.utility;

import com.mojang.blaze3d.platform.Window;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Projection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

/**
 * Tracers: Linien von der Bildschirmmitte (Fadenkreuz) zu anderen Spielern, gezeichnet im HUD.
 * Spieler hinter dir bekommen eine Linie zum Bildschirmrand in ihrer Richtung. Die Linienstärke
 * gilt in echten Bildschirmpixeln – gleich bei jeder GUI-Skalierung und Auflösung.
 *
 * <p>Steht wie ESP unter "Utility": die Linien verraten Spieler hinter Wänden.
 * Tote, unsichtbare (für dich) und Zuschauer bekommen keine Linie.
 */
public final class TracersModule extends Module {
	/** Wohin die Linie am Spieler zeigt. */
	public enum Target { HEAD, BODY, FEET }

	private final ColorSetting color = add(new ColorSetting("color", 0xFFFF5555).noAlpha());
	private final NumberSetting opacity = add(new NumberSetting("opacity", 0.8, 0.1, 1.0, 0.05));
	private final NumberSetting lineWidth = add(new NumberSetting("width", 1.5, 0.5, 5, 0.5));
	private final NumberSetting range = add(new NumberSetting("range", 128, 8, 256, 8));
	private final ModeSetting<Target> target = add(new ModeSetting<>("target", Target.BODY));
	private final BoolSetting teammates = add(new BoolSetting("teammates", false));
	private final BoolSetting teamColor = add(new BoolSetting("team_color", false));

	/** Wiederverwendet: keine Allokation pro Linie. */
	private final float[] screen = new float[2];

	public TracersModule() {
		super("tracers", Category.UTILITY);
	}

	private boolean shows(Player player, LocalPlayer self, Entity camera) {
		return player != self
			&& player != camera
			&& player.isAlive()
			&& !player.isDeadOrDying()
			&& !player.isSpectator()
			// berücksichtigt Teams, die unsichtbare Mitspieler sehen dürfen
			&& !player.isInvisibleTo(self)
			&& (teammates.isOn() || !self.isAlliedTo(player));
	}

	@Override
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {
		LocalPlayer self = mc().player;
		if (self == null || mc().level == null || !Projection.begin()) {
			return;
		}
		Window window = mc().getWindow();
		// Exakte GUI-Größe statt guiWidth()/guiHeight() (aufgerundet): sonst läge der Start bei
		// ungeraden Auflösungen neben dem Fadenkreuz und die Ziele leicht versetzt.
		float guiScale = window.getGuiScale();
		float width = window.getWidth() / guiScale;
		float height = window.getHeight() / guiScale;
		float centerX = width / 2F;
		float centerY = height / 2F;
		float thickness = lineWidth.get().floatValue() / guiScale;
		float partialTick = delta.getGameTimeDeltaPartialTick(true);
		double maxSq = range.get() * range.get();
		int alpha = Math.round(opacity.get().floatValue() * 255) << 24;
		int baseColor = color.argb() & 0xFFFFFF;
		Entity camera = mc().getCameraEntity();
		Vec3 selfPosition = self.getPosition(partialTick);

		for (Player player : mc().level.players()) {
			if (!shows(player, self, camera)) {
				continue;
			}
			Vec3 position = player.getPosition(partialTick);
			if (position.distanceToSqr(selfPosition) > maxSq) {
				continue;
			}
			Projection.toGuiOrEdge(position.add(0, targetHeight(player), 0), width, height, screen);
			int rgb = teamColor.isOn() && player.getTeam() != null ? player.getTeamColor() & 0xFFFFFF : baseColor;
			line(graphics, centerX, centerY, screen[0], screen[1], thickness, alpha | rgb);
		}
	}

	private double targetHeight(Player player) {
		return switch (target.get()) {
			case HEAD -> player.getEyeHeight();
			case BODY -> player.getBbHeight() * 0.5;
			case FEET -> 0;
		};
	}

	/** Linie als gedrehtes Rechteck – GuiGraphics kann nur achsenparallel füllen. */
	private static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, float thickness, int argb) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5F) {
			return;
		}
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.scale(length, thickness);
		pose.translate(0F, -0.5F); // quer zur Linie mittig
		graphics.fill(0, 0, 1, 1, argb);
		pose.popMatrix();
	}
}
