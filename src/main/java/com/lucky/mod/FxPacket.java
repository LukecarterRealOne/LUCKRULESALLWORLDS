package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class FxPacket {
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

   public static void encode(FxPacket m, FriendlyByteBuf buf) {
      buf.writeFloat(m.flash);
      buf.writeFloat(m.shake);
      buf.writeInt(m.duration);
      buf.writeInt(m.color);
   }

   public static FxPacket decode(FriendlyByteBuf buf) {
      return new FxPacket(buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt());
   }

   public static void handle(FxPacket m, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.receive(m.flash, m.shake, m.duration, m.color)));
      ctx.get().setPacketHandled(true);
   }
}
