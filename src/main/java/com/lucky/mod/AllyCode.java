package com.lucky.mod;

import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * HARDCODED ally law. Every summoned ally, whatever mob it is, obeys exactly this and nothing else:
 *   1. never target, hurt or follow anyone except its owner's orders,
 *   2. protect the owner: attack whoever hurt the owner and whoever the owner attacks,
 *   3. serve the owner: stay close, teleport back if left behind, hit enemies even if the mob has no attack of its own.
 * Native AI that would turn on the owner (target goals, brain memories) is stripped or erased.
 */
public final class AllyCode {
   private AllyCode() {
   }

   public static void bind(Mob mob, Player owner) {
      mob.setPersistenceRequired();
      mob.addTag("lbm_ally");
      mob.addTag("lbm_owner_" + owner.getUUID());
      try {
         mob.targetSelector.removeAllGoals(g -> true); // no native targeting at all: the owner decides
      } catch (Throwable ignored) {
      }
      if (mob instanceof TamableAnimal t) {
         t.tame(owner);
      }
      if (mob instanceof AbstractHorse h) {
         h.setTamed(true);
         h.setOwnerUUID(owner.getUUID());
      }
      mob.setTarget(null);
   }

   public static boolean hasOwnAttack(Mob m) {
      return m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
   }

   /** Called every tick for each living ally by the summoner loop. */
   public static void tick(ServerLevel sw, Player owner, Mob a, Set<UUID> allies, int remaining, double followSq) {
      // law 1: never against the owner, other players or fellow allies (goals AND brain memories)
      LivingEntity tgt = a.getTarget();
      if (tgt != null && (tgt instanceof Player || tgt.getTags().contains("lbm_ally") || allies.contains(tgt.getUUID()))) {
         a.setTarget(null);
         a.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
         a.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
      }
      // law 2: protect: whoever hurt the owner, or whoever the owner is fighting
      LivingEntity foe = owner.getLastHurtByMob();
      if (!valid(foe, owner, allies)) {
         foe = owner.getLastHurtMob();
         if (!valid(foe, owner, allies) || owner.tickCount - owner.getLastHurtMobTimestamp() > 100) {
            foe = null;
         }
      }
      if (foe != null) {
         a.setTarget(foe);
      }
      // law 3: serve: stay close, teleport if left far behind
      if (a.distanceToSqr(owner) > 1600.0) {
         a.teleportTo(owner.getX() + (sw.random.nextDouble() - 0.5) * 4.0, owner.getY(), owner.getZ() + (sw.random.nextDouble() - 0.5) * 4.0);
      } else if (a.getTarget() == null && a.distanceToSqr(owner) > followSq * 0.04) {
         a.getNavigation().moveTo(owner, 1.2);
      }
      // mobs that have no attack of their own (villagers, cows...) still fight for the owner
      LivingEntity t2 = a.getTarget();
      if (t2 != null && t2.isAlive() && !hasOwnAttack(a)) {
         a.getNavigation().moveTo(t2, 1.3);
         if (remaining % 10 == 0 && a.distanceToSqr(t2) < 9.0) {
            DamageSource src = sw.damageSources().mobAttack(a);
            t2.hurt(src, 10.0F);
         }
      }
   }

   private static boolean valid(LivingEntity e, Player owner, Set<UUID> allies) {
      return e != null && e.isAlive() && e != owner && !(e instanceof Player) && !e.getTags().contains("lbm_ally") && !allies.contains(e.getUUID());
   }

   /** Hostile = anything that is an Enemy and not ours. */
   public static boolean isFoe(Mob m, Set<UUID> allies) {
      return m instanceof Enemy && m.isAlive() && !m.getTags().contains("lbm_ally") && !allies.contains(m.getUUID());
   }
}
