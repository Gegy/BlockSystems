package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Random;

public class ChunkPartitionHandler {
    public static ChunkPos generateValidPartitionPosition(World world, BlockSystem blockSystem) {
        BlockSystemSavedData data = BlockSystemSavedData.get(world);
        int attempts = 0;
        while (attempts < 100) {
            ChunkPos position = ChunkPartitionHandler.generatePartitionPosition();
            if (!data.hasPartition(position)) {
                data.addPartition(position, blockSystem);
                return position;
            }
            attempts++;
        }
        return null;
    }

    public static ChunkPos generatePartitionPosition() {
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
            return new ChunkPos(x, z);
        } else {
            return new ChunkPos(z, x);
        }
    }
}
