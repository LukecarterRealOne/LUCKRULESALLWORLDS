package com.lucky.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Server -> client: luck stacks + the list of things the player has adapted to (for the bottom-right HUD). */
public final class MeterPacket {
   public final int luck;
   public final List<String> names;

   public MeterPacket(int luck, List<String> names) {
      this.luck = luck;
      this.names = List.copyOf(names);
   }

   public static void encode(MeterPacket m, FriendlyByteBuf buf) {
      buf.writeVarInt(m.luck);
      int n = Math.min(300, m.names.size());
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         buf.writeUtf(m.names.get(i), 64);
      }
   }

   public static MeterPacket decode(FriendlyByteBuf buf) {
      int luck = buf.readVarInt();
      int n = Math.max(0, Math.min(300, buf.readVarInt()));
      List<String> names = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         names.add(buf.readUtf(64));
      }
      return new MeterPacket(luck, names);
   }

   public static void handle(MeterPacket m, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
         ClientState.luck = m.luck;
         ClientState.adapted = m.names;
      }));
      ctx.get().setPacketHandled(true);
   }
}
