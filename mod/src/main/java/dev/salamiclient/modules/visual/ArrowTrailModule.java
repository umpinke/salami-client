package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;

/** Arrow Trail: fliegende Pfeile ziehen eine Partikelspur hinter sich her (nur bei dir sichtbar). */
public final class ArrowTrailModule extends Module {
	public enum Particle {
		FLAME,
		HEART,
		CRIT,
		END_ROD,
		NOTE,
		CLOUD
	}

	/** Pfeile weiter weg als das bekommen keine Spur (spart Leistung). */
	private static final double RANGE = 64.0;

	private final ModeSetting<Particle> particle = add(new ModeSetting<>("particle", Particle.FLAME));
	private final BoolSetting onlyOwn = add(new BoolSetting("only_own", true));

	public ArrowTrailModule() {
		super("arrow_trail", Category.VISUAL);
	}

	@Override
	public void onTick() {
		var player = mc().player;
		var level = mc().level;
		AABB area = player.getBoundingBox().inflate(RANGE);
		ParticleOptions type = type();
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, area)) {
			if (onlyOwn.isOn() && arrow.getOwner() != player) {
				continue;
			}
			// steckt im Boden oder in einem Block: keine Spur mehr
			if (arrow.getDeltaMovement().lengthSqr() < 1.0E-4) {
				continue;
			}
			level.addParticle(type, arrow.getX(), arrow.getY(), arrow.getZ(), 0.0, 0.0, 0.0);
		}
	}

	private ParticleOptions type() {
		return switch (particle.get()) {
			case FLAME -> ParticleTypes.FLAME;
			case HEART -> ParticleTypes.HEART;
			case CRIT -> ParticleTypes.CRIT;
			case END_ROD -> ParticleTypes.END_ROD;
			case NOTE -> ParticleTypes.NOTE;
			case CLOUD -> ParticleTypes.CLOUD;
		};
	}
}
