package net.gegy1000.blocksystems.server.core;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.ClientProxy;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.HookedChunk;
import net.gegy1000.blocksystems.server.world.TransformedSubWorld;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import javax.vecmath.Point3d;
import java.util.HashMap;
import java.util.Map;

public class BlockSystemHooks {
    private static final Map<World, HookedChunk> HOOKED_CHUNKS = new HashMap<>();

    public static void onWorldLoad(World world) {
        HOOKED_CHUNKS.put(world, new HookedChunk(world, 0, 0));
    }

    public static void onWorldUnload(World world) {
        HOOKED_CHUNKS.remove(world);
    }

    public static void transformEffect(Particle particle) {
        TransformedSubWorld transforming = TransformedSubWorld.TRANSFORMING.get();
        if (transforming != null) {
            Point3d transformed = transforming.getTransform().toGlobalPos(new Point3d(particle.posX, particle.posY, particle.posZ));
            particle.setPosition(transformed.getX(), transformed.getY(), transformed.getZ());
            particle.prevPosX = transformed.getX();
            particle.prevPosY = transformed.getY();
            particle.prevPosZ = transformed.getZ();
            Vec3d transformedVelocity = transforming.getTransform().toGlobalVector(new Vec3d(particle.motionX, particle.motionY, particle.motionZ));
            particle.motionX = transformedVelocity.x;
            particle.motionY = transformedVelocity.y;
            particle.motionZ = transformedVelocity.z;
        }
    }

    public static World getMainWorld(World world) {
        if (world instanceof BlockSystem) {
            return ((BlockSystem) world).getParentWorld();
        }
        return world;
    }

    public static boolean checkBlockAccess(World world, BlockPos pos) {
        if (world instanceof BlockSystem) {
            return false;
        }
        boolean outsideWorldHeight = pos.getY() < 0 || pos.getY() >= 256;
        boolean partitionSpace = pos.getX() <= -29999999 || pos.getX() >= 29999984 || pos.getZ() <= -29999999 || pos.getZ() >= 29999984;
        return outsideWorldHeight || partitionSpace && !BlockSystemWorldAccess.canAccess(world);
    }

    public static boolean checkChunkAccess(World world, int x, int z, boolean override) {
        if (world instanceof BlockSystem) {
            return false;
        }
        boolean partitionSpace = x <= -1875000 || x > 1874999 || z <= -1875000 || z > 1874999;
        return partitionSpace && (!BlockSystemWorldAccess.canAccess(world) || override);
    }

    public static Chunk getChunk(World world, int x, int z) {
        if (BlockSystemHooks.checkBlockAccess(world, new BlockPos(x << 4, 0, z << 4))) {
            return HOOKED_CHUNKS.get(world);
        }
        return world.getChunkProvider().provideChunk(x, z);
    }

    public static void initChunkPrimer(World world, Chunk chunk, int x, int z) {
        if (BlockSystemHooks.checkChunkAccess(world, x, z, true)) {
            ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
            for (int i = 0; i < storage.length; i++) {
                storage[i] = Chunk.NULL_BLOCK_STORAGE;
            }
        }
    }

    public static void getMouseOver() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null && mc.player != null) {
            Vec3d eyePos = mc.player.getPositionEyes(1.0F);
            BlockSystem blockSystem = ClientProxy.getMouseOverSystem();
            RayTraceResult mousedOver = ClientProxy.getMouseOver();
            if (blockSystem != null && mousedOver != null) {
                double length = mc.objectMouseOver.hitVec.subtract(eyePos).lengthSquared();
                Vec3d blockSystemEyePos = blockSystem.getTransform().toLocalPos(eyePos);
                if (mousedOver.hitVec.subtract(blockSystemEyePos).lengthSquared() < length) {
                    mc.objectMouseOver = new RayTraceResult(RayTraceResult.Type.MISS, blockSystemEyePos, EnumFacing.DOWN, mousedOver.getBlockPos());
                }
            }
        }
    }

    public static boolean rightClickMouse() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if ((mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit == RayTraceResult.Type.MISS) && player != null) {
            BlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(mc.world);
            BlockSystemInteractionHandler interactionHandler = handler.getInteractionHandler(player);
            if (interactionHandler == null) {
                return false;
            }

            BlockSystem blockSystem = ClientProxy.getMouseOverSystem();
            RayTraceResult mousedOver = ClientProxy.getMouseOver();
            if (blockSystem == null || mousedOver == null || mousedOver.typeOfHit != RayTraceResult.Type.BLOCK) {
                return false;
            }

            BlockPos pos = mousedOver.getBlockPos();
            IBlockState state = blockSystem.getBlockState(pos);
            for (EnumHand hand : EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (state.getMaterial() != Material.AIR) {
                    int previousCount = stack.getCount();

                    float hitX = (float) (mousedOver.hitVec.x - pos.getX());
                    float hitY = (float) (mousedOver.hitVec.y - pos.getY());
                    float hitZ = (float) (mousedOver.hitVec.z - pos.getZ());

                    EnumActionResult result = interactionHandler.handleInteract(blockSystem, pos, hand, mousedOver.sideHit, hitX, hitY, hitZ);
                    if (result == EnumActionResult.SUCCESS) {
                        player.swingArm(hand);
                        if (!stack.isEmpty() && (stack.getCount() != previousCount || mc.player.isCreative())) {
                            mc.entityRenderer.itemRenderer.resetEquippedProgress(hand);
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static BlockPos chunkWorkerGetPosition(ChunkCompileTaskGenerator generator, BlockPos pos) {
        World world = generator.getRenderChunk().getWorld();
        if (world instanceof BlockSystem) {
            BlockSystem blockSystem = (BlockSystem) world;
            return blockSystem.getTransform().toLocalPos(pos);
        }
        return pos;
    }

    public static World setWorld(TileEntity entity, World world) {
        BlockPos pos = entity.getPos();
        if (pos != BlockPos.ORIGIN) {
            return BlockSystemHooks.getBlockSystemWorld(world, new ChunkPos(pos));
        }
        return world;
    }

    public static BlockPos setPos(TileEntity entity, BlockPos pos) {
        World world = entity.getWorld();
        if (world != null) {
            entity.setWorld(BlockSystemHooks.getBlockSystemWorld(world, new ChunkPos(pos)));
        }
        return pos;
    }

    private static World getBlockSystemWorld(World world, ChunkPos pos) {
        if (world instanceof WorldServer) {
            BlockSystemSavedData data = BlockSystemSavedData.get((WorldServer) world, false);
            BlockSystem blockSystem = data.getBlockSystem(pos);
            if (blockSystem != null) {
                return blockSystem;
            }
        }
        return world;
    }
}
