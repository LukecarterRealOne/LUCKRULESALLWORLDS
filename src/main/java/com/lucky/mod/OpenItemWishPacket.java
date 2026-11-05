package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class OpenItemWishPacket {
   public OpenItemWishPacket() {
   }

   public static void encode(OpenItemWishPacket message, FriendlyByteBuf buffer) {
   }

   public static OpenItemWishPacket decode(FriendlyByteBuf buffer) {
      return new OpenItemWishPacket();
   }

   public static void handle(OpenItemWishPacket message, Supplier<Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientItemWish::open));
      context.get().setPacketHandled(true);
   }
}
