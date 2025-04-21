package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoVaultClipCommand extends Command {
    public AutoVaultClipCommand() {
        super("autovaultclip", "Clips vertically with vault bypass and zero fall damage. Works best on Paper/Spigot.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            error("Choose Up, Down or Highest");
            return SINGLE_SUCCESS;
        });

        // UP
        builder.then(literal("up").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;

            for (int i = 0; i < 199; i++) {
                BlockPos gap1 = player.getBlockPos().add(0, i + 2, 0);
                BlockPos gap2 = player.getBlockPos().add(0, i + 3, 0);
                if (isSafe(gap1) && isSafe(gap2)) {
                    teleportAndReset(gap1.getY());
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // DOWN
        builder.then(literal("down").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;

            for (int i = -1; i > -199; i--) {
                BlockPos gap1 = player.getBlockPos().add(0, i, 0);
                BlockPos gap2 = player.getBlockPos().add(0, i - 1, 0);
                if (isSafe(gap1) && isSafe(gap2)) {
                    teleportAndReset(gap2.getY());
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // HIGHEST
        builder.then(literal("highest").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;

            for (int i = 199; i > 0; i--) {
                BlockPos below = player.getBlockPos().add(0, i, 0);
                BlockPos above = below.up(1);
                if (!isSafe(below) || !mc.world.getFluidState(below).isEmpty()) {
                    teleportAndReset(above.getY());
                    return SINGLE_SUCCESS;
                }
            }
            error("No blocks above you found!");
            return SINGLE_SUCCESS;
        }));
    }

    // Teleport player (and vehicle) to targetY, then send position packets to nullify fall damage
    private void teleportAndReset(double targetY) {
        ClientPlayerEntity player = mc.player;
        assert player != null;

        // Vehicle movement
        if (player.hasVehicle()) {
            Entity vehicle = player.getVehicle();
            for (int j = 0; j < 19; j++) {
                mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(vehicle));
            }
            vehicle.setPosition(vehicle.getX(), targetY, vehicle.getZ());
        }

        // Client teleport
        player.setPosition(player.getX(), targetY, player.getZ());
        player.fallDistance = 0;

        // Send a position packet to server, then a burst of on-ground packets
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(),
                player.getY(),
                player.getZ(),
                false,
                player.horizontalCollision
            )
        );
        for (int k = 0; k < 20; k++) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    true,
                    player.horizontalCollision
                )
            );
        }
    }

    // Checks for replaceable, empty, and not powder snow
    private boolean isSafe(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable()
            && mc.world.getFluidState(pos).isEmpty()
            && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }
}
