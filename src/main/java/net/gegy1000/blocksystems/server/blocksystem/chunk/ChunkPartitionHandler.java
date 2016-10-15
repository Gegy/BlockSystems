package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class ChunkPartitionHandler {
    public static BlockPos generateValidPartitionPosition(World world) {
        BlockSystemSavedData data = BlockSystemSavedData.get(world);
        int attempts = 0;
        while (attempts < 100) {
            BlockPos position = ChunkPartitionHandler.generatePartitionPosition();
            if (!data.hasPartition(position)) {
                data.addPartition(position);
                return position;
            }
            attempts++;
        }
        return null;
    }

    public static BlockPos generatePartitionPosition() {
        int size = 1875000;
        Random random = new Random();
        int x = random.nextInt(size * 2) - size;
        int z = (random.nextBoolean() ? 1 : -1) * size;
        if (x > size - 1) {
            x = size - 1;
        }
        if (z > size - 1) {
            z = size - 1;
        }
        if (random.nextBoolean()) {
            return new BlockPos(x, 0, z);
        } else {
            return new BlockPos(z, 0, x);
        }
    }
}
