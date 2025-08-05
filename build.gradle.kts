import org.gradle.internal.extensions.stdlib.toDefaultLowerCase

plugins {
    id("java")
    id("com.modrinth.minotaur") version "2.+"
}

val modName: String by extra
val modAuthor: String by extra
val modId: String by extra
val modVersion: String by extra
val modGroup: String by extra
val modIssueUrl: String by extra
val modHomeUrl: String by extra
val modDescription: String by extra
val modJavaVersion: String by extra

val minecraftVersion: String by extra
val minecraftVersionRange: String by extra
val neoForgeVersion: String by extra
val neoForgeVersionRange: String by extra
val parchmentVersion: String by extra
val fabricVersion: String by extra
val fabricLoaderVersion: String by extra


subprojects {
    version = modVersion + "+" + minecraftVersion + (if (project.name == "NeoForge") "-legacy"  else "")
    group = modGroup

    repositories {
        exclusiveContent {
            forRepository { maven("https://maven.parchmentmc.org/") }
            filter { includeGroup("org.parchmentmc.data") }
        }
    }

    tasks {
        withType<ProcessResources> {
            filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta", "fabric.mod.json")) {
                expand(mapOf(
                    "modName" to modName,
                    "modAuthor" to modAuthor,
                    "modAuthorFabric" to "\"" + modAuthor.split(", ").joinToString("\", \"") + "\"",
                    "modId" to modId,
                    "modGroup" to modGroup,
                    "modIssueUrl" to modIssueUrl,
                    "modHomeUrl" to modHomeUrl,
                    "modDescription" to modDescription,
                    "modJavaVersion" to modJavaVersion,
                    "modVersion" to version.toString(),

                    "minecraftVersion" to minecraftVersion,
                    "minecraftVersionRange" to minecraftVersionRange,
                    "neoForgeVersion" to neoForgeVersion,
                    "neoForgeVersionRange" to neoForgeVersionRange,
                    "fabricVersion" to fabricVersion,
                    "fabricLoaderVersion" to fabricLoaderVersion,
                ))
            }
        }
    }

    if (System.getenv("MODRINTH") != null && project.name != "Common") {
        modrinth {
            token = System.getenv("MODRINTH")
            projectId = "3TMQS50Y"// The ID of your modrinth project, slugs will not work.
            versionNumber = "" + version // The version of the mod to upload.
            versionType = if (project.name == "Fabric") "release" else "alpha"
            uploadFile = if (project.name == "Fabric") tasks.get("remapJar") else tasks.jar
            gameVersions.add(minecraftVersion)
            changelog = System.getenv("CHANGELOG")
            loaders.add(project.name.toDefaultLowerCase())
        }
    }
}