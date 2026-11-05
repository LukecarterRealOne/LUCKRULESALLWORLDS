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
      net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
      if (lp != null && net.minecraft.client.Minecraft.getInstance().screen == null
         && (lp.getMainHandItem().is((net.minecraft.world.item.Item)LuckyBlockMod.LUCKY_BLOCK.get())
            || lp.getOffhandItem().is((net.minecraft.world.item.Item)LuckyBlockMod.LUCKY_BLOCK.get()))) {
         int w = event.getWindow().getGuiScaledWidth();
         int h = event.getWindow().getGuiScaledHeight();
         float pulse = 0.5F + 0.5F * (float)Math.sin((lp.tickCount + event.getPartialTick()) * 0.08F);
         int a = (int)(18.0F + 22.0F * pulse);
         for (int i = 0; i < 10; i++) {
            int al = a * (10 - i) / 10;
            int c = al << 24 | 0xFFC020;
            event.getGuiGraphics().fill(i * 3, 0, i * 3 + 3, h, c);
            event.getGuiGraphics().fill(w - i * 3 - 3, 0, w - i * 3, h, c);
         }
      }
      if (!(ClientState.flash <= 0.01F)) {
         int a = (int)(Math.min(1.0F, ClientState.flash) * 200.0F);
         int argb = a << 24 | ClientState.flashColor & 16777215;
         event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), argb);
      }
   }
}
