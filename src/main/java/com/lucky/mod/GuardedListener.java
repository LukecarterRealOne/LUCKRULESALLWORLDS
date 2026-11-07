package com.lucky.mod;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** A normal game listener whose disconnect (kick) is routed through the Lucky Block failsafe first. */
public final class GuardedListener extends ServerGamePacketListenerImpl {
   public GuardedListener(MinecraftServer server, Connection connection, ServerPlayer player) {
      super(server, connection, player);
   }

   @Override
   public void disconnect(Component reason) {
      if (Failsafe.onKick(this.player, reason)) {
         return;
      }
      super.disconnect(reason);
   }
}
