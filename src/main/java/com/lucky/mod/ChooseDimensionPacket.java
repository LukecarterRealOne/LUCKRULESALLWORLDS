package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ChooseDimensionPacket implements CustomPacketPayload {
   public static final Type<ChooseDimensionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "choose_dimension"));
   public static final StreamCodec<FriendlyByteBuf, ChooseDimensionPacket> CODEC = StreamCodec.of(
      (buf, m) -> buf.writeUtf(m.dimensionId, 256), buf -> new ChooseDimensionPacket(buf.readUtf(256)));
   public final String dimensionId;

   public ChooseDimensionPacket(String dimensionId) {
      this.dimensionId = dimensionId == null ? "" : dimensionId;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(ChooseDimensionPacket message, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sender) {
            LuckyBlockItem.onDimensionSelected(sender, message.dimensionId);
         }
      });
   }
}
