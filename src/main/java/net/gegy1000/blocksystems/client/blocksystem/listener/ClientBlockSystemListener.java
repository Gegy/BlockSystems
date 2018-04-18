package net.gegy1000.blocksystems.client.blocksystem.listener;

import net.gegy1000.blocksystems.client.blocksystem.WorldClientWrapper;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ReportedException;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;

public class ClientBlockSystemListener implements IWorldEventListener {
    private Minecraft mc = Minecraft.getMinecraft();

    private final BlockSystem blockSystem;
    private final WorldClientWrapper wrapper;

    public ClientBlockSystemListener(BlockSystem blockSystem) {
        this.blockSystem = blockSystem;
        this.wrapper = new WorldClientWrapper(blockSystem);
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        this.spawnParticle(id, ignoreRange, false, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean minParticles, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        try {
            this.spawnEffectParticle(id, ignoreRange, minParticles, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        } catch (Throwable throwable) {
            CrashReport crash = CrashReport.makeCrashReport(throwable, "Exception while adding particle");
            CrashReportCategory category = crash.makeCategory("Particle being added");
            category.addCrashSection("ID", id);
            category.addCrashSection("Parameters", parameters);
            category.addDetail("Position", () -> CrashReportCategory.getCoordinateInfo(x, y, z));
            throw new ReportedException(crash);
        }
    }

    private Particle spawnEffectParticle(int particleID, boolean ignoreRange, boolean minParticles, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Entity entity = this.mc.getRenderViewEntity();
        if (this.mc != null && entity != null && this.mc.effectRenderer != null) {
            int particleLevel = this.getParticleLevel(minParticles);
            double deltaX = entity.posX - xCoord;
            double deltaY = entity.posY - yCoord;
            double deltaZ = entity.posZ - zCoord;
            if (ignoreRange) {
                return this.mc.effectRenderer.spawnEffectParticle(particleID, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
            } else {
                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > 1024.0) {
                    return null;
                } else {
                    if (particleLevel > 1) {
                        return null;
                    } else {
                        return this.mc.effectRenderer.spawnEffectParticle(particleID, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
                    }
                }
            }
        } else {
            return null;
        }
    }

    private int getParticleLevel(boolean minParticle) {
        int level = this.mc.gameSettings.particleSetting;
        if (minParticle && level == 2 && this.blockSystem.rand.nextInt(10) == 0) {
            level = 1;
        }
        if (level == 1 && this.blockSystem.rand.nextInt(3) == 0) {
            level = 2;
        }
        return level;
    }

    @Override
    public void onEntityAdded(Entity entity) {
    }

    @Override
    public void onEntityRemoved(Entity entity) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
        RenderGlobal renderGlobal = this.mc.renderGlobal;
        WorldClient previousWorld = renderGlobal.world;
        renderGlobal.world = this.wrapper;
        renderGlobal.playEvent(player, type, pos, data);
        renderGlobal.world = previousWorld;
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }

    public static ClientBlockSystemListener get(BlockSystem world) {
        for (IWorldEventListener listener : world.getListeners()) {
            if (listener instanceof ClientBlockSystemListener) {
                return (ClientBlockSystemListener) listener;
            }
        }
        return null;
    }
}
