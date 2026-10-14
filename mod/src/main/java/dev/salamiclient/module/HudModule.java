package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Basis für verschiebbare HUD-Elemente (Overlay-System).
 *
 * <p>Position als Anteil der Bildschirmgröße (0..1, linke obere Ecke), damit sie
 * bei jeder Auflösung und GUI-Skalierung stimmt. Verschoben wird im HUD-Editor;
 * die Positions-Settings sind in der ClickGUI ausgeblendet.
 *
 * <p>Darstellung bei jedem HUD-Element gleich (nach den Modul-Einstellungen angezeigt):
 * Größe, Hintergrund und dessen Farbe, Textschatten und – mit „Eigene Farben“ –
 * Akzent- und Textfarbe statt der Theme-Farben.
 */
public abstract class HudModule extends Module {
	public final NumberSetting x;
	public final NumberSetting y;
	public final NumberSetting scale;
	private final BoolSetting background;
	private final ColorSetting backgroundColor;
	private final BoolSetting textShadow;
	private final BoolSetting customColors;
	private final ColorSetting accentColor;
	private final ColorSetting textColor;

	protected HudModule(String id, Category category, boolean enabledByDefault, double defaultX, double defaultY) {
		super(id, category, enabledByDefault);
		this.x = add(new NumberSetting("x", defaultX, 0, 1, 0.001));
		this.y = add(new NumberSetting("y", defaultY, 0, 1, 0.001));
		x.hide();
		y.hide();
		this.scale = add(new NumberSetting("scale", 1.0, 0.5, 2.0, 0.1));
		scale.appearance();

		background = add(new BoolSetting("background", defaultBackground()));
		background.appearance();
		backgroundColor = add(new ColorSetting("background_color", 0x80000000));
		backgroundColor.appearance().showIf(background::isOn);
		if (!usesBackground()) {
			background.hide();
			backgroundColor.hide();
		}
		textShadow = add(new BoolSetting("text_shadow", defaultShadow()));
		textShadow.appearance();
		customColors = add(new BoolSetting("custom_colors", false));
		customColors.appearance();
		accentColor = add(new ColorSetting("accent_color", 0xFFB23A3A));
		accentColor.appearance().showIf(customColors::isOn);
		textColor = add(new ColorSetting("text_color", 0xFFFFFFFF));
		textColor.appearance().showIf(customColors::isOn);
		if (!usesText()) {
			textShadow.hide();
			customColors.hide();
			accentColor.hide();
			textColor.hide();
		}
	}

	// --- Was das Element unterstützt (Konstanten, werden schon im Konstruktor gefragt) ---

	/** Hintergrund standardmäßig an? */
	protected boolean defaultBackground() {
		return false;
	}

	/** Textschatten standardmäßig an? */
	protected boolean defaultShadow() {
		return true;
	}

	/** Gibt es überhaupt einen Hintergrund? */
	protected boolean usesBackground() {
		return true;
	}

	/** Zeichnet das Element seinen Hintergrund selbst (z. B. Keystrokes pro Taste)? */
	protected boolean drawsOwnBackground() {
		return false;
	}

	/** Rand um den Inhalt, wenn die Basis den Hintergrund zeichnet (0 = Inhalt hat schon Rand). */
	protected int backgroundPadding() {
		return 2;
	}

	/** Zeigt das Element Text (sonst keine Text-Einstellungen)? */
	protected boolean usesText() {
		return true;
	}

	// --- Farben für die Unterklassen ---

	/** Bezeichnungen und Hervorhebungen. */
	public final int accentColor() {
		return customColors.isOn() ? accentColor.argb() : SalamiClient.get().theme().accent();
	}

	/** Normaler Text. */
	public final int textColor() {
		return customColors.isOn() ? textColor.argb() : SalamiClient.get().theme().text();
	}

	/** Nebensächlicher Text (etwas blasser als {@link #textColor()}). */
	public final int dimColor() {
		if (!customColors.isOn()) {
			return SalamiClient.get().theme().textDim();
		}
		int text = textColor.argb();
		int alpha = Math.round((text >>> 24) * 0.7F);
		return (text & 0x00FFFFFF) | (alpha << 24);
	}

	/** Hintergrundfarbe oder 0, wenn der Hintergrund aus ist. */
	public final int backgroundColor() {
		return background.isOn() ? backgroundColor.argb() : 0;
	}

	public final boolean shadow() {
		return textShadow.isOn();
	}

	/** Breite/Höhe in unskalierten GUI-Pixeln (für Editor-Rahmen und Begrenzung am Rand). */
	public abstract int width();

	public abstract int height();

	/** Zeichnet bei (0,0) – Verschiebung und Skalierung übernimmt {@link #onHud}. */
	protected abstract void render(GuiGraphics graphics, DeltaTracker delta);

	/** Ob das Element gerade etwas anzeigt (Editor zeigt trotzdem einen Rahmen). */
	public boolean hasContent() {
		return true;
	}

	/** Eigene Größe × globale HUD-Größe (Modul „Oberfläche“). */
	public float effectiveScale() {
		return scale.get().floatValue() * GuiScaleModule.hudScale();
	}

	public int screenX(int guiWidth) {
		int w = Math.round(width() * effectiveScale());
		return Math.max(0, Math.min(guiWidth - w, (int) Math.round(x.get() * guiWidth)));
	}

	public int screenY(int guiHeight) {
		int h = Math.round(height() * effectiveScale());
		return Math.max(0, Math.min(guiHeight - h, (int) Math.round(y.get() * guiHeight)));
	}

	public void moveTo(int guiX, int guiY, int guiWidth, int guiHeight) {
		x.set((double) guiX / guiWidth);
		y.set((double) guiY / guiHeight);
	}

	@Override
	public final void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (!hasContent()) {
			return;
		}
		float s = effectiveScale();
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX(graphics.guiWidth()), screenY(graphics.guiHeight()));
		graphics.pose().scale(s, s);
		if (usesBackground() && !drawsOwnBackground() && background.isOn()) {
			int pad = backgroundPadding();
			graphics.fill(-pad, -pad, width() + pad, height() + pad, backgroundColor.argb());
		}
		render(graphics, delta);
		graphics.pose().popMatrix();
	}
}
