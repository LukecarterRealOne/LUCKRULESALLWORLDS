package com.lucky.mod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "luckyblockmod",
   value = {Dist.CLIENT}
)
public class ClientEvents {
   public ClientEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         ClientState.tick();
         Minecraft mc = Minecraft.getInstance();

         while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
               mc.setScreen(new AbilityScreen());
            }
         }
      }
   }

   @SubscribeEvent
   public static void onCamera(ComputeCameraAngles event) {
      if (ClientState.shakeTicks > 0) {
         float amp = ClientState.shakeAmp * (float)ClientState.shakeTicks / (float)ClientState.shakeMax;
         event.setYaw(event.getYaw() + (ClientState.RNG.nextFloat() - 0.5F) * 2.0F * amp);
         event.setPitch(event.getPitch() + (ClientState.RNG.nextFloat() - 0.5F) * 2.0F * amp);
         event.setRoll(event.getRoll() + (ClientState.RNG.nextFloat() - 0.5F) * 2.0F * amp);
      }
   }

   @SubscribeEvent
   public static void onGui(Post event) {
      if (!(ClientState.flash <= 0.01F)) {
         int a = (int)(Math.min(1.0F, ClientState.flash) * 200.0F);
         int argb = a << 24 | ClientState.flashColor & 16777215;
         event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), argb);
      }
   }
}
