package com.alcatrazescapee.hexlands.platform;

import net.minecraft.world.level.biome.Climate;

public interface XPlatform {
    XPlatform INSTANCE = new FabricPlatform();

    /** Fabric does a stupid thing and sets a cached seed on `Climate.Sampler`, and then blows up if it's not there... how annoying */
    default void copyFabricCachedClimateSamplerSeed(Climate.Sampler from, Climate.Sampler to) {
    }
}
