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
        super("smartclip", "Like autovaultclip, but 'down' uses a single vclip to the nearest gap.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            error("Choose Up, Down or Highest");
            return SINGLE_SUCCESS;
        });

        // Up: identical to AutoVaultClipCommand
        builder.then(literal("up").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 0; i < 199; i++) {
                BlockPos isopenair1 = player.getBlockPos().add(0, i + 2, 0);
                BlockPos isopenair2 = player.getBlockPos().add(0, i + 3, 0);
                if (mc.world.getBlockState(isopenair1).isReplaceable()
                    && mc.world.getFluidState(isopenair1).isEmpty()
                    && !mc.world.getBlockState(isopenair1).isOf(Blocks.POWDER_SNOW)
                    && mc.world.getBlockState(isopenair2).isReplaceable()
                    && mc.world.getFluidState(isopenair2).isEmpty()
                    && !mc.world.getBlockState(isopenair2).isOf(Blocks.POWDER_SNOW)
                ) {
                    int packetsRequired = 20;
                    if (player.hasVehicle()) {
                        Entity vehicle = player.getVehicle();
                        for (int n = 0; n < packetsRequired - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(vehicle));
                        vehicle.setPosition(vehicle.getX(), isopenair1.getY(), vehicle.getZ());
                    }
                    for (int n = 0; n < packetsRequired - 1; n++)
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                    player.setPosition(player.getX(), isopenair1.getY(), player.getZ());
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // Down: smart single vclip
        builder.then(literal("down").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 0; i > -199; i--) {
                BlockPos isopenair1 = player.getBlockPos().add(0, i, 0);
                BlockPos isopenair2 = player.getBlockPos().add(0, i - 1, 0);
                if (mc.world.getBlockState(isopenair1).isReplaceable()
                    && mc.world.getFluidState(isopenair1).isEmpty()
                    && !mc.world.getBlockState(isopenair1).isOf(Blocks.POWDER_SNOW)
                    && mc.world.getBlockState(isopenair2).isReplaceable()
                    && mc.world.getFluidState(isopenair2).isEmpty()
                    && !mc.world.getBlockState(isopenair2).isOf(Blocks.POWDER_SNOW)
                ) {
                    double blocks = isopenair2.getY() - player.getY(); // negative
                    int packetsRequired = (int) Math.ceil(Math.abs(blocks / 10.0));
                    if (packetsRequired > 20) packetsRequired = 1;

                    if (player.hasVehicle()) {
                        Entity vehicle = player.getVehicle();
                        for (int n = 0; n < packetsRequired - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(vehicle));
                        vehicle.setPosition(vehicle.getX(), vehicle.getY() + blocks, vehicle.getZ());
                        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(vehicle));
                    } else {
                        for (int n = 0; n < packetsRequired - 1; n++)
                            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                        mc.player.networkHandler.sendPacket(
                            new PlayerMoveC2SPacket.PositionAndOnGround(
                                player.getX(),
                                player.getY() + blocks,
                                player.getZ(),
                                true,
                                mc.player.horizontalCollision
                            )
                        );
                        player.setPosition(player.getX(), player.getY() + blocks, player.getZ());
                    }
                    return SINGLE_SUCCESS;
                }
            }
            error("No gap found to vclip into");
            return SINGLE_SUCCESS;
        }));

        // Highest: identical to AutoVaultClipCommand
        builder.then(literal("highest").executes(ctx -> {
            ClientPlayerEntity player = mc.player;
            assert player != null;
            for (int i = 199; i > 0; i--) {
                BlockPos isopenair1 = player.getBlockPos().add(0, i, 0);
                BlockPos newopenair2 = isopenair1.up(1);
                if (!mc.world.getBlockState(isopenair1).isReplaceable()
                    || mc.world.getBlockState(isopenair1).isOf(Blocks.POWDER_SNOW)
                    || !mc.world.getFluidState(isopenair1).isEmpty()
                ) {
                    int packetsRequired = 20;
                    if (player.hasVehicle()) {
                        Entity vehicle = player.getVehicle();
                        for (int n = 0; n < packetsRequired - 1; n++)
                            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(vehicle));
                        vehicle.setPosition(vehicle.getX(), newopenair2.getY(), vehicle.getZ());
                    }
                    for (int n = 0; n < packetsRequired - 1; n++)
                        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                    player.setPosition(player.getX(), newopenair2.getY(), player.getZ());
                    return SINGLE_SUCCESS;
                }
            }
            error("No blocks above you found!");
            return SINGLE_SUCCESS;
        }));
    }
}
