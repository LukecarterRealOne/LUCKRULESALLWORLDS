package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** mode 0 = death cutscene, 1 = red/yellow alert flash. */
public class CutscenePacket implements CustomPacketPayload {
   public static final Type<CutscenePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "cutscene"));
   public static final StreamCodec<FriendlyByteBuf, CutscenePacket> CODEC = StreamCodec.of(
      (buf, m) -> buf.writeInt(m.mode), buf -> new CutscenePacket(buf.readInt()));
   public final int mode;

   public CutscenePacket() {
      this(0);
   }

   public CutscenePacket(int mode) {
      this.mode = mode;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(CutscenePacket m, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (m.mode == 1) {
            ClientState.startAlert();
         } else {
            ClientState.startCutscene();
         }
      });
   }
}
