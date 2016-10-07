package net.gegy1000.blocksystems.client.blocksystem;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.client.blocksystem.listener.ClientBlockSystemListener;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;

import javax.vecmath.Point3d;
import java.util.Random;

public class BlockSystemClient extends BlockSystem {
    private Minecraft mc = Minecraft.getMinecraft();
    private MultiplayerChunkCacheBlockSystem chunkProviderClient;

    public BlockSystemClient(World mainWorld, int id) {
        super(mainWorld, id, null);
    }

    @Override
    public void initializeBlockSystem(MinecraftServer server) {
        this.addEventListener(new ClientBlockSystemListener(this));
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        this.chunkProviderClient = new MultiplayerChunkCacheBlockSystem(this);
        return this.chunkProviderClient;
    }

    @Override
    public void tick() {
        super.tick();
        this.chunkProviderClient.unloadQueuedChunks();
        EntityPlayerSP player = this.mc.thePlayer;
        if (player != null) {
            Point3d untransformedPosition = this.getUntransformedPosition(new Point3d(player.posX, player.posY, player.posZ));
            this.runDisplayTicks((int) untransformedPosition.x, (int) untransformedPosition.y, (int) untransformedPosition.z);
        }
    }

    @Override
    public void playSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean distanceDelay) {
        Point3d transformed = this.getTransformedPosition(new Point3d(x, y, z));
        x = transformed.getX();
        y = transformed.getY();
        z = transformed.getZ();
        double distance = this.mc.getRenderViewEntity().getDistanceSq(x, y, z);
        PositionedSoundRecord positionedSound = new PositionedSoundRecord(sound, category, volume, pitch, (float) x, (float) y, (float) z);
        if (distanceDelay && distance > 100.0D) {
            double delay = Math.sqrt(distance) / 40.0D;
            this.mc.getSoundHandler().playDelayedSound(positionedSound, (int) (delay * 20.0D));
        } else {
            this.mc.getSoundHandler().playSound(positionedSound);
        }
    }

    @Override
    public void playSound(EntityPlayer player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        if (this.mc.getRenderViewEntity() == player) {
            this.playSound(x, y, z, sound, category, volume, pitch, false);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos) {
        return 15;
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return allowEmpty || !this.getChunkProvider().provideChunk(x, z).isEmpty();
    }

    public void loadChunkAction(int chunkX, int chunkZ, boolean loadChunk) {
        if (loadChunk) {
            this.chunkProviderClient.loadChunk(chunkX, chunkZ);
        } else {
            this.chunkProviderClient.unloadChunk(chunkX, chunkZ);
            this.markBlockRangeForRenderUpdate(chunkX << 4, 0, chunkZ << 4, (chunkX << 4) + 15, 256, (chunkZ << 4) + 15);
        }
    }

    public void runDisplayTicks(int posX, int posY, int posZ) {
        Random random = new Random();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 667; i++) {
            this.runDisplayTicksArea(posX, posY, posZ, 16, random, pos);
            this.runDisplayTicksArea(posX, posY, posZ, 32, random, pos);
        }
    }

    public void runDisplayTicksArea(int posX, int posY, int posZ, int range, Random random, BlockPos.MutableBlockPos pos) {
        int x = posX + this.rand.nextInt(range) - this.rand.nextInt(range);
        int y = posY + this.rand.nextInt(range) - this.rand.nextInt(range);
        int z = posZ + this.rand.nextInt(range) - this.rand.nextInt(range);
        pos.setPos(x, y, z);
        IBlockState state = this.getBlockState(pos);
        state.getBlock().randomDisplayTick(state, this, pos, random);
    }
}
