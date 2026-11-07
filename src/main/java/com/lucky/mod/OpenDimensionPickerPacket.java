package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

public final class OpenDimensionPickerPacket implements CustomPacketPayload {
   public static final Type<OpenDimensionPickerPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "open_dimension_picker"));
   public static final StreamCodec<FriendlyByteBuf, OpenDimensionPickerPacket> CODEC = StreamCodec.of((buf, m) -> {
      int count = Math.min(512, m.dimensions.size());
      buf.writeVarInt(count);
      for (int i = 0; i < count; i++) {
         buf.writeUtf(m.dimensions.get(i), 256);
      }
      buf.writeUtf(m.selected, 256);
   }, buf -> {
      int count = Math.max(0, Math.min(512, buf.readVarInt()));
      List<String> dimensions = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         dimensions.add(buf.readUtf(256));
      }
      return new OpenDimensionPickerPacket(dimensions, buf.readUtf(256));
   });
   public final List<String> dimensions;
   public final String selected;

   public OpenDimensionPickerPacket(List<String> dimensions, String selected) {
      this.dimensions = List.copyOf(dimensions);
      this.selected = selected == null ? "" : selected;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(OpenDimensionPickerPacket message, IPayloadContext ctx) {
      ctx.enqueueWork(() -> ClientDimensionPicker.open(message.dimensions, message.selected));
   }
}
