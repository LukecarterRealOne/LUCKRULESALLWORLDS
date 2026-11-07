package com.lucky.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("luckyblockmod")
public class LuckyBlockMod {
   public static final String MOD_ID = "luckyblockmod";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "luckyblockmod");
   public static final RegistryObject<Item> LUCKY_BLOCK = ITEMS.register("lucky_block", () -> new LuckyBlockItem(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "luckyblockmod");
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
      "main",
      () -> CreativeModeTab.builder()
            .title(Component.literal("Lucky Block"))
            .icon(() -> new ItemStack((ItemLike)LUCKY_BLOCK.get()))
            .displayItems((params, output) -> output.accept((ItemLike)LUCKY_BLOCK.get()))
            .build()
   );
   public static final String PROTOCOL = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation("luckyblockmod", "main"), () -> "1", "1"::equals, "1"::equals
   );

   public LuckyBlockMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ITEMS.register(bus);
      TABS.register(bus);
      bus.addListener(this::commonSetup);
      bus.addListener(this::addToTabs);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      int id = 0;
      CHANNEL.registerMessage(id++, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle);
      CHANNEL.registerMessage(id++, SelectPacket.class, SelectPacket::encode, SelectPacket::decode, SelectPacket::handle);
      CHANNEL.registerMessage(id++, OpenItemWishPacket.class, OpenItemWishPacket::encode, OpenItemWishPacket::decode, OpenItemWishPacket::handle);
      CHANNEL.registerMessage(id++, WishItemPacket.class, WishItemPacket::encode, WishItemPacket::decode, WishItemPacket::handle);
      CHANNEL.registerMessage(
         id++, OpenDimensionPickerPacket.class, OpenDimensionPickerPacket::encode, OpenDimensionPickerPacket::decode, OpenDimensionPickerPacket::handle
      );
      CHANNEL.registerMessage(id++, ChooseDimensionPacket.class, ChooseDimensionPacket::encode, ChooseDimensionPacket::decode, ChooseDimensionPacket::handle);
      CHANNEL.registerMessage(id++, MeterPacket.class, MeterPacket::encode, MeterPacket::decode, MeterPacket::handle);
      CHANNEL.registerMessage(id++, CutscenePacket.class, CutscenePacket::encode, CutscenePacket::decode, CutscenePacket::handle);
   }

   private void addToTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
         event.accept(LUCKY_BLOCK);
      }
   }
}
