package com.lucky.mod;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.registries.ForgeRegistries;

public class WishItemPacket {
   private final ResourceLocation itemId;

   public WishItemPacket(ResourceLocation itemId) {
      this.itemId = itemId;
   }

   public static void encode(WishItemPacket message, FriendlyByteBuf buffer) {
      buffer.writeResourceLocation(message.itemId);
   }

   public static WishItemPacket decode(FriendlyByteBuf buffer) {
      return new WishItemPacket(buffer.readResourceLocation());
   }

   public static void handle(WishItemPacket message, Supplier<Context> context) {
      context.get().enqueueWork(() -> {
         ServerPlayer player = context.get().getSender();
         if (player != null) {
            Item item = (Item)ForgeRegistries.ITEMS.getValue(message.itemId);
            if (item != null && item != Items.AIR) {
               int count = Math.max(1, item.getMaxStackSize());
               ItemStack stack = new ItemStack(item, count);
               if (!player.getInventory().add(stack)) {
                  player.drop(stack, false);
               }

               player.displayClientMessage(Component.literal("\u00a76Lucky Wish granted: \u00a7e" + message.itemId), true);
            } else {
               player.displayClientMessage(Component.literal("That item is not registered on this server."), true);
            }
         }
      });
      context.get().setPacketHandled(true);
   }
}
