package ropeladders.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ropeladders.core.RopeLaddersCore;

@Mixin(ServerPlayerInteractionManager.class)
public class InteractMixin {

    @Shadow private ServerPlayerEntity player;

    private static final ThreadLocal<BlockState> BEFORE_BREAK_STATE = new ThreadLocal<>();

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = false)
    private void onInteractBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        RopeLaddersCore.tryPlaceDownward(player, world, hand, hitResult);
    }

    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void onTryBreakBlockHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.player != null) {
            BEFORE_BREAK_STATE.set(this.player.getWorld().getBlockState(pos));
        }
    }

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void onTryBreakBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = BEFORE_BREAK_STATE.get();
        if (state != null && this.player != null && cir.getReturnValue() && !RopeLaddersCore.isProcessingChain()) {
            RopeLaddersCore.breakChain(this.player.getWorld(), pos, state, this.player);
        }
        BEFORE_BREAK_STATE.remove();
    }
}