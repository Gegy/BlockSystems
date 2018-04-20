package net.gegy1000.blocksystems.server.block;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.api.DefaultRenderedItem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.util.QuatRotation;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockSystemBlock extends Block implements DefaultRenderedItem {
    public BlockSystemBlock() {
        super(Material.IRON);
        this.setHardness(3.0F);
        this.setHarvestLevel("pickaxe", 1);
        this.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        IBlockState state = super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer);
        if (!(world instanceof BlockSystem)) {
            if (!world.isRemote) {
                ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
                BlockSystem blockSystem = BlockSystems.PROXY.createBlockSystem(world, BlockSystem.nextID++);

                QuatRotation rotation = new QuatRotation();
                rotation.rotate(180.0 - placer.rotationYaw, 0.0, 1.0, 0.0);
                blockSystem.setPositionAndRotation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rotation);

                handler.addBlockSystem(blockSystem);

                blockSystem.setBlockState(BlockPos.ORIGIN, state, 3);
            }
            return Blocks.AIR.getDefaultState();
        }
        return state;
    }
}
