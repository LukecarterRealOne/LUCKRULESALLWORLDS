package com.lucky.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WishItemPacket implements CustomPacketPayload {
   public static final Type<WishItemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("luckyblockmod", "wish_item"));
   public static final StreamCodec<FriendlyByteBuf, WishItemPacket> CODEC = StreamCodec.of(
      (buf, m) -> buf.writeResourceLocation(m.itemId), buf -> new WishItemPacket(buf.readResourceLocation()));
   private final ResourceLocation itemId;

   public WishItemPacket(ResourceLocation itemId) {
      this.itemId = itemId;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(WishItemPacket message, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer player) {
            Item item = BuiltInRegistries.ITEM.get(message.itemId);
            if (item != null && item != Items.AIR) {
               int count = Math.max(1, item.getDefaultMaxStackSize());
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
   }
}
