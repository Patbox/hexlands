plugins {
    id("net.neoforged.moddev") version "2.0.106"
}

val parchmentMinecraftVersion: String by extra
val parchmentVersion: String by extra
val commonNeoFormVersion: String by extra

dependencies {
    compileOnly(group = "org.spongepowered", name = "mixin", version = "0.8.5")
    // fabric and neoforge both bundle mixinextras, so it is safe to use it in common
    compileOnly(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.3.5")
    annotationProcessor(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.3.5")
}

neoForge {
    neoFormVersion = commonNeoFormVersion
    // Automatically enable AccessTransformers if the file exists
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }

    parchment {
        minecraftVersion.set(parchmentMinecraftVersion)
        mappingsVersion.set(parchmentVersion)
    }
}

