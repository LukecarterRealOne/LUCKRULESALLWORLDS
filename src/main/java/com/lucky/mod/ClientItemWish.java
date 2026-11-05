package com.lucky.mod;

import net.minecraft.client.Minecraft;

public final class ClientItemWish {
   private ClientItemWish() {
   }

   public static void open() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         minecraft.setScreen(new ItemWishScreen());
      }
   }
}
