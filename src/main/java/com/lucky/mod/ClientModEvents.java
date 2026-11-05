package com.lucky.mod;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

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
   public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
      event.register(OPEN_MENU);
   }
}
