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

public class SmartClipCommand extends Command {
    public SmartClipCommand() {
        super("smartclip", "Like autovaultclip, but ‘down’ does a single safe vclip.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            error("Choose Up, Down or Highest");
            return SINGLE_SUCCESS;
        });

        // UP: same as before
        builder.then(literal("up").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 0; i < 199; i++) {
                BlockPos a = player.getBlockPos().add(0, i + 2, 0);
                BlockPos b = player.getBlockPos().add(0, i + 3, 0);
                if (mc.world.getBlockState(a).isReplaceable()
                    && mc.world.getFluidState(a).isEmpty()
                    && !mc.world.getBlockState(a).isOf(Blocks.POWDER_SNOW)
                    && mc.world.getBlockState(b).isReplaceable()
                    && mc.world.getFluidState(b).isEmpty()
                    && !mc.world.getBlockState(b).isOf(Blocks.POWDER_SNOW)
                ) {
                    int packets = 20;
                    if (player.hasVehicle()) {
                        Entity v = player.getVehicle();
                        for (int n = 0; n < packets - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                        v.setPosition(v.getX(), a.getY(), v.getZ());
                        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                    }
                    for (int n = 0; n < packets - 1; n++)
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
                    player.setPosition(player.getX(), a.getY(), player.getZ());
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // DOWN: smart single-step vclip with no damage
        builder.then(literal("down").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 0; i > -199; i--) {
                BlockPos a = player.getBlockPos().add(0, i, 0);
                BlockPos b = player.getBlockPos().add(0, i - 1, 0);
                if (mc.world.getBlockState(a).isReplaceable()
                    && mc.world.getFluidState(a).isEmpty()
                    && !mc.world.getBlockState(a).isOf(Blocks.POWDER_SNOW)
                    && mc.world.getBlockState(b).isReplaceable()
                    && mc.world.getFluidState(b).isEmpty()
                    && !mc.world.getBlockState(b).isOf(Blocks.POWDER_SNOW)
                ) {
                    // distance down (negative)
                    double dist = b.getY() - player.getY();
                    int packets = (int) Math.ceil(Math.abs(dist / 10.0));
                    if (packets > 20) packets = 1;

                    double targetY = b.getY() + 0.2; // 0.2 above the ground block

                    if (player.hasVehicle()) {
                        Entity v = player.getVehicle();
                        for (int n = 0; n < packets - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                        // teleport vehicle
                        v.setPosition(v.getX(), v.getY() + dist, v.getZ());
                        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                        // ensure player inside vehicle is safe
                        player.fallDistance = 0;
                        player.setVelocity(0, 0.1, 0);
                    } else {
                        for (int n = 0; n < packets - 1; n++)
                            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
                        // final move packet
                        mc.player.networkHandler.sendPacket(
                            new PlayerMoveC2SPacket.PositionAndOnGround(
                                player.getX(),
                                player.getY() + dist,
                                player.getZ(),
                                true,
                                player.horizontalCollision
                            )
                        );
                        // position 0.2 above floor
                        player.setPosition(player.getX(), targetY, player.getZ());
                        // upward bump & clear fall
                        player.setVelocity(0, 0.1, 0);
                        player.fallDistance = 0;
                    }
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // HIGHEST: same as before
        builder.then(literal("highest").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 199; i > 0; i--) {
                BlockPos a = player.getBlockPos().add(0, i, 0);
                BlockPos b = a.up(1);
                if (!mc.world.getBlockState(a).isReplaceable()
                    || mc.world.getBlockState(a).isOf(Blocks.POWDER_SNOW)
                    || !mc.world.getFluidState(a).isEmpty()
                ) {
                    int packets = 20;
                    if (player.hasVehicle()) {
                        Entity v = player.getVehicle();
                        for (int n = 0; n < packets - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                        v.setPosition(v.getX(), b.getY(), v.getZ());
                        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
                    }
                    for (int n = 0; n < packets - 1; n++)
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
                    player.setPosition(player.getX(), b.getY(), player.getZ());
                    return SINGLE_SUCCESS;
                }
            }
            error("No blocks above you found!");
            return SINGLE_SUCCESS;
        }));
    }
}
