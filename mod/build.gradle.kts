plugins {
	// Wendet die passende Loom-Variante an: fabric-loom-remap bis 1.21.11, fabric-loom ab 26.1
	id("dev.kikugie.loom-back-compat")
}

// Läuft einmal pro Minecraft-Version (versions/<version>/), Werte aus stonecutter.properties.toml.
val minecraft = sc.current.version
version = "${salamiVersion()}+$minecraft"

/**
 * Version von salami-core = Version des Launchers, mit dem die Jar ausgeliefert wird:
 * `-PsalamiVersion=1.8.6` (so baut launcher/scripts/bundle-salami-core.mjs), sonst aus
 * launcher/src-tauri/tauri.conf.json, sonst mod.version aus stonecutter.properties.toml.
 */
fun salamiVersion(): String {
	providers.gradleProperty("salamiVersion").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
	val tauriConf = rootProject.layout.projectDirectory.file("../launcher/src-tauri/tauri.conf.json")
	if (tauriConf.asFile.isFile) {
		// über providers gelesen, damit der Konfigurationscache Änderungen an der Datei bemerkt
		val json = providers.fileContents(tauriConf).asText.get()
		@Suppress("UNCHECKED_CAST")
		val launcherVersion = (groovy.json.JsonSlurper().parseText(json) as Map<String, Any?>)["version"] as? String
		if (!launcherVersion.isNullOrBlank()) {
			return launcherVersion
		}
	}
	return sc.properties.get<String>("mod.version")
}
group = sc.properties.get<String>("mod.group")

base {
	archivesName = sc.properties.get<String>("mod.archives_name")
}

// Ab 26.1 verlangt Minecraft Java 25
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

dependencies {
	minecraft("com.mojang:minecraft:$minecraft")
	// Mojang-Mappings bis 1.21.11 – ab 26.x ist Minecraft unverschleiert und hat genau diese Namen.
	loomx.applyMojangMappings()
	// mod… geht auch ab 26.1; loom-back-compat macht daraus implementation
	modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
	// Mod Menu nur zum Kompilieren: der Eintrag „modmenu“ in fabric.mod.json wird nur geladen, wenn
	// Mod Menu installiert ist. Dieselbe Version wie im Salami-Pack (packs/salami-packs.json).
	val modMenu = packModCoordinate(minecraft, "modmenu")
		?: error("packs/salami-packs.json hat kein Mod Menu für Minecraft $minecraft")
	modCompileOnly(modMenu) { isTransitive = false }
}

// Kompatibilitätstest: alle Modrinth-Mods des Salami-Packs (Sodium, Lithium …) mitladen.
// ./gradlew :26.3:runClientGameTest -PpackMods
// Die Jars kommen unverändert in den mods-Ordner des Testlaufs – wie im Launcher, samt eingebetteter
// Bibliotheken; bis 1.21.11 übersetzt Fabric Loader ihre Namen beim Start. Ohne -PpackMods wird der
// Ordner wieder geleert.
val packMods = configurations.create("salamiPackMods") { isTransitive = false }
if (providers.gradleProperty("packMods").isPresent) {
	// Der Test-Screenshot der Gametest-API für 26.1 überspringt GameRenderer.update, wo Sodium sein
	// Gelände vorbereitet ("Global terrain uniforms have not been updated"). Im Spiel läuft es normal.
	val testSkips = if (sc.current.parsed >= "26.1" && sc.current.parsed < "26.2") setOf("sodium") else emptySet()
	for (coordinate in packModCoordinates(minecraft, testSkips)) {
		dependencies.add(packMods.name, coordinate)
	}
}
val syncPackMods = tasks.register<Sync>("syncPackMods") {
	from(packMods)
	into(layout.buildDirectory.dir("run/clientGameTest/mods"))
	// Fabric API leert den Testordner vor jedem Lauf – erst danach die Mods hineinlegen
	mustRunAfter(tasks.matching { it.name == "deleteGameTestRunDir" })
}
tasks.matching { it.name == "runClientGameTest" }.configureEach { dependsOn(syncPackMods) }

