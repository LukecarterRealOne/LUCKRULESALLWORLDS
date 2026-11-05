package com.lucky.mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AbilityScreen extends Screen {
   private static boolean castNow = false;
   private static int lastPage = 0;
   private int page = Math.max(0, Math.min(lastPage, pageCount() - 1));
   private int startX;
   private int startY;
   private int buttonWidth;
   private int buttonHeight;
   private int rowStep;
   private int navY;

   public AbilityScreen() {
      super(Component.literal("Lucky Block Abilities"));
   }

   private static int pageCount() {
      return (Abilities.COUNT + 10 - 1) / 10;
   }

   private static Component modeText() {
      return Component.literal(castNow ? "CAST NOW  \u2022  hold Lucky Block" : "SELECT  \u2022  right-click to cast");
   }

   protected void init() {
      this.clearWidgets();
      int margin = Math.max(8, this.width / 24);
      int gapX = 6;
      this.buttonWidth = Math.max(80, (this.width - margin * 2 - gapX) / 2);
      this.buttonHeight = this.height < 250 ? 18 : 20;
      this.rowStep = this.buttonHeight + 3;
      this.startX = margin;
      this.startY = this.height < 240 ? 42 : 49;

      for (int slot = 0; slot < 10; slot++) {
         int ability = this.page * 10 + slot;
         if (ability >= Abilities.COUNT) {
            break;
         }

         int x = this.startX + slot % 2 * (this.buttonWidth + gapX);
         int y = this.startY + slot / 2 * this.rowStep;
         String label = String.format("%02d  %s", ability + 1, Abilities.NAMES[ability]);
         this.addRenderableWidget(
            Button.builder(Component.literal(label), b -> this.choose(ability)).bounds(x, y, this.buttonWidth, this.buttonHeight).build()
         );
      }

      this.navY = this.height - (this.height < 240 ? 60 : 67);
      int navGap = 6;
      int prevWidth = Math.max(54, (this.width - 24) / 4);
      int randomWidth = Math.max(88, this.width / 3);
      int navTotal = prevWidth + randomWidth + prevWidth + navGap * 2;
      int navX = (this.width - navTotal) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("\u2039 Prev"), b -> this.changePage(-1)).bounds(navX, this.navY, prevWidth, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Random Roll"), b -> this.choose(-1))
            .bounds(navX + prevWidth + navGap, this.navY, randomWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Next \u203a"), b -> this.changePage(1))
            .bounds(navX + prevWidth + navGap + randomWidth + navGap, this.navY, prevWidth, 20)
            .build()
      );
      int modeWidth = Math.min(260, this.width - 24);
      this.addRenderableWidget(Button.builder(modeText(), b -> {
         castNow = !castNow;
         b.setMessage(modeText());
      }).bounds((this.width - modeWidth) / 2, this.height - 27, modeWidth, 20).build());
   }

   private void changePage(int delta) {
      this.page = (this.page + delta + pageCount()) % pageCount();
      lastPage = this.page;
      this.rebuildWidgets();
   }

   private void choose(int id) {
      lastPage = this.page;
      LuckyBlockMod.CHANNEL.sendToServer(new SelectPacket(id, castNow ? 1 : 0));
      this.onClose();
   }

   private int hoveredAbility(double mouseX, double mouseY) {
      int gapX = 6;

      for (int slot = 0; slot < 10; slot++) {
         int id = this.page * 10 + slot;
         if (id >= Abilities.COUNT) {
            break;
         }

         int x = this.startX + slot % 2 * (this.buttonWidth + gapX);
         int y = this.startY + slot / 2 * this.rowStep;
         if (mouseX >= (double)x && mouseX < (double)(x + this.buttonWidth) && mouseY >= (double)y && mouseY < (double)(y + this.buttonHeight)) {
            return id;
         }
      }

      return -1;
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(g);
      g.fillGradient(0, 0, this.width, this.height, 0x66301E00, 0xAA050300);
      g.fill(this.width / 2 - 90, 48, this.width / 2 + 90, 49, 0xCCFFC83A);
      super.render(g, mouseX, mouseY, partialTick);
      g.drawString(this.font, "FX thanks to rimuru.dev (The Shooting Star)", 4, this.height - 10, 0x88FFC83A, false);
      g.drawCenteredString(this.font, this.title, this.width / 2, 10, 16777215);
      g.drawCenteredString(this.font, Component.literal("Choose a power  \u2022  compact mode"), this.width / 2, 25, 16767050);
      g.drawCenteredString(
         this.font,
         Component.literal("Page " + (this.page + 1) + " / " + pageCount() + "   \u2022   " + this.pageHeading()),
         this.width / 2,
         37,
         12578815
      );
      int hovered = this.hoveredAbility((double)mouseX, (double)mouseY);
      if (hovered >= 0) {
         int wrapWidth = Math.max(140, Math.min(300, this.width - 24));
         g.renderTooltip(this.font, this.font.split(Component.literal(Abilities.DESCRIPTIONS[hovered]), wrapWidth), mouseX, mouseY);
      } else {
         g.drawCenteredString(
            this.font,
            Component.literal("Hover a power for its exact effect \u2022 Random Roll stays available"),
            this.width / 2,
            Math.min(this.height - 35, this.navY + 23),
            10463155
         );
      }
   }

   private String pageHeading() {
      return switch (this.page) {
         case 0 -> "Core powers";
         case 1 -> "Summons & combat";
         case 2 -> "Time & space";
         default -> "Ascended powers";
      };
   }

   public boolean isPauseScreen() {
      return false;
   }
}
