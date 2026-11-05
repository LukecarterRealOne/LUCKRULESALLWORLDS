package com.lucky.mod;

import java.util.Random;

public class ClientState {
   public static float flash = 0.0F;
   public static int flashColor = 16773242;
   public static float shakeAmp = 0.0F;
   public static int shakeTicks = 0;
   public static int shakeMax = 1;
   public static final Random RNG = new Random();

   public ClientState() {
   }

   public static void receive(float f, float shake, int duration, int color) {
      float safeFlash = Math.max(0.0F, Math.min(f, 0.25F));
      if (safeFlash > flash) {
         flash = safeFlash;
         flashColor = color;
      }

      float current = shakeTicks > 0 ? shakeAmp * (float)shakeTicks / (float)shakeMax : 0.0F;
      float safeShake = Math.max(0.0F, Math.min(shake, 1.5F));
      if (safeShake >= current) {
         shakeAmp = safeShake;
         shakeMax = Math.max(1, Math.min(duration, 14));
         shakeTicks = shakeMax;
      }
   }

   public static void tick() {
      if (flash > 0.0F) {
         flash = Math.max(0.0F, flash - 0.035F);
      }

      if (shakeTicks > 0) {
         shakeTicks--;
      }
   }
}
