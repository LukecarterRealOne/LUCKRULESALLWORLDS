package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FxPacket implements CustomPacketPayload {
   public static final Type<FxPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "fx"));
   public static final StreamCodec<FriendlyByteBuf, FxPacket> CODEC = StreamCodec.of((buf, m) -> {
      buf.writeFloat(m.flash);
      buf.writeFloat(m.shake);
      buf.writeInt(m.duration);
      buf.writeInt(m.color);
   }, buf -> new FxPacket(buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt()));
   public final float flash;
   public final float shake;
   public final int duration;
   public final int color;

   public FxPacket(float flash, float shake, int duration, int color) {
      this.flash = flash;
      this.shake = shake;
      this.duration = duration;
      this.color = color;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(FxPacket m, IPayloadContext ctx) {
      ctx.enqueueWork(() -> ClientState.receive(m.flash, m.shake, m.duration, m.color));
   }
}
