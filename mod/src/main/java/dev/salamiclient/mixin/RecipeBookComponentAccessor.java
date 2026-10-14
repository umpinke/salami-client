package dev.salamiclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf das Suchfeld des Rezeptbuchs (Tasten beim Tippen dort nicht als Hotkey werten). */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
	@Accessor("searchBox")
	EditBox salami$getSearchBox();
}
