pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// Ein Quellcode für mehrere Minecraft-Versionen: https://stonecutter.kikugie.dev
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Wählt pro Version die passende Loom-Variante (mit Remapping bis 1.21.11, ohne ab 26.1)
	id("dev.kikugie.loom-back-compat") version "0.4.3"
}

stonecutter {
	create(rootProject) {
		// Eine Jar pro Eintrag; welche Minecraft-Versionen sie abdeckt, steht in stonecutter.properties.toml.
		versions("1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")
		// So steht der Code in git. Vor jedem Commit zurückschalten: ./gradlew "Reset active project"
		vcsVersion = "1.21.11"
	}
}

rootProject.name = "salami-core"
