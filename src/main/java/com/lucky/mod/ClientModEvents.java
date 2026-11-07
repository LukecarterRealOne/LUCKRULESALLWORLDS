package com.lucky.mod;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "luckyblockmod",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public class ClientModEvents {
   public static final KeyMapping OPEN_MENU = new KeyMapping("key.luckyblockmod.menu", Type.KEYSYM, 71, "key.categories.luckyblockmod");

   public ClientModEvents() {
   }

   @SubscribeEvent
   public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
      event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
         LuckyBlockMod.LUCKY_BLOCK.get(),
         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("luckyblockmod", "shattered"),
         (stack, level, entity, seed) -> Shards.isShattered(stack) ? 1.0F : 0.0F));
   }

   @SubscribeEvent
   public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
      event.register(OPEN_MENU);
   }
}
