package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoVaultClipCommand extends Command {
    public AutoVaultClipCommand() {
        super("autovaultclip", "Ultimate vertical clip with vault bypass and zero fall damage. Paper/Spigot recommended.");
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
                BlockPos a = player.getBlockPos().add(0, i + 2, 0);
                BlockPos b = player.getBlockPos().add(0, i + 3, 0);
                if (isSafe(a) && isSafe(b)) {
                    executeClip(a.getY());
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
                BlockPos a = player.getBlockPos().add(0, i, 0);
                BlockPos b = player.getBlockPos().add(0, i - 1, 0);
                if (isSafe(a) && isSafe(b)) {
                    executeClip(b.getY());
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
                BlockPos above = below.up();
                if (!isSafe(below) || !mc.world.getFluidState(below).isEmpty()) {
                    executeClip(above.getY());
                    return SINGLE_SUCCESS;
                }
            }
            error("No blocks above you found!");
            return SINGLE_SUCCESS;
        }));
    }

    /**
     * Performs the teleport, resets fall state, and floods packets to cancel fall damage.
     */
    private void executeClip(double targetY) {
        ClientPlayerEntity player = mc.player;
        assert player != null;

        // Vehicle bypass
        if (player.hasVehicle()) {
            Entity veh = player.getVehicle();
            for (int i = 0; i < 19; i++) mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(veh));
            veh.setPosition(veh.getX(), targetY, veh.getZ());
        }

        // Teleport client
        player.updatePosition(player.getX(), targetY, player.getZ());
        player.fallDistance = 0;
        player.setVelocity(0, 0, 0);

        // Send initial false onGround to register movement
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), player.getY(), player.getZ(), false, player.horizontalCollision
            )
        );

        // Micro-oscillations to reset server fall calculations
        for (int i = 0; i < 100; i++) sendMicroMovement();

        // Final on-ground confirmations
        for (int i = 0; i < 20; i++) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision)
            );
        }

        // Optional sprint packet to solidify state
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    /**
     * Sends tiny up-down movement packets to trick the server into thinking we've landed.
     */
    private void sendMicroMovement() {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 1e-6, z, true, mc.player.horizontalCollision)
        );
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 1e-6, z, false, mc.player.horizontalCollision)
        );
    }

    private boolean isSafe(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable()
            && mc.world.getFluidState(pos).isEmpty()
            && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }
}
