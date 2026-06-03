import net.fabricmc.loom.task.prod.ClientProductionRunTask
import net.raphimc.classtokenreplacer.extension.ClassTokenReplacerExtension

plugins {
	id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
	id("net.raphimc.class-token-replacer") version "1.0.0"
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val modVersion = providers.gradleProperty("mod_version").get()
val mavenGroup = providers.gradleProperty("maven_group").get()
val archiveBaseName = providers.gradleProperty("archives_base_name").get()
val mcVersionRange = providers.gradleProperty("minecraft_version_range").get()

version = modVersion
group = mavenGroup

base {
	archivesName = archiveBaseName
}

val targetJavaVersion = 21
tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = targetJavaVersion
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

val rusherhackApi by configurations.creating {
	isCanBeResolved = true
}

configurations.compileOnly {
	extendsFrom(rusherhackApi)
}

repositories {
	mavenCentral()
	maven("https://maven.rusherhack.org/snapshots")
	maven("https://maven.parchmentmc.org")
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	modImplementation("net.fabricmc:fabric-loader:0.19.2")
	mappings(loom.layered {
		officialMojangMappings()
		parchment("org.parchmentmc.data:parchment-$minecraftVersion:2025.12.20@zip")
	})
	rusherhackApi("org.rusherhack:rusherhack-api:$minecraftVersion-SNAPSHOT")
}

loom {
	// apply accesswidener from rusherhack-api
	for (file in zipTree(rusherhackApi.singleFile)) {
		if (file.name == "rusherhack.accesswidener") {
			accessWidenerPath = file
		}
	}

	// disable run configs
	runConfigs.configureEach {
		setIdeConfigGenerated(false)
	}
}

tasks {
	processResources {
		inputs.property("mod_version", modVersion)

		filesMatching("rusherhack-plugin.json") {
			expand("mod_version" to modVersion)
		}
	}
	val copyPluginToRunDir = register("copyPluginToRunDir", Copy::class) {
		group = "build"
		dependsOn(remapJar)
		from(remapJar.map { it.outputs })
		into(file("run/rusherhack/plugins"))
	}
	register("runPlugin", ClientProductionRunTask::class) {
		group = "build"
		dependsOn(copyPluginToRunDir)
		val rusherLoaderJarFile = layout.projectDirectory.file("lib/rusherhack-loader.jar").asFile
		if (!rusherLoaderJarFile.exists()) {
			throw GradleException("rusherhack-loader.jar must be copied to the lib directory!")
		}
		val rusherLoaderJarPath = rusherLoaderJarFile.absolutePath
		jvmArgs.addAll(listOf(
			"-Drusherhack.enablePlugins=true",
			"-Dfabric.addMods=$rusherLoaderJarPath"
		))
	}
	remapJar {
		archiveVersion = "$modVersion+$mcVersionRange"
	}
}

sourceSets {
	main {
		extensions.configure<ClassTokenReplacerExtension>("classTokenReplacer") {
			property("\${version}", modVersion)
		}
	}
}
