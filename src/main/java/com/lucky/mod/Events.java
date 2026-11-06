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
      if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
         return;
      }
      ItemStack held = player.getMainHandItem().getItem() instanceof LuckyBlockItem ? player.getMainHandItem()
         : (player.getOffhandItem().getItem() instanceof LuckyBlockItem ? player.getOffhandItem() : ItemStack.EMPTY);
      if (held.isEmpty() || Shards.isShattered(held)) {
         return; // no block, or it is already broken: death is real
      }
      // The block takes the killing blow: it SHATTERS (once), a cutscene plays, then you come back with ADAPTATION.
      event.setCanceled(true);
      Shards.shatter(held);
      if (player instanceof ServerPlayer sp) {
         Cutscene.start(sp);
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

   /** A shattered block leaks gold light from its cracks while carried; sparks drift off the hand, shards fall. */
   @SubscribeEvent
   public static void onShardAura(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
      if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.player.level().isClientSide) return;
      Player p = event.player;
      if (p.tickCount % 4 != 0) return;
      boolean held = (p.getMainHandItem().getItem() instanceof LuckyBlockItem && Shards.isShattered(p.getMainHandItem()))
         || (p.getOffhandItem().getItem() instanceof LuckyBlockItem && Shards.isShattered(p.getOffhandItem()));
      if (!held || !(p.level() instanceof ServerLevel sw)) return;
      net.minecraft.world.phys.Vec3 look = p.getLookAngle();
      double hx = p.getX() + look.x * 0.7 - look.z * 0.35;
      double hy = p.getY() + 1.2;
      double hz = p.getZ() + look.z * 0.7 + look.x * 0.35;
      Fx.burst(sw, ParticleTypes.END_ROD, hx, hy, hz, 1, 0.12, 0.12, 0.12, 0.02);
      Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, hx, hy, hz, 2, 0.2, 0.2, 0.2, 0.08);
      net.minecraft.core.particles.BlockParticleOption crack = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
      Fx.burst(sw, crack, hx, hy + 0.4, hz, 1, 0.15, 0.0, 0.15, 0.0);
      if (p.tickCount % 20 == 0) {
         Fx.burst(sw, ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.5, 0.6, 0.5, 0.03);
      }
   }
}
