package dev.salamiclient.gui.skin.werkbank;

import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Welches Minecraft-Item die Werkbank für einen Menüeintrag, eine Kategorie oder ein Modul zeigt, und
 * wie es gezeichnet wird.
 *
 * <p>Ab 26.1 bekommen Items ihre Komponenten erst mit den Registern einer Welt – im Titelbildschirm
 * lässt sich dann noch kein {@link ItemStack} bauen. Bis dahin zeichnet {@link #draw} das Item flach aus
 * Minecrafts eigenen Texturen ({@code textures/item/…}, {@code textures/block/…}), damit das Menü auch
 * vor der ersten Welt seine Symbole hat.
 */
final class WerkbankItems {
	private static final Map<Item, ItemStack> STACKS = new HashMap<>();
	private static final Map<Item, Optional<Identifier>> FLAT = new HashMap<>();
	private static final int LEATHER = 0xFFA06540;
	private static final int WATER = 0xFF385DC6;

	private WerkbankItems() {}

	/** Eintrag des Startmenüs bzw. Werkzeug der Mod-Liste. */
	static Item kind(MenuView.Kind kind) {
		return switch (kind) {
			case MODS -> Items.BOOK;
			case HUD -> Items.COMPASS;
			case CONFIGS -> Items.CHEST;
			case PANELS -> Items.CRAFTING_TABLE;
			case FRIENDS -> Items.PLAYER_HEAD;
			case COSMETICS -> Items.LEATHER_CHESTPLATE;
			case MINIGAMES -> Items.CAKE;
		};
	}

	/** Reiter der Mod-Liste (siehe {@code ModsView.Tab#id}). */
	static Item tab(String id) {
		return switch (id == null ? "" : id) {
			case "all" -> Items.NETHER_STAR;
			case "client" -> Items.COMPARATOR;
			case "visual" -> Items.PAINTING;
			case "hud" -> Items.CLOCK;
			case "misc" -> Items.BUNDLE;
			case "social" -> Items.PLAYER_HEAD;
			case "extras" -> Items.ENCHANTED_BOOK;
			default -> Items.BOOK;
		};
	}

	/** Modul („zoom“) oder mitgelieferte Mod („extra:waveycapes“); unbekannte bekommen ein Buch. */
	static Item module(String id) {
		return moduleItem(id == null ? "" : id);
	}

	// ------------------------------------------------------------------ Zeichnen

	/** Zeichnet {@code item} 16 × 16 bei (x, y) – als echtes Item, solange das geht, sonst flach. */
	static void draw(GuiGraphics g, Item item, int x, int y) {
		if (ready(item)) {
			GuiDraw.item(g, STACKS.computeIfAbsent(item, ItemStack::new), x, y);
		} else {
			flat(g, item, x, y);
		}
	}

	/** Lässt sich schon ein {@link ItemStack} bauen? Bis 1.21.11 immer, ab 26.1 erst mit gebundenen Komponenten. */
	private static boolean ready(Item item) {
		//? if >=26.1 {
		/*return item.builtInRegistryHolder().areComponentsBound();
		*///?} else
		return true;
	}

	private static void flat(GuiGraphics g, Item item, int x, int y) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		switch (path) {
			case "player_head" -> {
				// Steves Gesicht samt Hut-Schicht
				Identifier skin = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
				g.blit(RenderPipelines.GUI_TEXTURED, skin, x + 1, y + 1, 8f, 8f, 14, 14, 8, 8, 64, 64);
				g.blit(RenderPipelines.GUI_TEXTURED, skin, x + 1, y + 1, 40f, 8f, 14, 14, 8, 8, 64, 64);
			}
			case "chest" -> {
				// Vorderseite der Truhe aus der Truhen-Textur: Deckel, Unterteil, Schloss
				Identifier chest = Identifier.withDefaultNamespace("textures/entity/chest/normal.png");
				g.blit(RenderPipelines.GUI_TEXTURED, chest, x + 1, y + 1, 14f, 14f, 14, 5, 14, 5, 64, 64);
				g.blit(RenderPipelines.GUI_TEXTURED, chest, x + 1, y + 6, 14f, 33f, 14, 10, 14, 10, 64, 64);
				g.blit(RenderPipelines.GUI_TEXTURED, chest, x + 7, y + 4, 1f, 1f, 2, 4, 2, 4, 64, 64);
			}
			case "leather_chestplate", "leather_helmet" -> {
				texture(g, Identifier.withDefaultNamespace("textures/item/" + path + ".png"), x, y, LEATHER);
				texture(g, Identifier.withDefaultNamespace("textures/item/" + path + "_overlay.png"), x, y, 0xFFFFFFFF);
			}
			case "potion", "splash_potion" -> {
				texture(g, Identifier.withDefaultNamespace("textures/item/potion_overlay.png"), x, y, WATER);
				texture(g, Identifier.withDefaultNamespace("textures/item/" + path + ".png"), x, y, 0xFFFFFFFF);
			}
			default -> FLAT.computeIfAbsent(item, i -> flatTexture(path)).ifPresent(id -> texture(g, id, x, y, 0xFFFFFFFF));
		}
	}

	private static void texture(GuiGraphics g, Identifier id, int x, int y, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, 16, 16, 16, 16, 16, 16, color);
	}

	/** Flache Textur eines Items: eigene Item-Textur, sonst eine Seite des Blocks. */
	private static Optional<Identifier> flatTexture(String path) {
		String[] candidates = switch (path) {
			case "compass", "clock", "recovery_compass" -> new String[] {"item/" + path + "_00"};
			case "glass_pane" -> new String[] {"block/glass"};
			case "stone_button" -> new String[] {"block/stone"};
			case "barrel", "target", "tnt" -> new String[] {"block/" + path + "_side"};
			case "crafting_table", "lectern" -> new String[] {"block/" + path + "_front"};
			default -> new String[] {"item/" + path, "block/" + path};
		};
		for (String candidate : candidates) {
			Identifier id = Identifier.withDefaultNamespace("textures/" + candidate + ".png");
			if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	// ------------------------------------------------------------------ Zuordnung

	private static Item moduleItem(String id) {
		return switch (id) {
			// Client
			case "borderless_fullscreen" -> Items.GLASS_PANE;
			case "gui_scale" -> Items.ITEM_FRAME;
			case "salami_theme" -> Items.COOKED_PORKCHOP;
			// HUD
			case "armor_hud" -> Items.IRON_CHESTPLATE;
			case "combo_counter" -> Items.STONE_SWORD;
			case "info_hud" -> Items.MAP;
			case "item_counter" -> Items.ARROW;
			case "keystrokes" -> Items.STONE_BUTTON;
			case "logo_overlay" -> Items.GLOW_ITEM_FRAME;
			case "potion_status" -> Items.POTION;
			case "scoreboard" -> Items.OAK_SIGN;
			case "shield_status" -> Items.SHIELD;
			case "titles" -> Items.NAME_TAG;
			case "watermark" -> Items.PAPER;
			// Sonstiges
			case "auto_reconnect" -> Items.REPEATER;
			case "auto_text" -> Items.WRITABLE_BOOK;
			case "chat_tweaks" -> Items.FEATHER;
			case "confirm_disconnect" -> Items.OAK_DOOR;
			case "kits" -> Items.BARREL;
			case "no_advancement" -> Items.KNOWLEDGE_BOOK;
			case "panic_disconnect" -> Items.BARRIER;
			case "streamer_mode" -> Items.CARVED_PUMPKIN;
			case "tab_tweaks" -> Items.LECTERN;
			case "toggle_sprint" -> Items.SUGAR;
			// Soziales
			case "emotes" -> Items.ARMOR_STAND;
			case "friends" -> Items.PLAYER_HEAD;
			// Optik
			case "arrow_trail" -> Items.SPECTRAL_ARROW;
			case "block_outline" -> Items.GLASS;
			case "buddy" -> Items.PARROT_SPAWN_EGG;
			case "capes" -> Items.ELYTRA;
			case "clear_background" -> Items.TINTED_GLASS;
			case "color_saturation" -> Items.GLOW_INK_SAC;
			case "crosshair" -> Items.TARGET;
			case "fov_changer" -> Items.SPIDER_EYE;
			case "free_look" -> Items.ENDER_EYE;
			case "fullbright" -> Items.TORCH;
			case "glint_colorizer" -> Items.EXPERIENCE_BOTTLE;
			case "hats" -> Items.LEATHER_HELMET;
			case "health_indicators" -> Items.GOLDEN_APPLE;
			case "hide_armor" -> Items.CHAINMAIL_CHESTPLATE;
			case "hit_color" -> Items.REDSTONE;
			case "hitbox" -> Items.IRON_SWORD;
			case "item_model" -> Items.DIAMOND_PICKAXE;
			case "item_physics" -> Items.SLIME_BALL;
			case "light_level" -> Items.REDSTONE_TORCH;
			case "live_tracker" -> Items.RECOVERY_COMPASS;
			case "low_fire" -> Items.FIRE_CHARGE;
			case "minime" -> Items.TOTEM_OF_UNDYING;
			case "motion_blur" -> Items.PHANTOM_MEMBRANE;
			case "nametags" -> Items.NAME_TAG;
			case "no_fog" -> Items.GLASS_BOTTLE;
			case "no_hurt_cam" -> Items.MILK_BUCKET;
			case "old_animations" -> Items.WOODEN_SWORD;
			case "particles" -> Items.BLAZE_POWDER;
			case "shiny_pots" -> Items.SPLASH_POTION;
			case "side_shield" -> Items.SHIELD;
			case "time_changer" -> Items.CLOCK;
			case "tnt_timer" -> Items.TNT;
			case "weather_changer" -> Items.WATER_BUCKET;
			case "zoom" -> Items.SPYGLASS;
			// mitgelieferte Mods
			case "extra:waveycapes" -> Items.LEATHER;
			case "extra:skinlayers3d" -> Items.PLAYER_HEAD;
			case "extra:tiers" -> Items.DIAMOND;
			case "extra:appleskin" -> Items.APPLE;
			case "extra:shulkerboxtooltip" -> Items.SHULKER_BOX;
			default -> id.startsWith("extra:") ? Items.ENCHANTED_BOOK : Items.BOOK;
		};
	}
}
