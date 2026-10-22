package dev.fiw.modsapi.mixin;

import dev.fiw.modsapi.FiwModsApi;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels movement and interaction packets while a player is frozen awaiting
 * verification. The server tick snaps frozen players back to the freeze position.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    private boolean fiw$frozen() {
        return FiwModsApi.engine() != null && FiwModsApi.engine().freezes().isFrozen(player.getUuid());
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        // Runs on the network thread first: only drop the packet here. The snap-back
        // teleport is sent from the server tick on the main thread.
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
    private void fiw$onVehicleMove(VehicleMoveC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerInteractBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerInteractItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void fiw$onCreativeInventoryAction(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void fiw$onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void fiw$onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }
}
