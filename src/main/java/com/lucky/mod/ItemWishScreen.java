package com.lucky.mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public class ItemWishScreen extends Screen {
   private static final int PAGE_SIZE = 10;
   private final List<ResourceLocation> allItems = new ArrayList<>();
   private final List<ResourceLocation> filteredItems = new ArrayList<>();
   private final Button[] itemButtons = new Button[10];
   private EditBox searchBox;
   private Button previousButton;
   private Button nextButton;
   private int page;
   private int startX;
   private int startY;
   private int buttonWidth;
   private int buttonHeight;
   private int rowStep;

   public ItemWishScreen() {
      super(Component.literal("Lucky Wish Catalogue"));

      for (Item item : BuiltInRegistries.ITEM) {
         if (item != null && item != Items.AIR) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
               this.allItems.add(id);
            }
         }
      }

      this.allItems.sort(Comparator.comparing(ResourceLocation::toString));
   }

   protected void init() {
      int margin = Math.max(8, this.width / 24);
      int gap = 6;
      this.buttonWidth = Math.max(80, (this.width - margin * 2 - gap) / 2);
      this.buttonHeight = this.height < 250 ? 18 : 20;
      this.rowStep = this.buttonHeight + 3;
      this.startX = margin;
      this.startY = 65;
      this.searchBox = new EditBox(this.font, margin, 38, this.width - margin * 2, 20, Component.literal("Search items"));
      this.searchBox.setMaxLength(160);
      this.searchBox.setHint(Component.literal("Search by item name or mod:id"));
      this.searchBox.setResponder(ignored -> this.refreshItems());
      this.addRenderableWidget(this.searchBox);

      for (int slot = 0; slot < 10; slot++) {
         int buttonSlot = slot;
         int x = this.startX + slot % 2 * (this.buttonWidth + gap);
         int y = this.startY + slot / 2 * this.rowStep;
         this.itemButtons[slot] = Button.builder(Component.literal(""), b -> this.choose(buttonSlot))
            .bounds(x, y, this.buttonWidth, this.buttonHeight)
            .build();
         this.addRenderableWidget(this.itemButtons[slot]);
      }

      int navY = this.height - 52;
      int navWidth = Math.min(100, (this.width - 30) / 3);
      this.previousButton = Button.builder(Component.literal("\u2039 Prev"), b -> {
         this.page = Math.max(0, this.page - 1);
         this.refreshItems();
      }).bounds(this.width / 2 - navWidth - 4, navY, navWidth, 20).build();
      this.addRenderableWidget(this.previousButton);
      this.nextButton = Button.builder(Component.literal("Next \u203a"), b -> {
         this.page = Math.min(this.pageCount() - 1, this.page + 1);
         this.refreshItems();
      }).bounds(this.width / 2 + 4, navY, navWidth, 20).build();
      this.addRenderableWidget(this.nextButton);
      int closeWidth = Math.min(220, this.width - 24);
      this.addRenderableWidget(
         Button.builder(Component.literal("Cancel"), b -> this.onClose())
            .bounds((this.width - closeWidth) / 2, this.height - 26, closeWidth, 20)
            .build()
      );
      this.setInitialFocus(this.searchBox);
      this.refreshItems();
   }

   private int pageCount() {
      return Math.max(1, (this.filteredItems.size() + 10 - 1) / 10);
   }

   private void refreshItems() {
      String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
      this.filteredItems.clear();

      for (ResourceLocation id : this.allItems) {
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         String itemName = item == null ? "" : item.getDescription().getString().toLowerCase(Locale.ROOT);
         if (query.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(query) || itemName.contains(query)) {
            this.filteredItems.add(id);
         }
      }

      this.page = Math.max(0, Math.min(this.page, this.pageCount() - 1));

      for (int slot = 0; slot < 10; slot++) {
         int index = this.page * 10 + slot;
         Button button = this.itemButtons[slot];
         if (button != null) {
            boolean available = index < this.filteredItems.size();
            button.active = available;
            button.setMessage(Component.literal(available ? shortName(this.filteredItems.get(index)) : "\u2014"));
         }
      }

      if (this.previousButton != null) {
         this.previousButton.active = this.page > 0;
      }

      if (this.nextButton != null) {
         this.nextButton.active = this.page + 1 < this.pageCount();
      }
   }

   private static String shortName(ResourceLocation id) {
      Item item = (Item)BuiltInRegistries.ITEM.get(id);
      String name = item == null ? id.toString() : item.getDescription().getString();
      return name.length() <= 24 ? name : name.substring(0, 21) + "\u2026";
   }

   private void choose(int slot) {
      int index = this.page * 10 + slot;
      if (index >= 0 && index < this.filteredItems.size()) {
         net.neoforged.neoforge.network.PacketDistributor.sendToServer(new WishItemPacket(this.filteredItems.get(index)));
         this.onClose();
      }
   }

   private String hoveredId(double mouseX, double mouseY) {
      for (int slot = 0; slot < 10; slot++) {
         int index = this.page * 10 + slot;
         if (index >= this.filteredItems.size()) {
            break;
         }

         int x = this.startX + slot % 2 * (this.buttonWidth + 6);
         int y = this.startY + slot / 2 * this.rowStep;
         if (mouseX >= (double)x && mouseX < (double)(x + this.buttonWidth) && mouseY >= (double)y && mouseY < (double)(y + this.buttonHeight)) {
            ResourceLocation id = this.filteredItems.get(index);
            Item item = (Item)BuiltInRegistries.ITEM.get(id);
            String name = item == null ? id.toString() : item.getDescription().getString();
            return name + "  \u2022  " + id;
         }
      }

      return "Search by item name or mod:id; select one to receive a stack.";
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics);
      super.render(graphics, mouseX, mouseY, partialTick);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 16777215);
      graphics.drawCenteredString(
         this.font,
         Component.literal(this.filteredItems.size() + " matching items  \u2022  Page " + (this.page + 1) + " / " + this.pageCount()),
         this.width / 2,
         26,
         16767050
      );
      graphics.drawCenteredString(this.font, Component.literal(this.hoveredId((double)mouseX, (double)mouseY)), this.width / 2, this.height - 38, 12578815);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
