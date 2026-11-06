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
      if (ClientState.alert >= 0) {
         int ph = ClientState.alert;
         int col = (ph / 3) % 2 == 0 ? 0xFF1A1A : 0xFFD21A;
         int al = (int)(150.0F * (1.0F - ph / 45.0F)) + 40;
         event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), (Math.min(255, al) << 24) | col);
      }
      if (ClientState.cutscene >= 0) {
         renderCutscene(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
         return;
      }
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

   private static void renderCutscene(net.minecraft.client.gui.GuiGraphics g, int w, int h) {
      int t = ClientState.cutscene;
      float a = t < 20 ? t / 20.0F : (t > 470 ? Math.max(0.0F, 1.0F - (t - 470) / 30.0F) : 1.0F);
      g.fill(0, 0, w, h, ((int)(a * 255.0F) << 24));
      if (t >= 465 && t <= 480) {
         int fa = (int)(200.0F * (1.0F - Math.abs(t - 472) / 8.0F));
         if (fa > 0) {
            g.fill(0, 0, w, h, (fa << 24) | 0xFFE9A0);
         }
      }
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      for (int i = 0; i < ClientState.LINES.length; i++) {
         int[] tm = ClientState.LINE_TIMING[i];
         int dt = t - tm[0];
         if (dt < 0 || dt > tm[1]) {
            continue;
         }
         String full = ClientState.LINES[i];
         int shown = Math.min(full.length(), (dt + 1) * 2);
         int fade = Math.min(255, Math.max(8, (tm[1] - dt) * 32));
         String s = full.substring(0, shown);
         float sc = tm[3] / 10.0F;
         while (font.width(full) * sc > w - 24 && sc > 1.0F) {
            sc -= 0.25F;
         }
         g.pose().pushPose();
         g.pose().translate(w / 2.0F, h / 2.0F - 8.0F * sc, 0.0F);
         g.pose().scale(sc, sc, 1.0F);
         g.drawString(font, s, -font.width(full) / 2, 0, (fade << 24) | tm[2], false);
         g.pose().popPose();
      }
   }
}
