package com.lucky.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public final class OpenDimensionPickerPacket {
   public final List<String> dimensions;
   public final String selected;

   public OpenDimensionPickerPacket(List<String> dimensions, String selected) {
      this.dimensions = List.copyOf(dimensions);
      this.selected = selected == null ? "" : selected;
   }

   public static void encode(OpenDimensionPickerPacket message, FriendlyByteBuf buf) {
      int count = Math.min(512, message.dimensions.size());
      buf.writeVarInt(count);

      for (int i = 0; i < count; i++) {
         buf.writeUtf(message.dimensions.get(i), 256);
      }

      buf.writeUtf(message.selected, 256);
   }

   public static OpenDimensionPickerPacket decode(FriendlyByteBuf buf) {
      int count = Math.max(0, Math.min(512, buf.readVarInt()));
      List<String> dimensions = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         dimensions.add(buf.readUtf(256));
      }

      return new OpenDimensionPickerPacket(dimensions, buf.readUtf(256));
   }

   public static void handle(OpenDimensionPickerPacket message, Supplier<Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDimensionPicker.open(message.dimensions, message.selected)));
      context.get().setPacketHandled(true);
   }
}
