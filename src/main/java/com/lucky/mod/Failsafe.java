package com.lucky.mod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * KICK FAILSAFE. If something (a server-side mod, a plugin, /kick) tries to kick a player who carries the Lucky Block,
 * the block is SACRIFICED: the kick is cancelled and every mob within 500 blocks is deleted. Only works while the player actually has the block;
 * normal leaving (quit, shutdown, timeout) is never touched.
 */
public final class Failsafe {
   private Failsafe() {
   }

   /** Swaps the player's network listener for one whose disconnect() runs the failsafe first. */
   public static void guard(ServerPlayer sp) {
      if (sp.connection != null && !(sp.connection instanceof GuardedListener) && sp.getServer() != null) {
         try {
            new GuardedListener(sp.getServer(), sp.connection.connection, sp);
         } catch (Throwable ignored) {
         }
      }
   }

   private static boolean legit(Component reason) {
      if (reason != null && reason.getContents() instanceof TranslatableContents tc) {
         String k = tc.getKey();
         return k.contains("server_shutdown") || k.contains("quitting") || k.contains("timeout") || k.contains("disconnect.lost")
            || k.contains("end_of_stream") || k.contains("duplicate_login");
      }
      return false;
   }

   /** @return true if the kick must be cancelled (block sacrificed). */
   public static boolean onKick(ServerPlayer p, Component reason) {
      if (p == null || p.getServer() == null || !p.getServer().isRunning() || legit(reason)) {
         return false;
      }
      ItemStack block = ItemStack.EMPTY;
      for (ItemStack s : p.getInventory().items) {
         if (s.getItem() instanceof LuckyBlockItem) {
            block = s;
            break;
         }
      }
      if (block.isEmpty() && p.getOffhandItem().getItem() instanceof LuckyBlockItem) {
         block = p.getOffhandItem();
      }
      if (block.isEmpty() || !(p.level() instanceof ServerLevel sw)) {
         return false; // no block to sacrifice: not possible
      }
      block.shrink(1);
      int wiped = 0;
      for (Mob m : sw.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(500.0, 400.0, 500.0))) {
         if (wiped++ < 60) {
            Fx.burst(sw, ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
         }
         m.discard();
      }
      Fx.sound(sw, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
      Fx.sound(sw, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
      Fx.screen(sw, p.position(), 64.0, 1.0F, 3.0F, 40, 16766720);
      Fx.ring(sw, ParticleTypes.END_ROD, p.getX(), p.getY() + 0.3, p.getZ(), 6.0, 60);
      p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§6§lFAILSAFE")));
      p.displayClientMessage(Component.literal("§6The Lucky Block sacrificed itself: kick denied, " + wiped + " mob(s) erased within 500 blocks."), false);
      return true;
   }
}
