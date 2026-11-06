package com.lucky.mod;

import java.util.Random;

public class ClientState {
   public static float flash = 0.0F;
   public static int flashColor = 16773242;
   public static float shakeAmp = 0.0F;
   public static int shakeTicks = 0;
   public static int shakeMax = 1;
   public static final Random RNG = new Random();
   public static final int CUTSCENE_LEN = 500;
   public static int cutscene = -1;
   /** {start tick, hold ticks, color, scale x10} */
   public static final int[][] LINE_TIMING = new int[][]{
      {30, 45, 0xFFFFFF, 30}, {85, 30, 0xFF5555, 40}, {120, 38, 0xFFFFFF, 20}, {165, 42, 0xFFFFFF, 20},
      {212, 70, 0xFFD94A, 20}, {290, 48, 0xFFFFFF, 20}, {345, 48, 0xFFD94A, 20}, {400, 62, 0xFFD94A, 20}
   };
   public static final String[] LINES = new String[]{
      "...", "?!", "Wait... what?", "How did you die?",
      "Wow. The coder did NOT make the block powerful enough?",
      "Well, anyway... since you're here.", "I'll give you a new power.", "Now use the block properly this time."
   };

   public static int alert = -1;

   public static void startAlert() {
      alert = 0;
   }

   public static void startCutscene() {
      cutscene = 0;
   }


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
      if (alert >= 0) {
         alert++;
         if (alert > 44) {
            alert = -1;
         }
      }
      if (cutscene >= 0) {
         cutscene++;
         for (int i = 0; i < LINES.length; i++) {
            int dt = cutscene - LINE_TIMING[i][0];
            if (dt >= 0 && dt < LINES[i].length() / 2 + 1 && dt % 1 == 0) {
               net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                  net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.7F + (dt % 3) * 0.1F, 0.25F));
            }
         }
         if (cutscene > CUTSCENE_LEN) {
            cutscene = -1;
         }
      }

      if (flash > 0.0F) {
         flash = Math.max(0.0F, flash - 0.035F);
      }

      if (shakeTicks > 0) {
         shakeTicks--;
      }
   }
}
