package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenItemWishPacket implements CustomPacketPayload {
   public static final Type<OpenItemWishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "open_item_wish"));
   public static final StreamCodec<FriendlyByteBuf, OpenItemWishPacket> CODEC = StreamCodec.of((buf, m) -> {
   }, buf -> new OpenItemWishPacket());

   public OpenItemWishPacket() {
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(OpenItemWishPacket message, IPayloadContext ctx) {
      ctx.enqueueWork(ClientItemWish::open);
   }
}
