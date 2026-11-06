package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Tells the client to play the death cutscene (black screen + typed lines). */
public class CutscenePacket {
   public static void encode(CutscenePacket m, FriendlyByteBuf buf) {
   }

   public static CutscenePacket decode(FriendlyByteBuf buf) {
      return new CutscenePacket();
   }

   public static void handle(CutscenePacket m, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.startCutscene()));
      ctx.get().setPacketHandled(true);
   }
}
