package com.lucky.mod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(
   modid = "luckyblockmod",
   value = {Dist.CLIENT}
)
public class ClientEvents {
   public ClientEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      {
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
         event.getGuiGraphics().fill(0, 0, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight(), (Math.min(255, al) << 24) | col);
      }
      if (ClientState.cutscene >= 0) {
         renderCutscene(event.getGuiGraphics(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight());
         return;
      }
      renderMeter(event.getGuiGraphics());
      net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
      if (lp != null && net.minecraft.client.Minecraft.getInstance().screen == null
         && (lp.getMainHandItem().is((net.minecraft.world.item.Item)LuckyBlockMod.LUCKY_BLOCK.get())
            || lp.getOffhandItem().is((net.minecraft.world.item.Item)LuckyBlockMod.LUCKY_BLOCK.get()))) {
         int w = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
         int h = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
         float pulse = 0.5F + 0.5F * (float)Math.sin((lp.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.08F);
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
         event.getGuiGraphics().fill(0, 0, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight(), argb);
      }
   }

   /** Bottom-right: the luck meter bar and the list of everything you have adapted to. */
   private static void renderMeter(net.minecraft.client.gui.GuiGraphics g) {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      net.minecraft.client.player.LocalPlayer lp = mc.player;
      if (lp == null || mc.screen != null || mc.options.hideGui
         || !(lp.getMainHandItem().getItem() instanceof LuckyBlockItem || lp.getOffhandItem().getItem() instanceof LuckyBlockItem)) {
         return;
      }
      if (ClientState.luck <= 0 && ClientState.adapted.isEmpty()) {
         return;
      }
      int w = mc.getWindow().getGuiScaledWidth();
      int h = mc.getWindow().getGuiScaledHeight();
      net.minecraft.client.gui.Font font = mc.font;
      int bw = 120;
      int x = w - bw - 8;
      int y = h - 14;
      float f = Math.min(1.0F, ClientState.luck / 50000.0F);
      g.fill(x - 1, y - 1, x + bw + 1, y + 7, 0xAA000000);
      g.fill(x, y, x + bw, y + 6, 0xFF2A2200);
      int fillW = (int)(bw * f);
      g.fill(x, y, x + fillW, y + 6, 0xFFFFC820);
      g.fill(x, y, x + fillW, y + 2, 0xFFFFF3A0);
      String lt = "\u00a76LUCK \u00a7e" + String.format("%,d", ClientState.luck) + " \u00a78/ 50,000";
      g.drawString(font, lt, w - 8 - font.width(lt), y - 10, 0xFFFFFF, true);
      java.util.List<String> a = ClientState.adapted;
      if (!a.isEmpty()) {
         int shown = Math.min(8, a.size());
         int ly = y - 12 - shown * 10;
         String head = "\u00a7e\u00a7lADAPTED \u00a7r\u00a78(" + a.size() + ")";
         g.drawString(font, head, w - 8 - font.width(head), ly - 11, 0xFFFFFF, true);
         for (int i = 0; i < shown; i++) {
            String line = "\u00a76" + a.get(a.size() - shown + i);
            g.drawString(font, line, w - 8 - font.width(line), ly + i * 10, 0xFFFFFF, true);
         }
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

   /** No inventory or other menus while the death cutscene plays (pause menu still works). */
   @SubscribeEvent
   public static void onScreenOpen(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
      if (ClientState.cutscene >= 0 && event.getNewScreen() != null && !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) {
         event.setCanceled(true);
      }
   }

   /** Hide the "Killed <name>" / death lines the game prints for the death the block just cancelled. */
   @SubscribeEvent
   public static void onChatLine(net.neoforged.neoforge.client.event.ClientChatReceivedEvent event) {
      if (ClientState.cutscene >= 0 && event.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
         && (tc.getKey().startsWith("death.") || tc.getKey().startsWith("commands.kill"))) {
         event.setCanceled(true);
      }
   }
}
