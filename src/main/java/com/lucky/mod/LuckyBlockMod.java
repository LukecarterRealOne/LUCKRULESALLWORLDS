package com.lucky.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("luckyblockmod")
public class LuckyBlockMod {
   public static final String MOD_ID = "luckyblockmod";
   public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("luckyblockmod");
   public static final DeferredItem<Item> LUCKY_BLOCK = ITEMS.register("lucky_block", () -> new LuckyBlockItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "luckyblockmod");
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
      "main",
      () -> CreativeModeTab.builder()
            .title(Component.literal("Lucky Block"))
            .icon(() -> new ItemStack((ItemLike)LUCKY_BLOCK.get()))
            .displayItems((params, output) -> output.accept((ItemLike)LUCKY_BLOCK.get()))
            .build()
   );

   public LuckyBlockMod(IEventBus bus) {
      ITEMS.register(bus);
      TABS.register(bus);
      bus.addListener(this::addToTabs);
      bus.addListener(this::registerPayloads);
   }

   private void registerPayloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1");
      r.playToClient(FxPacket.TYPE, FxPacket.CODEC, FxPacket::handle);
      r.playToServer(SelectPacket.TYPE, SelectPacket.CODEC, SelectPacket::handle);
      r.playToClient(OpenItemWishPacket.TYPE, OpenItemWishPacket.CODEC, OpenItemWishPacket::handle);
      r.playToServer(WishItemPacket.TYPE, WishItemPacket.CODEC, WishItemPacket::handle);
      r.playToClient(OpenDimensionPickerPacket.TYPE, OpenDimensionPickerPacket.CODEC, OpenDimensionPickerPacket::handle);
      r.playToServer(ChooseDimensionPacket.TYPE, ChooseDimensionPacket.CODEC, ChooseDimensionPacket::handle);
      r.playToClient(MeterPacket.TYPE, MeterPacket.CODEC, MeterPacket::handle);
      r.playToClient(CutscenePacket.TYPE, CutscenePacket.CODEC, CutscenePacket::handle);
   }

   private void addToTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
         event.accept(LUCKY_BLOCK);
      }
   }
}
