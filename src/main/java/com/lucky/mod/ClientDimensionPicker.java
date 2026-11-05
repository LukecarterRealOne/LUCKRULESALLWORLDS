package com.lucky.mod;

import java.util.List;
import net.minecraft.client.Minecraft;

public final class ClientDimensionPicker {
   private ClientDimensionPicker() {
   }

   public static void open(List<String> dimensions, String selected) {
      Minecraft.getInstance().setScreen(new DimensionPickerScreen(dimensions, selected));
   }
}
