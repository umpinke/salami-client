package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.Projection;
import com.mojang.blaze3d.platform.Window;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NameTags: Namensschilder lesbarer machen – Größe (optional mit der Entfernung wachsend),
 * Hintergrund, eigener Name in der Third-Person-Ansicht und Leben/Rüstung hinter dem Namen
 * (mit den Herz- und Rüstungs-Symbolen aus dem Spiel). Nur Anzeige, nur bei dir.
 *
 * <p>Über dem Namen anderer Spieler zusätzlich eine Zeile mit Item-Symbolen: Haupthand, Rüstung,
 * Nebenhand (leere Plätze fallen weg). Sie wird im HUD gezeichnet, genau so groß und an derselben
 * Stelle wie das Namensschild – und nur, wenn Minecraft das Namensschild in diesem Frame auch zeigt
 * (Teams, Unsichtbarkeit, F1 und Reichweite gelten also wie für den Namen).
 */
public final class NameTagsModule extends Module {
	private static final Component HEART = Component.object(new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/heart/full")));
	private static final Component ARMOR = Component.object(new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/armor_full")));

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Ein Pixel des Namensschilds in Blöcken (Minecraft zeichnet Namen mit 0.025). */
	private static final float TAG_PIXEL = 0.025F;
	/** Über dem Kopf (Anhängepunkt) setzt Minecraft das Namensschild 0.5 Blöcke höher an. */
	private static final double TAG_LIFT = 0.5;
	/** Zeile mit dem Punktestand „unter dem Namen“ schiebt den Namen um 9 × 1.15 Pixel nach oben. */
	private static final double SCORE_LINE = 9 * 1.15 * TAG_PIXEL;
	/** Duckende Spieler zeichnet Minecraft samt Namen 2/16 Block tiefer. */
	private static final double CROUCH_OFFSET = -2.0 / 16.0;
	/** Bis zu dieser Entfernung² zeigt Minecraft die Punktestand-Zeile. */
	private static final double SCORE_LINE_RANGE_SQ = 100;
	/** Geduckte Spieler haben nur bis 32 Blöcke einen Namen. */
	private static final double DISCRETE_RANGE_SQ = 32 * 32;
	private static final int ICON = 16;
	private static final int GAP = 2;
	private static final int PAD = 1;
	/** Unterkante der Item-Zeile: 1 Pixel über dem Hintergrund des Namens (der beginnt bei y = -1). */
	private static final int ROW_BOTTOM = -2;

	private static NameTagsModule instance;

	private final BoolSetting showOwn = add(new BoolSetting("show_own", true));
	private final BoolSetting health = add(new BoolSetting("show_health", true));
	private final BoolSetting armor = add(new BoolSetting("show_armor", true));
	private final BoolSetting items = add(new BoolSetting("show_items", true));
	private final BoolSetting durability = add(new BoolSetting("durability", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final NumberSetting range = add(new NumberSetting("range", 64, 8, 64, 4));
	private final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 3.0, 0.1));
	private final BoolSetting distanceScale = add(new BoolSetting("distance_scale", false));
	private final NumberSetting background = add(new NumberSetting("background", 0.25, 0.0, 1.0, 0.05));

	/** Entity-ID → zuletzt gebaute Anzeige (wird nur neu gebaut, wenn sich Leben/Rüstung ändern). */
	private record Cached(int tenths, int armorValue, boolean withHealth, boolean withArmor, Component suffix) {}

	private final Map<Integer, Cached> cache = new HashMap<>();

	/** Spieler, deren Namensschild Minecraft seit dem letzten HUD-Frame vorbereitet hat. */
	private final List<Player> tagged = new ArrayList<>();
	private final IntOpenHashSet taggedIds = new IntOpenHashSet();
	/** Geduckte Spieler in Sichtlinie (ihr Name ist nicht durch Wände sichtbar – die Items auch nicht). */
	private final IntOpenHashSet sneakingInSight = new IntOpenHashSet();
	private final List<ItemStack> stacks = new ArrayList<>(6);
	private final float[] screen = new float[3];
	private Vec3 sortCamera = Vec3.ZERO;
	/** Weit entfernte zuerst zeichnen, nahe liegen oben. */
	private final Comparator<Player> farFirst = Comparator.comparingDouble(p -> -p.distanceToSqr(sortCamera));

	public NameTagsModule() {
		super("nametags", Category.VISUAL);
		instance = this;
		durability.showIf(items::isOn);
	}

	@Override
	public void onTick() {
		if (cache.size() > 512) {
			cache.clear(); // verschwundene Entities nicht ewig merken
		}
		updateSneakingInSight();
	}

	@Override
	protected void onDisable() {
		cache.clear();
		clearTagged();
		sneakingInSight.clear();
	}

	/** Für {@code LivingEntityRendererMixin}: eigener Name nur, wenn die Kamera nicht in dir steckt. */
	public static boolean showOwn(LivingEntity entity) {
		if (instance == null || !instance.isEnabled() || !instance.showOwn.isOn()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		// Namen sind unsichtbar, solange das HUD ausgeblendet ist (F1)
		//? if >=26.2 {
		/*boolean namesShown = !mc.gui.hud.isHidden();
		*///?} else
		boolean namesShown = Minecraft.renderNames();
		return entity == mc.player
			&& namesShown
			&& !mc.options.getCameraType().isFirstPerson()
			&& !entity.isInvisible();
	}

	/** Für {@code LivingEntityRendererMixin}: Namen nur bis zur eingestellten Reichweite (Abstand² zur Kamera). */
	public static boolean inRange(double distanceSq) {
		NameTagsModule module = instance;
		if (module == null || !module.isEnabled()) {
			return true;
		}
		double max = module.range.get();
		return distanceSq <= max * max;
	}

	/** Für {@code NameTagStorageMixin}. */
	public static float backgroundOpacity(float vanilla) {
		return instance != null && instance.isEnabled() ? instance.background.get().floatValue() : vanilla;
	}

	/**
	 * Für {@code NameTagStorageMixin}: Größenfaktor eines Namensschilds. Mit „Mit Entfernung größer“
	 * bleibt es ab 8 Blöcken etwa gleich groß auf dem Bildschirm (höchstens 4×).
	 */
	public static float scale(double distanceSq) {
		NameTagsModule module = instance;
		if (module == null || !module.isEnabled()) {
			return 1F;
		}
		float factor = module.scale.get().floatValue();
		if (module.distanceScale.isOn()) {
			factor *= (float) Math.max(1.0, Math.min(4.0, Math.sqrt(distanceSq) / 8.0));
		}
		return factor;
	}

	/** Für {@code EntityRendererMixin}: Leben und Rüstung hinter den Namen setzen. */
	public static Component decorate(Component name, Entity entity) {
		NameTagsModule module = instance;
		if (module == null || !module.isEnabled() || name == null || !(entity instanceof LivingEntity living)) {
			return name;
		}
		// Minecraft holt den Namen nur, wenn es das Schild auch zeichnet → hier merken, über wem die Items stehen
		if (living instanceof Player player && player != Minecraft.getInstance().player) {
			module.markTagged(player);
		}
		if (!(living instanceof Player) && !module.mobs.isOn()) {
			return name;
		}
		// Health Indicators zeigt das Leben schon – nicht doppelt
		boolean withHealth = module.health.isOn() && !HealthIndicatorsModule.active();
		boolean withArmor = module.armor.isOn() && living.getArmorValue() > 0;
		if (!withHealth && !withArmor) {
			return name;
		}
		return Component.empty().append(name).append(module.suffix(living, withHealth, withArmor));
	}

	private Component suffix(LivingEntity living, boolean withHealth, boolean withArmor) {
		float hp = living.getHealth() + living.getAbsorptionAmount();
		int tenths = Math.round(hp * 5); // Herzen in Zehnteln (2 Leben = 1 Herz)
		int armorValue = living.getArmorValue();
		Cached cached = cache.get(living.getId());
		if (cached != null && cached.tenths() == tenths && cached.armorValue() == armorValue
			&& cached.withHealth() == withHealth && cached.withArmor() == withArmor) {
			return cached.suffix();
		}
		MutableComponent suffix = Component.empty();
		if (withHealth) {
			float max = Math.max(1F, living.getMaxHealth());
			ChatFormatting color = hp > max * 0.6F ? ChatFormatting.GREEN : hp > max * 0.3F ? ChatFormatting.GOLD : ChatFormatting.RED;
			suffix.append(" ").append(HEART).append(Component.literal(" " + tenths / 10 + "." + tenths % 10).withStyle(color));
		}
		if (withArmor) {
			suffix.append(" ").append(ARMOR).append(Component.literal(" " + armorValue).withStyle(ChatFormatting.AQUA));
		}
		cache.put(living.getId(), new Cached(tenths, armorValue, withHealth, withArmor, suffix));
		return suffix;
	}

	// --- Item-Zeile über dem Namen (HUD) ---

	private void markTagged(Player player) {
		if (!items.isOn()) {
			return;
		}
		if (tagged.size() >= 256) {
			clearTagged(); // HUD lief länger nicht – nicht endlos sammeln
		}
		// einmal pro Spieler, auch wenn ein Shader-Schattendurchgang die Namen ein zweites Mal holt
		if (taggedIds.add(player.getId())) {
			tagged.add(player);
		}
	}

	private void clearTagged() {
		tagged.clear();
		taggedIds.clear();
	}

	/** Sichtlinie zu geduckten Spielern – einmal pro Tick statt pro Frame. */
	private void updateSneakingInSight() {
		sneakingInSight.clear();
		if (!items.isOn()) {
			return;
		}
		LocalPlayer self = mc().player;
		for (Player player : mc().level.players()) {
			if (player != self && player.isDiscrete() && self.distanceToSqr(player) < DISCRETE_RANGE_SQ
				&& self.hasLineOfSight(player)) {
				sneakingInSight.add(player.getId());
			}
		}
	}

	@Override
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (tagged.isEmpty()) {
			return;
		}
		try {
			// Live Tracker zeigt die Rüstung schon über dem Namen – nicht doppelt
			if (items.isOn() && mc().level != null && !liveTrackerActive() && Projection.begin()) {
				drawItemRows(graphics, delta.getGameTimeDeltaPartialTick(true));
			}
		} finally {
			clearTagged();
		}
	}

	private static boolean liveTrackerActive() {
		return SalamiClient.get().modules().get(LiveTrackerModule.class).isEnabled();
	}

	private void drawItemRows(GuiGraphics graphics, float partialTick) {
		Window window = mc().getWindow();
		// exakte GUI-Größe – guiWidth()/guiHeight() sind aufgerundet und würden die Zeile leicht verschieben
		float guiScale = window.getGuiScale();
		float width = window.getWidth() / guiScale;
		float height = window.getHeight() / guiScale;
		Vec3 camera = mc().gameRenderer.getMainCamera().position();
		boolean scoreLine = mc().level.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null;
		int backgroundColor = Math.round(background.get().floatValue() * 255) << 24;

		sortCamera = camera;
		tagged.sort(farFirst);
		for (Player player : tagged) {
			if (!player.isAlive() || player.isRemoved()) {
				continue;
			}
			if (player.isDiscrete() && !sneakingInSight.contains(player.getId())) {
				continue; // Minecraft zeigt den Namen Geduckter nicht durch Wände
			}
			collectItems(player);
			if (stacks.isEmpty()) {
				continue;
			}
			Vec3 position = player.getPosition(partialTick);
			double distanceSq = position.distanceToSqr(camera);
			// derselbe Ankerpunkt wie Minecrafts Namensschild: Oberkante der Namenszeile
			Vec3 attachment = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getYRot(partialTick));
			double lift = TAG_LIFT + (scoreLine && distanceSq < SCORE_LINE_RANGE_SQ ? SCORE_LINE : 0)
				+ (player.isCrouching() ? player.getScale() * CROUCH_OFFSET : 0);
			Vec3 anchor = attachment != null
				? position.add(attachment.x, attachment.y + lift, attachment.z)
				: position.add(0, player.getBbHeight() + lift, 0);
			if (!Projection.toGuiScaled(anchor, width, height, screen)) {
				continue;
			}
			// GUI-Pixel pro Pixel des Namensschilds – mit Größe und „Mit Entfernung größer“ wie beim Namen
			float pixel = screen[2] * TAG_PIXEL * scale(distanceSq);
			if (pixel < 0.1F) {
				continue; // nur noch ein paar Bildpunkte groß
			}
			drawRow(graphics, screen[0], screen[1], pixel, backgroundColor);
		}
	}

	private void drawRow(GuiGraphics graphics, float x, float y, float pixel, int backgroundColor) {
		int rowWidth = stacks.size() * (ICON + GAP) - GAP;
		int top = ROW_BOTTOM - PAD - ICON;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(pixel, pixel);
		graphics.pose().translate(-rowWidth / 2F, 0F);
		if ((backgroundColor >>> 24) != 0) {
			graphics.fill(-PAD, top - PAD, rowWidth + PAD, ROW_BOTTOM, backgroundColor);
		}
		int itemX = 0;
		for (ItemStack stack : stacks) {
			GuiDraw.item(graphics, stack, itemX, top);
			if (durability.isOn()) {
				GuiDraw.itemDecorations(graphics, mc().font, stack, itemX, top);
			}
			itemX += ICON + GAP;
		}
		graphics.pose().popMatrix();
	}

	/** Haupthand, Rüstung von oben nach unten, Nebenhand – leere Plätze fallen weg. */
	private void collectItems(Player player) {
		stacks.clear();
		addIfPresent(player.getItemBySlot(EquipmentSlot.MAINHAND));
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			addIfPresent(player.getItemBySlot(slot));
		}
		addIfPresent(player.getItemBySlot(EquipmentSlot.OFFHAND));
	}

	private void addIfPresent(ItemStack stack) {
		if (!stack.isEmpty()) {
			stacks.add(stack);
		}
	}
}
