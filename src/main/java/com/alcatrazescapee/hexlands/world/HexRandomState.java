package com.alcatrazescapee.hexlands.world;

import com.alcatrazescapee.hexlands.mixin.RandomStateAccessor;
import com.alcatrazescapee.hexlands.util.Hex;
import com.alcatrazescapee.hexlands.util.HexSettings;
import com.google.common.base.Supplier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;

import java.util.concurrent.ExecutionException;

public record HexRandomState(RandomState state, NoiseRouter hexRouter) {
    private static final Cache<RandomState, HexRandomState> RANDOM_STATE_EXTENSIONS = CacheBuilder.newBuilder()
            .concurrencyLevel(4)
            .weakKeys()
            .build();

    public static NoiseGeneratorSettings modifyNoiseSettings(NoiseGeneratorSettings value, HexSettings hexSettings) {
        final DfRewriteRule rewriteRule[] = new DfRewriteRule[1];

        rewriteRule[0] = f -> {
            if (isNoiseDensityFunction(f)) {
                return sampleHexRelative(hexSettings, f);
            }
            return f.rewriteChildren(rewriteRule[0]);
        };


        final NoiseRouter router = value.noiseRouter();
        final NoiseRouter hexRouter = new NoiseRouter(
                sampleHexCenter(hexSettings, router.temperature()),
                sampleHexCenter(hexSettings, router.vegetation()),
                sampleHexCenter(hexSettings, router.continents()),
                sampleHexCenter(hexSettings, router.erosion()),
                sampleHexCenter(hexSettings, router.depth()),
                sampleHexCenter(hexSettings, router.ridges()),
                rewriteRule[0].rewrite(router.chunkSurfaceLevel()),
                rewriteRule[0].rewrite(router.finalDensity())
        );

        return new NoiseGeneratorSettings(
                value.noiseSettings(),
                value.defaultBlock(),
                value.defaultFluid(),
                hexRouter,
                value.materialRule(),
                value.spawnTarget(),
                value.seaLevel(),
                value.disableMobGeneration(),
                value.aquifers(),
                value.useLegacyRandomSource(),
                value.debugFunctions()
        );
    }

    public static HexRandomState modify(RandomState state, NoiseGeneratorSettings settings, HexSettings hexSettings) {
        try {
            return RANDOM_STATE_EXTENSIONS.get(state, () -> {
                final DfRewriteRule rewriteRule[] = new DfRewriteRule[1];

                rewriteRule[0] = f -> {
                    if (isNoiseDensityFunction(f)) {
                        return sampleHexRelative(hexSettings, f);
                    }
                    return f.rewriteChildren(rewriteRule[0]);
                };



                final NoiseRouter router = settings.noiseRouter();
                final NoiseRouter hexRouter = new NoiseRouter(
                        sampleHexCenter(hexSettings, router.temperature()),
                        sampleHexCenter(hexSettings, router.vegetation()),
                        sampleHexCenter(hexSettings, router.continents()),
                        sampleHexCenter(hexSettings, router.erosion()),
                        sampleHexCenter(hexSettings, router.depth()),
                        sampleHexCenter(hexSettings, router.ridges()),
                        router.chunkSurfaceLevel().rewriteChildren(rewriteRule[0]),
                        router.finalDensity().rewriteChildren(rewriteRule[0])
                );

                final RandomStateAccessor mutableState = (RandomStateAccessor) (Object) state;

                mutableState.setRouter(hexRouter);

                return new HexRandomState(state, hexRouter);
            });
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to inject HexRandomState into RandomState", e);
        }
    }

    private static boolean isNoiseDensityFunction(DensityFunction f) {
        return f instanceof NoiseFunction;// || f instanceof ShiftFunction || f instanceof ShiftedNoiseFunction;
    }

    private static DensityFunction sampleHexCenter(HexSettings hexSettings, DensityFunction function) {
        return new PointMapped(function, function::range, hexSettings, false);
    }

    private static DensityFunction sampleHexRelative(HexSettings hexSettings, DensityFunction function) {
        return new PointMapped(function, function::range, hexSettings, true);
    }

    record PointMapped(DensityFunction wrapped, Supplier<Interval> rangeSup, HexSettings settings, boolean relative) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            var compiled = wrapped.compileSampler(context);
            return new DensitySampler() {
                @Override
                public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
                    DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
                }

                @Override
                public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                    final double scale = settings.biomeScale();
                    final double size = settings.hexSize();
                    final Hex hex = Hex.blockToHex(blockX * scale, blockZ * scale, size * scale);
                    final BlockPos center = hex.center();

                    final double deltaX = relative ? blockX - center.getX() / scale : 0;
                    final double deltaZ = relative ? blockZ - center.getZ() / scale : 0;

                    return compiled.sampleValue(context, center.getX() + (int) deltaX, blockY, center.getZ() + (int) deltaZ);
                }
            };
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new PointMapped(rule.rewrite(wrapped.rewriteChildren(rule)), this.rangeSup, settings, relative);
        }

        @Override
        public Interval range() {
            return this.rangeSup.get();
        }


        @Override
        public @Axes int domainAxes() {
            return ALL_AXES;
        }

        @Override
        public MapCodec<? extends DensityFunction> codec() {
            return MapCodec.unit(this);
        }
    }
}
