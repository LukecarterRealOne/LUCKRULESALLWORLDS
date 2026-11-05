package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public final class ChooseDimensionPacket {
   public final String dimensionId;

   public ChooseDimensionPacket(String dimensionId) {
      this.dimensionId = dimensionId == null ? "" : dimensionId;
   }

   public static void encode(ChooseDimensionPacket message, FriendlyByteBuf buf) {
      buf.writeUtf(message.dimensionId, 256);
   }

   public static ChooseDimensionPacket decode(FriendlyByteBuf buf) {
      return new ChooseDimensionPacket(buf.readUtf(256));
   }

   public static void handle(ChooseDimensionPacket message, Supplier<Context> context) {
      context.get().enqueueWork(() -> {
         ServerPlayer sender = context.get().getSender();
         if (sender != null) {
            LuckyBlockItem.onDimensionSelected(sender, message.dimensionId);
         }
      });
      context.get().setPacketHandled(true);
   }
}
