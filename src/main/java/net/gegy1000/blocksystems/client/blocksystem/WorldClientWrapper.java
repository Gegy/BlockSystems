package net.gegy1000.blocksystems.client.blocksystem;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;

public class WorldClientWrapper extends WorldClient {
    private final BlockSystem blockSystem;

    public WorldClientWrapper(BlockSystem blockSystem) {
        super(null, new WorldSettings(blockSystem.getParentWorld().getWorldInfo()), blockSystem.provider.getDimension(), blockSystem.getParentWorld().getDifficulty(), blockSystem.profiler);
        this.blockSystem = blockSystem;
    }

    @Override
    public void playSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean distanceDelay) {
        this.blockSystem.playSound(x, y, z, sound, category, volume, pitch, distanceDelay);
    }

    @Override
    public void playRecord(BlockPos pos, SoundEvent sound) {
        this.blockSystem.playRecord(pos, sound);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        this.blockSystem.spawnParticle(particleType, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void spawnParticle(EnumParticleTypes particleType, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        this.blockSystem.spawnParticle(particleType, ignoreRange, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
        return this.blockSystem.setBlockState(pos, state, flags);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return this.blockSystem.getBlockState(pos);
    }
}
