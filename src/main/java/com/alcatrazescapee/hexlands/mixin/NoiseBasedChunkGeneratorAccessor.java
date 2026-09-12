package com.alcatrazescapee.hexlands.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
    @Mutable
    @Accessor
    void setGlobalFluidPicker(Supplier<Aquifer.FluidPicker> globalFluidPicker);
}
