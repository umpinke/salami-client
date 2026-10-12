package dev.salamiclient.module;

import net.minecraft.world.entity.Entity;

/**
 * Für Module, die unmittelbar vor einem Angriff eingreifen (z. B. Slot tauschen).
 *
 * <p>Wird am Anfang von {@code MultiPlayerGameMode#attack} aufgerufen – also bevor
 * der Client den gehaltenen Slot und das Angriffs-Paket an den Server sendet.
 */
public interface AttackListener {
	void beforeAttack(Entity target);
}
