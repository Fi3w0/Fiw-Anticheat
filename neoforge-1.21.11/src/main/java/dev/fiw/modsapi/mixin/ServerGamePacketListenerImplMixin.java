package dev.fiw.modsapi.mixin;

import dev.fiw.modsapi.FiwModsApi;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels movement and interaction packets while a player is frozen awaiting
 * verification. The server tick snaps frozen players back to the freeze position.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    private boolean fiw$frozen() {
        return FiwModsApi.engine() != null && FiwModsApi.engine().freezes().isFrozen(player.getUUID());
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void fiw$onMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Runs on the network thread first: only drop the packet here. The snap-back
        // teleport is sent from the server tick on the main thread.
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void fiw$onMoveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void fiw$onUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void fiw$onInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void fiw$onUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void fiw$onSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void fiw$onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void fiw$onContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void fiw$onChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (fiw$frozen()) ci.cancel();
    }
}
