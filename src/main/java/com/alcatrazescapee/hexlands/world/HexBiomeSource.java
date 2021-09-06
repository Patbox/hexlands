/*
 * Part of the HexLands mod.
 * Licensed under MIT. See the project LICENSE.txt for details.
 */

package com.alcatrazescapee.hexlands.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryLookupCodec;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;

import com.alcatrazescapee.hexlands.util.Hex;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class HexBiomeSource extends BiomeProvider
{
    public static final Codec<HexBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BiomeProvider.CODEC.fieldOf("biome_source").forGetter(c -> c.parent),
        RegistryLookupCodec.create(Registry.BIOME_REGISTRY).forGetter(c -> c.biomeRegistry),
        HexSettings.CODEC.forGetter(c -> c.settings)
    ).apply(instance, HexBiomeSource::new));

    protected final BiomeProvider parent;
    protected final Registry<Biome> biomeRegistry;
    protected final HexSettings settings;

    public HexBiomeSource(BiomeProvider parent, Registry<Biome> biomeRegistry, HexSettings settings)
    {
        super(parent.possibleBiomes());

        this.parent = parent;
        this.biomeRegistry = biomeRegistry;
        this.settings = settings;
    }

    @Override
    public Biome getNoiseBiome(int x, int y, int z)
    {
        return getHexBiome(x << 2, y, z << 2);
    }

    @Override
    protected Codec<? extends BiomeProvider> codec()
    {
        return CODEC;
    }

    @Override
    public HexBiomeSource withSeed(long seed)
    {
        return new HexBiomeSource(parent.withSeed(seed), biomeRegistry, settings);
    }

    public HexSettings hexSettings()
    {
        return settings;
    }

    public Biome getHexBiome(int x, int y, int z)
    {
        final double scale = settings.biomeScale();
        final double size = settings.hexSize() * scale;
        final Hex hex = Hex.blockToHex(x * scale, z * scale, size);
        final BlockPos pos = hex.center();
        return getParentNoiseBiome(pos.getX(), y, pos.getZ());
    }

    public Biome getHexBiome(Hex hex)
    {
        final BlockPos pos = hex.center();
        return getParentNoiseBiome(pos.getX(), 0, pos.getZ());
    }

    protected Biome getParentNoiseBiome(int x, int y, int z)
    {
        return parent.getNoiseBiome(x, y, z);
    }
}
