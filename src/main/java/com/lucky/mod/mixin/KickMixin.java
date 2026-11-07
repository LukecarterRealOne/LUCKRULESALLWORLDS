package com.lucky.mod.mixin;

import com.lucky.mod.Failsafe;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class KickMixin {
   @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void lbm$failsafeDetails(DisconnectionDetails details, CallbackInfo ci) {
      if ((Object)this instanceof ServerGamePacketListenerImpl g && Failsafe.onKick(g.player, details.reason())) {
         ci.cancel();
      }
   }

   @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void lbm$failsafeComponent(Component reason, CallbackInfo ci) {
      if ((Object)this instanceof ServerGamePacketListenerImpl g && Failsafe.onKick(g.player, reason)) {
         ci.cancel();
      }
   }
}
