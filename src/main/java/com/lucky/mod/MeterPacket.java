package com.lucky.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: luck stacks + the list of things the player has adapted to (for the bottom-right HUD). */
public final class MeterPacket implements CustomPacketPayload {
   public static final Type<MeterPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "meter"));
   public static final StreamCodec<FriendlyByteBuf, MeterPacket> CODEC = StreamCodec.of((buf, m) -> {
      buf.writeVarInt(m.luck);
      int n = Math.min(300, m.names.size());
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         buf.writeUtf(m.names.get(i), 64);
      }
   }, buf -> {
      int luck = buf.readVarInt();
      int n = Math.max(0, Math.min(300, buf.readVarInt()));
      List<String> names = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         names.add(buf.readUtf(64));
      }
      return new MeterPacket(luck, names);
   });
   public final int luck;
   public final List<String> names;

   public MeterPacket(int luck, List<String> names) {
      this.luck = luck;
      this.names = List.copyOf(names);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(MeterPacket m, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         ClientState.luck = m.luck;
         ClientState.adapted = m.names;
      });
   }
}
