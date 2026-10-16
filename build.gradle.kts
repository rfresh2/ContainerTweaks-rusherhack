import net.fabricmc.loom.task.prod.ClientProductionRunTask
import net.raphimc.classtokenreplacer.extension.ClassTokenReplacerExtension

plugins {
	id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
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

val targetJavaVersion = 25
tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = targetJavaVersion
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

val rusherhackApi = configurations.create("rusherhackApi") {
	isCanBeResolved = true
}

configurations.compileOnly {
	extendsFrom(rusherhackApi)
}

repositories {
	mavenCentral()
	maven("https://maven.rusherhack.org/snapshots")
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:0.19.5")
	rusherhackApi("org.rusherhack:rusherhack-api:$minecraftVersion-SNAPSHOT")
}

loom {
	// apply accesswidener from rusherhack-api
	for (file in zipTree(rusherhackApi.singleFile)) {
		if (file.name == "rusherhack.accesswidener") {
			accessWidenerPath = file
		}
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
		dependsOn(jar)
		from(jar.map { it.outputs })
		into(file("run/rusherhack/plugins"))
	}
	register("runPlugin", ClientProductionRunTask::class) {
		group = "build"
		dependsOn(copyPluginToRunDir)
		val rusherLoaderJarFile = layout.projectDirectory.file("lib/rusherhack-loader.jar").asFile
		if (!rusherLoaderJarFile.exists()) {
			throw GradleException("rusherhack-loader.jar must be copied to the lib directory!")
		}
		mods.from(layout.projectDirectory.dir("lib"))
		jvmArgs.addAll(listOf(
			"-Drusherhack.enablePlugins=true"
		))
	}
	jar {
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
