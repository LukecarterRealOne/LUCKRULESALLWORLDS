package com.lucky.mod;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DimensionPickerScreen extends Screen {
   private static final int PER_PAGE = 8;
   private final List<String> dimensions;
   private final String selected;
   private int page;
   private int buttonWidth;
   private int buttonHeight;
   private int startX;
   private int startY;
   private int rowStep;
   private int navY;

   public DimensionPickerScreen(List<String> dimensions, String selected) {
      super(Component.literal("Choose Destination Dimension"));
      this.dimensions = List.copyOf(dimensions);
      this.selected = selected == null ? "" : selected;
   }

   private int pageCount() {
      return Math.max(1, (this.dimensions.size() + 8 - 1) / 8);
   }

   protected void init() {
      this.clearWidgets();
      this.page = Math.max(0, Math.min(this.page, this.pageCount() - 1));
      int margin = Math.max(10, this.width / 24);
      int gap = 6;
      this.buttonWidth = Math.max(80, (this.width - margin * 2 - gap) / 2);
      this.buttonHeight = 22;
      this.rowStep = 27;
      this.startX = margin;
      this.startY = 62;

      for (int slot = 0; slot < 8; slot++) {
         int index = this.page * 8 + slot;
         if (index >= this.dimensions.size()) {
            break;
         }

         String id = this.dimensions.get(index);
         int x = this.startX + slot % 2 * (this.buttonWidth + gap);
         int y = this.startY + slot / 2 * this.rowStep;
         boolean isSelected = id.equals(this.selected);
         String shown = (isSelected ? "\u2713 " : "") + this.abbreviate(id, this.buttonWidth - 14);
         this.addRenderableWidget(
            Button.builder(Component.literal(shown), b -> this.select(id))
               .bounds(x, y, this.buttonWidth, this.buttonHeight)
               .tooltip(Tooltip.create(Component.literal(id)))
               .build()
         );
      }

      this.navY = this.height - 48;
      int navGap = 5;
      int navWidth = Math.max(52, (this.width - 24 - navGap * 2) / 3);
      int navX = (this.width - (navWidth * 3 + navGap * 2)) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("\u2039 Prev"), b -> this.changePage(-1)).bounds(navX, this.navY, navWidth, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Next \u203a"), b -> this.changePage(1)).bounds(navX + navWidth + navGap, this.navY, navWidth, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(navX + (navWidth + navGap) * 2, this.navY, navWidth, 20).build()
      );
   }

   private String abbreviate(String id, int maxPixels) {
      if (this.font.width(id) <= maxPixels) {
         return id;
      } else {
         String candidate = id;

         while (candidate.length() > 5 && this.font.width(candidate + "\u2026") > maxPixels) {
            candidate = candidate.substring(0, candidate.length() - 1);
         }

         return candidate + "\u2026";
      }
   }

   private void changePage(int delta) {
      this.page = (this.page + delta + this.pageCount()) % this.pageCount();
      this.rebuildWidgets();
   }

   private void select(String id) {
      net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ChooseDimensionPacket(id));
      this.onClose();
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(g);
      super.render(g, mouseX, mouseY, partialTick);
      g.drawCenteredString(this.font, this.title, this.width / 2, 10, 16777215);
      g.drawCenteredString(this.font, Component.literal("Available active dimensions \u2022 vanilla + modded"), this.width / 2, 27, 12578815);
      String selectedLabel = this.selected.isEmpty() ? "none" : this.abbreviate(this.selected, Math.max(120, this.width - 36));
      g.drawCenteredString(this.font, Component.literal("Selected: " + selectedLabel), this.width / 2, 42, 16767050);
      g.drawCenteredString(this.font, Component.literal("Page " + (this.page + 1) + " / " + this.pageCount()), this.width / 2, this.height - 25, 10463155);
      if (this.dimensions.isEmpty()) {
         g.drawCenteredString(this.font, Component.literal("The server reported no active dimensions."), this.width / 2, this.height / 2, 16742263);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
