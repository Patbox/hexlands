package com.alcatrazescapee.hexlands.world;

import com.alcatrazescapee.hexlands.mixin.NoiseBasedChunkGeneratorAccessor;
import com.alcatrazescapee.hexlands.util.Hex;
import com.alcatrazescapee.hexlands.util.HexSettings;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class HexChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<HexChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(c -> c.biomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(c -> c.settings),
            HexSettings.CODEC.fieldOf("hex_settings").forGetter(c -> c.hexSettings)
    ).apply(instance, HexChunkGenerator::new));

    private final Holder<NoiseGeneratorSettings> settings;
    private final Holder<NoiseGeneratorSettings> modifiedNoiseSettings;
    private final HexSettings hexSettings;


    public HexChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, HexSettings hexSettings) {
        this.settings = settings;
        this.hexSettings = hexSettings;
        //
        var modifiedNoiseSettings = this.modifiedNoiseSettings = new LazyHolder<>(Registries.NOISE_SETTINGS, () -> HexRandomState.modifyNoiseSettings(settings.value(), hexSettings));

        super(biomeSource, modifiedNoiseSettings);

        ((NoiseBasedChunkGeneratorAccessor) this).setGlobalFluidPicker(Suppliers.memoize(() -> {
            final NoiseGeneratorSettings noiseGeneratorSettings = settings.value();
            final Aquifer.FluidStatus lavaAtNeg54 = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
            final int seaLevel = noiseGeneratorSettings.seaLevel();
            final Aquifer.FluidStatus waterAtSeaLevel = new Aquifer.FluidStatus(seaLevel, noiseGeneratorSettings.defaultFluid());

            return (x, y, z) -> y < Math.min(-54, seaLevel) ? lavaAtNeg54 : waterAtSeaLevel;
        }));
    }

    @Override
    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    @Override
    protected MapCodec<HexChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, @org.jspecify.annotations.Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        HexRandomState.modify(randomState, this.settings.value(), this.hexSettings);
        return super.buildTerrain(chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes);
    }

    @Override
    protected void buildSurface(ChunkAccess chunk, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager, Set<Holder<Biome>> possibleBiomes, MaterialRule materialRule) {
        super.buildSurface(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);

        applyAtHexBorders(chunk, randomState, noiseChunk, (cursor, placed) -> {
            // Bottom Border
            for (int y = placed.minY; y <= placed.borderMinY; y++) {
                final Block block = chunk.getBlockState(cursor.setY(y)).getBlock();
                if (block != Blocks.BEDROCK) {
                    chunk.setBlockState(cursor, placed.borderMinState);
                }
            }

            // Between Borders
            for (int y = placed.borderMinY + 1; y < placed.borderMaxY; y++) {
                chunk.setBlockState(cursor.setY(y), Blocks.AIR.defaultBlockState());
            }

            // Top Border
            for (int y = placed.borderMaxY; y <= placed.maxY; y++) {
                final Block block = chunk.getBlockState(cursor.setY(y)).getBlock();
                if (block != Blocks.BEDROCK) {
                    chunk.setBlockState(cursor, placed.borderMaxState);
                }
            }
        });
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState state) {
        HexRandomState.modify(state, settings.value(), hexSettings);
        return super.getBaseHeight(x, z, type, level, state);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState state, BlockPos pos, SamplerContext samplerContext) {
        final double hexScale = hexSettings.biomeScale();
        final double hexSize = hexSettings.hexSize() * hexScale;
        final Hex hex = Hex.blockToHex(pos.getX() * hexScale, pos.getZ() * hexScale, hexSize);
        final PlacedHex placed = placeHex(hex, state, null, pos.getY());

        result.add(String.format("Hex (%d, %d) at %s : H%d B%d-%d", hex.q(), hex.r(), placed.biome().unwrap().map(ResourceKey::identifier, e -> "[unregistered biome]"), (int) placed.preliminaryHeight, placed.borderMinY, placed.borderMaxY));
        super.addDebugScreenInfo(result, state, pos, samplerContext);
    }

    private void applyAtHexBorders(ChunkAccess chunk, RandomState state, NoiseChunk noiseChunk, ColumnApplier applier) {
        final Map<Hex, PlacedHex> cachedBiomesByHex = new HashMap<>();
        final ChunkPos chunkPos = chunk.getPos();
        final int blockX = chunkPos.getMinBlockX(), blockZ = chunkPos.getMinBlockZ();

        final double hexScale = hexSettings.biomeScale();
        final double hexSize = hexSettings.hexSize() * hexScale;
        final double hexBorder = hexSettings.hexBorderThreshold();

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; ++localX) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                final int x = blockX + localX;
                final int z = blockZ + localZ;

                final Hex hex = Hex.blockToHex(x * hexScale, z * hexScale, hexSize);
                final Hex adjacentHex = hex.adjacent(x * hexScale, z * hexScale);

                final PlacedHex placed = cachedBiomesByHex.computeIfAbsent(hex, k -> placeHex(k, state, noiseChunk, 0));
                if (hex.radius(x * hexScale, z * hexScale) >= hexBorder) {
                    final PlacedHex adjacentPlacedHex = cachedBiomesByHex.computeIfAbsent(adjacentHex, k -> placeHex(k, state, noiseChunk, 0));
                    if (placed.biome != adjacentPlacedHex.biome) {
                        cursor.setX(x).setZ(z);
                        applier.apply(cursor, placed);
                    }
                }
            }
        }
    }

    private PlacedHex placeHex(Hex hex, RandomState state, @Nullable NoiseChunk noiseChunk, int backupSurfaceY) {
        final BlockPos center = hex.center();
        final double hexScale = hexSettings.biomeScale();
        final int quartX = QuartPos.fromBlock((int) (center.getX() / hexScale));
        final int quartZ = QuartPos.fromBlock((int) (center.getZ() / hexScale));
        final NoiseSettings noiseSettings = settings.value().noiseSettings();
        //final HexRandomState hexRandomState = HexRandomState.modify(state, settings.value(), hexSettings);
        final double preliminaryHeight = state.sampleBlockValueUncached(this.modifiedNoiseSettings.value().noiseRouter().chunkSurfaceLevel(), (int) (center.getX() / hexScale), 0, (int) (center.getZ() / hexScale));
        final Holder<Biome> biome = biomeSource.createCachingResolver(state).getNoiseBiome(quartX, QuartPos.fromBlock((int) preliminaryHeight), quartZ);
        final RandomSource random = new XoroshiroRandomSource(hex.q() * 178293412341L, hex.r() * 7520351231L);

        final int minY = noiseSettings.minY();
        final int maxY = noiseSettings.minY() + noiseSettings.height() - 1;

        final int borderMinY = hexSettings.bottomBorder()
                .map(border -> border.sample(random))
                .orElse(minY - 1);

        final int borderMaxY = hexSettings.topBorder()
                .map(border -> border.sample(random))
                .orElse(maxY + 1);

        final BlockState minBorderState = hexSettings.bottomBorder().map(HexSettings.BorderSettings::state).orElse(Blocks.AIR.defaultBlockState());
        final BlockState maxBorderState = hexSettings.topBorder().map(HexSettings.BorderSettings::state).orElse(Blocks.AIR.defaultBlockState());

        return new PlacedHex(hex, biome, preliminaryHeight, minY, maxY, borderMinY, borderMaxY, minBorderState, maxBorderState);
    }

    /*private NoiseChunk getOrCreateNoiseChunk(ChunkAccess chunk, RandomState state, StructureManager structureManager, Blender blender) {
        return chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, Beardifier.forStructuresInChunk(structureManager, c.getPos()), settings.value(), stupidMojangGlobalFluidPicker.get(), blender));
    }
     */

    record PlacedHex(Hex hex, Holder<Biome> biome, double preliminaryHeight, int minY, int maxY, int borderMinY,
                     int borderMaxY, BlockState borderMinState, BlockState borderMaxState) {
    }

    @FunctionalInterface
    interface ColumnApplier {
        void apply(BlockPos.MutableBlockPos cursor, PlacedHex placed);
    }


    private static final class LazyHolder<T> extends Holder.Reference<T> {
        private final Supplier<T> supplier;
        private boolean bind;

        LazyHolder(ResourceKey<Registry<T>> resourceKey, Supplier<T> supplier) {
            super(Type.STAND_ALONE, new HolderGetter<T>() {
                @Override
                public Optional<Reference<T>> get(ResourceKey<T> id) {
                    return Optional.empty();
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    return Optional.empty();
                }
            }, ResourceKey.create(resourceKey, Identifier.fromNamespaceAndPath("hexlands", "hack")), null);

            this.supplier = supplier;
        }


        @Override
        public T value() {
            if (!this.bind) {
                this.bindValue(this.supplier.get());
                this.bind = true;
            }
            return super.value();
        }
    }
}
