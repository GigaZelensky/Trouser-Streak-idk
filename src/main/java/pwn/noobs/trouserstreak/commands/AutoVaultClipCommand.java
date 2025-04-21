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
        super("autovaultclip", "Auto vault clip with robust velocity injections to prevent fall damage.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            error("Choose Up, Down or Highest");
            return SINGLE_SUCCESS;
        });
        builder.then(literal("up").executes(ctx -> clipWithVelocity(getGapY(1))));
        builder.then(literal("down").executes(ctx -> clipWithVelocity(getGapY(-1))));
        builder.then(literal("highest").executes(ctx -> clipWithVelocity(getHighestY())));
    }

    // Teleport + upward-velocity injection
    private int clipWithVelocity(double targetY) {
        ClientPlayerEntity player = mc.player;
        assert player != null;

        double x = player.getX();
        double z = player.getZ();
        // place slightly inside the gap
        double safeY = targetY + 0.5;

        // vault clip bypass packets
        if (player.hasVehicle()) {
            Entity v = player.getVehicle();
            for (int i = 0; i < 19; i++) 
                mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(v));
            v.setPosition(x, safeY, z);
        }

        // client teleport
        player.setPosition(x, safeY, z);
        player.fallDistance = 0;
        player.setVelocity(0, 0, 0);

        // notify server of teleport
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, safeY, z, false, player.horizontalCollision)
        );

        // hammer out any fall distance with repeated micro-jumps
        for (int i = 0; i < 50; i++) {
            player.setVelocity(0, 0.1, 0);
            player.fallDistance = 0;
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, safeY + 0.1, z, false, player.horizontalCollision)
            );
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, safeY,   z, true,  player.horizontalCollision)
            );
        }

        // finalize as on-ground
        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING)
        );

        return SINGLE_SUCCESS;
    }

    // find a two-block gap above (dir=1) or below (dir=-1)
    private double getGapY(int dir) {
        ClientPlayerEntity p = mc.player;
        assert p != null;
        BlockPos pos = p.getBlockPos();
        int range = 199;
        if (dir > 0) {
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
        return p.getY();
    }

    // find highest block above
    private double getHighestY() {
        ClientPlayerEntity p = mc.player;
        assert p != null;
        BlockPos pos = p.getBlockPos();
        for (int i = 199; i > 0; i--) {
            BlockPos down = pos.add(0, i, 0);
            BlockPos up   = down.up();
            if (!isSafe(down) || !mc.world.getFluidState(down).isEmpty()) return up.getY();
        }
        error("No blocks above you found!");
        return p.getY();
    }

    private boolean isSafe(BlockPos p) {
        return mc.world.getBlockState(p).isReplaceable()
            && mc.world.getFluidState(p).isEmpty()
            && !mc.world.getBlockState(p).isOf(Blocks.POWDER_SNOW);
    }
}
