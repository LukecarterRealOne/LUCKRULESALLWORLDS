package com.lucky.mod;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of the "the block shattered" death cutscene. Length must match ClientState.CUTSCENE_LEN. */
public final class Cutscene {
   public static final int REVIVE_AT = 470;
   public static final String TAG = "lbm_cutscene";

   private Cutscene() {
   }

   public static void start(ServerPlayer p) {
      if (p.getTags().contains(TAG)) {
         return;
      }
      p.addTag(TAG);
      p.setHealth(1.0F);
      p.clearFire();
      p.setInvulnerable(true);
      p.removeAllEffects();
      freeze(p);
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new CutscenePacket());
      Scheduler.later(REVIVE_AT, () -> revive(p));
   }

   private static void freeze(ServerPlayer p) {
      p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 10, false, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.JUMP, 600, 250, false, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 10, false, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 10, false, false, false));
   }

   /** Also used on login if the player quit mid-cutscene. */
   public static void clear(ServerPlayer p) {
      p.removeTag(TAG);
      p.setInvulnerable(false);
      p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
      p.removeEffect(MobEffects.JUMP);
      p.removeEffect(MobEffects.DIG_SLOWDOWN);
      p.removeEffect(MobEffects.WEAKNESS);
   }

   public static void revive(ServerPlayer p) {
      if (p.isRemoved() || !p.getTags().contains(TAG)) {
         return;
      }
      clear(p);
      p.setHealth(p.getMaxHealth());
      p.getFoodData().setFoodLevel(20);
      p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 4));
      p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 3));
      p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 6));
      p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
      Adapt.grant(p);
      p.displayClientMessage(Component.literal("§e§lTHE LUCKY BLOCK SHATTERS"), false);
      p.displayClientMessage(Component.literal("§fYou are back. It will not save you twice."), false);
      p.displayClientMessage(Component.literal("§6New power: §e§lADAPTATION §r§6- you adapt to anything that hurts you."), false);
      p.displayClientMessage(Component.literal("§6From the cracks: 12 new Shard abilities (menu pages 4-5)."), false);
      if (p.level() instanceof ServerLevel level) {
         Fx.screen(level, p.position(), 100.0, 1.0F, 5.0F, 40, 16776960);
         BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.defaultBlockState());
         Fx.burst(level, crack, p.getX(), p.getY() + 1.2, p.getZ(), 150, 1.2, 1.2, 1.2, 0.35);
         Fx.burst(level, ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 60, 1.6, 1.6, 1.6, 0.5);
         Fx.shatter(level, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.FIREWORK, ParticleTypes.CRIT, p.getX(), p.getY() + 1.2, p.getZ(), 1.5, 0.5);
         Fx.sound(level, p.blockPosition(), SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
         Fx.sound(level, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, 4.0F, 0.6F);
         Fx.sound(level, p.blockPosition(), SoundEvents.TOTEM_USE, 2.0F, 0.6F);
         Adapt.chime(level, p);
      }
   }
}
