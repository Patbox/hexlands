plugins {
    id("net.neoforged.moddev") version "2.0.110"
}

val modId: String by extra
val modGroup: String by extra
val minecraftVersion: String by extra
val neoForgeVersion: String by extra
val parchmentVersion: String by extra
val parchmentMinecraftVersion: String by extra

dependencies {
    compileOnly(project(":Common"))
}

base {
    archivesName.set(modId)
}

neoForge {
    version = neoForgeVersion

    // Use the access transformer from the :Common project

    val at = project(":Common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion.set(parchmentMinecraftVersion)
        mappingsVersion.set(parchmentVersion)
    }

    runs {
        register("client") { client() }
        register("server") { server() }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks {
    named<JavaCompile>("compileJava") { source(project(":Common").sourceSets.main.get().allSource) }
    named<ProcessResources>("processResources") { from(project(":Common").sourceSets.main.get().resources) }
}
