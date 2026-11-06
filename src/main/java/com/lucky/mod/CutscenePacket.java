package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Tells the client to play the death cutscene (black screen + typed lines). */
public class CutscenePacket {
   public final int mode; // 0 = death cutscene, 1 = red/yellow alert flash

   public CutscenePacket() {
      this(0);
   }

   public CutscenePacket(int mode) {
      this.mode = mode;
   }

   public static void encode(CutscenePacket m, FriendlyByteBuf buf) {
      buf.writeInt(m.mode);
   }

   public static CutscenePacket decode(FriendlyByteBuf buf) {
      return new CutscenePacket(buf.readInt());
   }

   public static void handle(CutscenePacket m, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { if (m.mode == 1) { ClientState.startAlert(); } else { ClientState.startCutscene(); } }));
      ctx.get().setPacketHandled(true);
   }
}
