package com.lucky.mod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "luckyblockmod"
)
public class Events {
   private static final String PASSIVE_FLIGHT_ACTIVE = "luckyblockmod_passive_flight_active";
   private static final String PASSIVE_OLD_MAYFLY = "luckyblockmod_passive_old_mayfly";
   private static final String PASSIVE_OLD_FLYING = "luckyblockmod_passive_old_flying";
   private static final String PASSIVE_OLD_SPEED = "luckyblockmod_passive_old_flying_speed";

   public Events() {
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.END && !event.player.level().isClientSide) {
         Player player = event.player;
         boolean hasLuckyBlock = player.getInventory().items.stream().anyMatch(stack -> stack.is((Item)LuckyBlockMod.LUCKY_BLOCK.get()))
            || player.getOffhandItem().is((Item)LuckyBlockMod.LUCKY_BLOCK.get());
         CompoundTag data = player.getPersistentData();
         net.minecraft.world.entity.player.Abilities abilities = player.getAbilities();
         boolean active = data.getBoolean("luckyblockmod_passive_flight_active");
         boolean changed = false;
         if (hasLuckyBlock) {
            if (!active) {
               data.putBoolean("luckyblockmod_passive_old_mayfly", abilities.mayfly);
               data.putBoolean("luckyblockmod_passive_old_flying", abilities.flying);
               data.putFloat("luckyblockmod_passive_old_flying_speed", abilities.getFlyingSpeed());
               data.putBoolean("luckyblockmod_passive_flight_active", true);
            }

            if (!player.isCreative() && !player.isSpectator() && !abilities.mayfly) {
               abilities.mayfly = true;
               changed = true;
            }

            if (Math.abs(abilities.getFlyingSpeed() - 0.1F) > 1.0E-4F) {
               abilities.setFlyingSpeed(0.1F);
               changed = true;
            }
         } else if (active) {
            if (!player.isCreative() && !player.isSpectator()) {
               boolean oldMayfly = data.getBoolean("luckyblockmod_passive_old_mayfly");
               abilities.mayfly = oldMayfly;
               abilities.flying = oldMayfly && data.getBoolean("luckyblockmod_passive_old_flying");
               changed = true;
            }

            float oldSpeed = data.getFloat("luckyblockmod_passive_old_flying_speed");
            if (Math.abs(abilities.getFlyingSpeed() - oldSpeed) > 1.0E-4F) {
               abilities.setFlyingSpeed(oldSpeed);
               changed = true;
            }

            data.remove("luckyblockmod_passive_flight_active");
            data.remove("luckyblockmod_passive_old_mayfly");
            data.remove("luckyblockmod_passive_old_flying");
            data.remove("luckyblockmod_passive_old_flying_speed");
         }

         if (changed && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
         }
      }
   }

   @SubscribeEvent
   public static void onAttack(LivingAttackEvent event) {
      if (event.getEntity() instanceof Player player) {
         DamageSource var5 = event.getSource();
         if (LuckyBlockItem.SHIELDED.contains(player.getUUID()) && !var5.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
         } else {
            Entity attacker = var5.getEntity();
            Entity direct = var5.getDirectEntity();
            if (attacker != null && attacker.getTags().contains("lbm_ally") || direct != null && direct.getTags().contains("lbm_ally")) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLuckyDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
         ItemStack main = player.getMainHandItem();
         ItemStack off = player.getOffhandItem();
         if (!(main.getItem() instanceof LuckyBlockItem) && !(off.getItem() instanceof LuckyBlockItem)) {
            return;
         }

         event.setCanceled(true);
         player.setHealth(Math.max(1.0F, player.getMaxHealth()));
         player.clearFire();
         player.removeAllEffects();
         player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 4));
         player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 2400, 4));
         player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 8));
         player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 5));
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 3));
         player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
         player.displayClientMessage(Component.literal("\u00a7e\u00a7lYou Died??"), false);
         player.displayClientMessage(Component.literal("\u00a7fWith that much power with you?"), false);
         player.displayClientMessage(Component.literal("\u00a7fwow your a noob"), false);
         player.displayClientMessage(Component.literal("\u00a76But aside from that, I won't allow you to die that easily."), false);
         if (player.level() instanceof ServerLevel level) {
            Fx.screen(level, player.position(), 100.0, 1.0F, 4.0F, 30, 16776960);
            Fx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 160, 2.0, 2.0, 2.0, 0.25);
         }

         return;
      }
   }

   private static boolean protectedFromGuard(Entity e) {
      return e instanceof Player || e.getTags().contains("lbm_ally");
   }

   /** Summoned wardens never keep a player or ally as a target: wipe brain memory + anger every other tick. */
   @SubscribeEvent
   public static void onGuardTick(LivingEvent.LivingTickEvent event) {
      if (!(event.getEntity() instanceof Warden warden) || warden.level().isClientSide) return;
      if (!warden.getTags().contains("lbm_ally") || warden.tickCount % 2 != 0) return;
      for (Player p : warden.level().players()) {
         warden.clearAnger(p);
      }
      LivingEntity t = warden.getTarget();
      if (t != null && protectedFromGuard(t)) {
         warden.clearAnger(t);
         warden.setTarget(null);
         warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
         warden.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
      }
   }

   @SubscribeEvent
   public static void onGuardRetarget(LivingChangeTargetEvent event) {
      if (event.getEntity().getTags().contains("lbm_ally") && event.getNewTarget() != null && protectedFromGuard(event.getNewTarget())) {
         event.setNewTarget(null);
      }
   }

   /** Never bring back leftover hypercube display entities after a save/reload. */
   @SubscribeEvent
   public static void onFxJoin(EntityJoinLevelEvent event) {
      if (event.loadedFromDisk() && event.getEntity().getTags().contains("lbm_fx")) {
         event.setCanceled(true);
      }
   }
}