/**
 * Modrinth-Maven-Koordinaten der Pack-Mods für eine Minecraft-Version (packs/salami-packs.json).
 * Ohne Fabric API (kommt schon aus dem Build), Dynamic FPS (bremst unfokussierte Testfenster) und
 * ohne [testSkips] (Mods, die nur in der Testumgebung dieser Version nicht laufen).
 */
fun packModCoordinates(minecraft: String, testSkips: Set<String>): List<String> {
	// Eingrenzen bei Problemen: -PpackModsSkip=badoptimizations,scalablelux
	val skipped = providers.gradleProperty("packModsSkip").orNull?.split(",")?.map { it.trim() }.orEmpty()
	val skip = setOf("fabric-api", "dynamic-fps") + skipped + testSkips
	return packModEntries(minecraft).filter { (id, _) -> id !in skip }.map { it.second }
}

/** Maven-Koordinate einer Mod des Salami-Packs für diese Minecraft-Version, oder `null`. */
fun packModCoordinate(minecraft: String, modId: String): String? =
	packModEntries(minecraft).firstOrNull { it.first == modId }?.second

/** Alle Modrinth-Mods des Salami-Packs für eine Minecraft-Version: (id, Maven-Koordinate). */
fun packModEntries(minecraft: String): List<Pair<String, String>> {
	// über providers gelesen, damit der Konfigurationscache Änderungen an der Datei bemerkt
	val json = providers.fileContents(rootProject.layout.projectDirectory.file("../packs/salami-packs.json")).asText.get()
	@Suppress("UNCHECKED_CAST")
	val pack = groovy.json.JsonSlurper().parseText(json) as Map<String, Any?>
	val mods = ((pack["packs"] as Map<String, Any?>)["salami"] as Map<String, Any?>)["mods"] as List<Map<String, Any?>>
	return mods.mapNotNull { mod ->
		val source = mod["source"] as Map<String, Any?>
		val target = ((mod["compatibility"] as Map<String, Any?>)[minecraft] as Map<String, Any?>?)?.get("fabric") as Map<String, Any?>?
		if (source["type"] != "modrinth" || target == null) null
		else mod["id"].toString() to "maven.modrinth:${source["projectId"]}:${target["identifier"]}"
	}
}

repositories {
	maven("https://api.modrinth.com/maven") {
		name = "Modrinth"
		content { includeGroup("maven.modrinth") }
	}
}

fabricApi {
	// Client-Gametests: startet Minecraft, prüft UI/Logik automatisch, macht Screenshots.
	// Ausführen: ./gradlew :1.21.11:runClientGameTest  (Screenshots: versions/<version>/build/run/clientGameTest/screenshots)
	configureTests {
		createSourceSet = true
		modId = "salami-gametest"
		enableGameTests = false
		enableClientGameTests = true
		eula = true
	}
}

