package ropeladders.core;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class RopeLaddersCore {

    private static final ThreadLocal<Boolean> processingChain = ThreadLocal.withInitial(() -> false);

    public static boolean isProcessingChain() {
        return processingChain.get();
    }

    public static boolean isSupported(World world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof LadderBlock)) return false;
        Direction facing = state.get(LadderBlock.FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(supportPos);
        return supportState.isFullCube(world, supportPos);
    }

    public static ActionResult tryPlaceDownward(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty() || stack.getItem() != Items.LADDER) {
            return ActionResult.PASS;
        }
        BlockPos originPos = hitResult.getBlockPos();
        BlockState originState = world.getBlockState(originPos);
        if (!(originState.getBlock() instanceof LadderBlock)) {
            return ActionResult.PASS;
        }
        if (player.interactionManager.getGameMode() == GameMode.ADVENTURE) {
            return ActionResult.PASS;
        }
        Direction facing = originState.get(LadderBlock.FACING);
        BlockPos currentPos = originPos.down();
        int depth = 0;
        boolean found = false;
        while (depth < RopeLaddersConfig.maxChainLength) {
            BlockState belowState = world.getBlockState(currentPos);
            if (belowState.isReplaceable()) {
                found = true;
                break;
            } else if (belowState.isSolidBlock(world, currentPos) || belowState.isOpaqueFullCube(world, currentPos)) {
                return ActionResult.PASS;
            }
            currentPos = currentPos.down();
            depth++;
        }
        if (!found) {
            return ActionResult.PASS;
        }
        BlockState newState = Blocks.LADDER.getDefaultState()
                .with(LadderBlock.FACING, facing)
                .with(LadderBlock.WATERLOGGED, world.getBlockState(currentPos).getBlock() == Blocks.WATER);
        world.setBlockState(currentPos, newState, 3);
        if (player.interactionManager.getGameMode() != GameMode.CREATIVE) {
            stack.decrement(1);
            if (stack.isEmpty()) {
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
        }
        if (RopeLaddersConfig.soundsEnabled) {
            world.playSound(null, currentPos, SoundEvents.BLOCK_LADDER_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        return ActionResult.SUCCESS;
    }

    public static void breakChain(World world, BlockPos brokenPos, BlockState brokenState, ServerPlayerEntity player) {
        if (processingChain.get()) return;
        processingChain.set(true);
        try {
            if (!(brokenState.getBlock() instanceof LadderBlock)) return;
            if (isSupported(world, brokenPos, brokenState)) return;

            BlockPos topPos = brokenPos;
            while (world.getBlockState(topPos.up()).getBlock() instanceof LadderBlock) {
                topPos = topPos.up();
            }
            BlockPos targetPos = topPos;
            List<ItemEntity> droppedItems = new ArrayList<>();
            BlockPos currentPos = brokenPos.down();
            int depth = 0;

            while (depth < RopeLaddersConfig.maxChainLength) {
                BlockState state = world.getBlockState(currentPos);
                if (!(state.getBlock() instanceof LadderBlock)) break;

                if (!isSupported(world, currentPos, state)) {
                    world.breakBlock(currentPos, false, player);
                    ItemEntity item = new ItemEntity(
                            world,
                            currentPos.getX() + 0.5,
                            currentPos.getY() + 0.5,
                            currentPos.getZ() + 0.5,
                            new ItemStack(Items.LADDER)
                    );
                    world.spawnEntity(item);
                    droppedItems.add(item);

                    if (RopeLaddersConfig.soundsEnabled) {
                        world.playSound(null, currentPos, SoundEvents.BLOCK_SCAFFOLDING_FALL, SoundCategory.BLOCKS, 0.8f, 0.5f);
                    }
                    currentPos = currentPos.down();
                    depth++;
                } else {
                    break;
                }
            }

            if (!droppedItems.isEmpty()) {
                double tx = targetPos.getX() + 0.5;
                double ty = targetPos.getY() + 0.5;
                double tz = targetPos.getZ() + 0.5;
                for (ItemEntity item : droppedItems) {
                    item.teleport(tx, ty, tz);
                }
            }
        } finally {
            processingChain.remove();
        }
    }

    public static void spawnIndicators(ServerPlayerEntity player) {
        if (!RopeLaddersConfig.particlesEnabled) return;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        if (main.getItem() != Items.LADDER && off.getItem() != Items.LADDER) return;
        World world = player.getWorld();
        double range = RopeLaddersConfig.raycastDistance;
        HitResult hit = player.raycast(range, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof LadderBlock) {
                Direction facing = state.get(LadderBlock.FACING);
                Vec3d center = Vec3d.ofCenter(pos);
                Vec3d offset = new Vec3d(facing.getUnitVector()).multiply(0.01);
                double[] yOffsets = {0.2, 0.5, 0.8};
                for (double yOff : yOffsets) {
                    double x = center.x + offset.x;
                    double y = pos.getY() + yOff;
                    double z = center.z + offset.z;
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(
                                player,
                                ParticleTypes.WAX_ON,
                                true,
                                x, y, z,
                                0, 0, 0, 0,
                                0
                        );
                    }
                }
            }
        }
    }
}