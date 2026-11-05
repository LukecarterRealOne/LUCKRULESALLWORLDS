package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class SelectPacket {
   public final int ability;
   public final int castNow;

   public SelectPacket(int ability, int castNow) {
      this.ability = ability;
      this.castNow = castNow;
   }

   public static void encode(SelectPacket m, FriendlyByteBuf buf) {
      buf.writeInt(m.ability);
      buf.writeInt(m.castNow);
   }

   public static SelectPacket decode(FriendlyByteBuf buf) {
      return new SelectPacket(buf.readInt(), buf.readInt());
   }

   public static void handle(SelectPacket m, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer sender = ctx.get().getSender();
         if (sender != null) {
            LuckyBlockItem.onSelect(sender, m.ability, m.castNow == 1);
         }
      });
      ctx.get().setPacketHandled(true);
   }
}