loom {
	runs {
		configureEach {
			// Utility-Module (Explosionen, Inventar-Klicks) bringen den deterministischen
			// Netzwerk-Synchronizer der Gametests durcheinander; die Tests warten stattdessen
			// mit Zeitfenstern auf das erwartete Ergebnis.
			if (name == "clientGameTest") {
				jvmArguments.add("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
				// Repo-Ordner, damit Tests Dateien des Launchers finden (z. B. die Cape-Vorlage)
				jvmArguments.add("-Dsalami.repoDir=${rootProject.projectDir.parentFile.absolutePath}")
				// Nur einzelne Tests: -PsalamiTests=Benchmark,Client (Teile des Klassennamens)
				providers.gradleProperty("salamiTests").orNull?.let { jvmArguments.add("-Dsalami.tests=$it") }
				// Mit fremden Mods testen (Verträglichkeit): -PsalamiExtraMods=C:/pfad/a.jar;C:/pfad/b.jar
				// (der Mods-Ordner des Testlaufs wird vor jedem Lauf geleert)
				providers.gradleProperty("salamiExtraMods").orNull?.let { jvmArguments.add("-Dfabric.addMods=$it") }
				// Look der Salami-Menüs (wie im Launcher gewählt): -PsalamiLook=theke|werkbank|salumeria
				providers.gradleProperty("salamiLook").orNull?.let { jvmArguments.add("-Dsalami.look=$it") }
			}
		}
	}
}

tasks.processResources {
	// Mixins, die es nur in manchen Versionen gibt (die Klasse ist sonst leer)
	val versionMixins = buildList {
		if (sc.current.parsed >= "26.3") add("FirstPersonHandsMixin")
	}
	val props = mapOf(
		"version" to project.version.toString(),
		"minecraft" to sc.properties.get<String>("mod.mc_compat"),
		"java" to requiredJava.majorVersion,
		"versionMixins" to versionMixins.joinToString("") { ",\n\t\t\"$it\"" },
	)
	inputs.properties(props)

	filesMatching(listOf("fabric.mod.json", "salami.mixins.json")) {
		expand(props)
	}

	// Emote- und Hut-Katalog: dieselben Dateien, die der Cape-Server ausliefert (dort kommt Neues ohne
	// Update dazu), als Notfall-Kopie in die Jar – für Starts ohne Launcher oder wenn der Server nicht
	// erreichbar ist
	from(rootProject.layout.projectDirectory.files("../server/capes/emotes.json", "../server/capes/hats.json", "../server/capes/buddies.json")) {
		into("assets/salami")
	}

	// Ab 26.3 werden Shader zu SPIR-V übersetzt (Vulkan): Ein- und Ausgaben brauchen feste Plätze
	val spirvShaders = sc.current.parsed >= "26.3"
	inputs.property("spirvShaders", spirvShaders)
	if (spirvShaders) {
		filesMatching("assets/*/shaders/**/*.fsh") {
			filter { line ->
				when {
					line.startsWith("#version ") -> "$line\n#extension GL_ARB_separate_shader_objects : require"
					line.startsWith("in vec2 texCoord;") -> "layout(location = 0) $line"
					line.startsWith("out vec4 fragColor;") -> "layout(location = 0) $line"
					else -> line
				}
			}
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = requiredJava.majorVersion.toInt()
	options.encoding = "UTF-8"
}

java {
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
}

tasks.jar {
	from(rootProject.file("../LICENSE")) {
		rename { "LICENSE_salami-core" }
	}
}

// Name, unter dem der Launcher die Jar dieser Version erwartet (packs/salami-packs.json).
val launcherJarName = "salami-core-$minecraft.jar"

// Kopiert die gebaute Jar in den Datenordner des Salami-Launchers, wo das Pack "salami" sie als
// file:dev/salami-core-<version>.jar erwartet. Ohne Versionspfad für alle Versionen:
// ./gradlew installDev   Anderer Ort: ./gradlew installDev -PlauncherDir=<pfad>
val launcherDir = providers.gradleProperty("launcherDir").orElse(
	providers.environmentVariable("APPDATA").map { "$it/salamiclient/SalamiClient" }
		.orElse(providers.systemProperty("user.home").map { "$it/.local/share/salamiclient" })
)

tasks.register<Copy>("installDev") {
	group = "salami"
	description = "Builds salami-core and copies it into the Salami launcher (dev pack)."
	val jarName = launcherJarName // lokale Kopie: der Konfigurationscache kann keine Skript-Variablen speichern
	from(loomx.modJar.flatMap { it.archiveFile })
	into(launcherDir.map { "$it/dev" })
	rename { jarName }
}

// Fester Ordner für den Launcher-Installer (bundle.resources in tauri.release-local.json):
// der Installer legt die Jars als mods/salami-core-<version>.jar neben die Launcher-Exe.
tasks.register<Copy>("launcherBundle") {
	group = "salami"
	description = "Builds salami-core and stages it for the launcher installer."
	val jarName = launcherJarName
	from(loomx.modJar.flatMap { it.archiveFile })
	into(rootProject.layout.buildDirectory.dir("launcher"))
	rename { jarName }
}
