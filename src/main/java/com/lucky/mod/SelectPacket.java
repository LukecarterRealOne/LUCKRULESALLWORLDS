package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SelectPacket implements CustomPacketPayload {
   public static final Type<SelectPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "select"));
   public static final StreamCodec<FriendlyByteBuf, SelectPacket> CODEC = StreamCodec.of((buf, m) -> {
      buf.writeInt(m.ability);
      buf.writeInt(m.castNow);
   }, buf -> new SelectPacket(buf.readInt(), buf.readInt()));
   public final int ability;
   public final int castNow;

   public SelectPacket(int ability, int castNow) {
      this.ability = ability;
      this.castNow = castNow;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(SelectPacket m, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sender) {
            LuckyBlockItem.onSelect(sender, m.ability, m.castNow == 1);
         }
      });
   }
}
