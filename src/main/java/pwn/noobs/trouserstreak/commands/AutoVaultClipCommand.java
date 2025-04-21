package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoVaultClipCommand extends Command {
    public AutoVaultClipCommand() {
        super("autovaultclip", "Ultimate vertical clip with vault bypass and zero fall damage via segmentation.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            error("Choose Up, Down or Highest");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("up").executes(ctx -> handleClip(getGapY(1))));
        builder.then(literal("down").executes(ctx -> handleClip(getGapY(-1))));
        builder.then(literal("highest").executes(ctx -> handleClip(getHighestY())));
    }

    // Main handler that executes the clip at the desired Y
    private int handleClip(double targetY) {
        ClientPlayerEntity player = mc.player;
        assert player != null;

        double startY = player.getY();
        if (targetY >= startY) {
            // Up or level: direct teleport
            performDirectClip(targetY);
        } else {
            // Down: segment into safe falls
            performSegmentedClip(startY, targetY);
        }

        return SINGLE_SUCCESS;
    }

    // Splits the downward drop into small safe segments (<3 blocks)
    private void performSegmentedClip(double startY, double targetY) {
        double segmentSize = 2.8; // safe max fall
        double distance = startY - targetY;
        int parts = (int) Math.ceil(distance / segmentSize);

        for (int i = 1; i <= parts; i++) {
            double nextY = startY - Math.min(segmentSize * i, distance);
            performDirectClip(nextY);
        }
    }

    // Teleports player (and vehicle) and resets fall state
    private void performDirectClip(double y) {
        ClientPlayerEntity player = mc.player;
        assert player != null;

        double x = player.getX();
        double z = player.getZ();

        // Vehicle bypass
        if (player.hasVehicle()) {
            Entity veh = player.getVehicle();
            for (int i = 0; i < 19; i++) {
                mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(veh));
            }
            veh.setPosition(x, y, z);
        }

        // Teleport client
        player.updatePosition(x, y, z);
        player.fallDistance = 0;
        player.setVelocity(0, 0, 0);

        // Send on-ground packet to server
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, player.horizontalCollision)
        );

        // Optional sprint packet to finalize state
        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING)
        );
    }

    // Finds the first safe gap for up/down scans (direction=1 for up, -1 for down)
    private double getGapY(int direction) {
        ClientPlayerEntity player = mc.player;
        assert player != null;
        BlockPos pos = player.getBlockPos();
        int range = 199;

        if (direction > 0) {
            for (int i = 0; i < range; i++) {
                BlockPos a = pos.add(0, i + 2, 0);
                BlockPos b = pos.add(0, i + 3, 0);
                if (isSafe(a) && isSafe(b)) return a.getY();
            }
        } else {
            for (int i = -1; i > -range; i--) {
                BlockPos a = pos.add(0, i, 0);
                BlockPos b = pos.add(0, i - 1, 0);
                if (isSafe(a) && isSafe(b)) return b.getY();
            }
        }
        error("No gap found to vclip into");
        return player.getY();
    }

    // Finds highest teleport Y above
    private double getHighestY() {
        ClientPlayerEntity player = mc.player;
        assert player != null;
        BlockPos pos = player.getBlockPos();

        for (int i = 199; i > 0; i--) {
            BlockPos below = pos.add(0, i, 0);
            BlockPos above = below.up();
            if (!isSafe(below) || !mc.world.getFluidState(below).isEmpty()) return above.getY();
        }
        error("No blocks above you found!");
        return player.getY();
    }

    // Safety check: gap must be two-block tall of air
    private boolean isSafe(BlockPos p) {
        return mc.world.getBlockState(p).isReplaceable()
            && mc.world.getFluidState(p).isEmpty()
            && !mc.world.getBlockState(p).isOf(Blocks.POWDER_SNOW);
    }
}
