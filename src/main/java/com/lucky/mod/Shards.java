package com.lucky.mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** The shattered-block state and the 12 abilities it unlocks (ids 38..49). */
public final class Shards {
   public static final int FIRST = 38;
   private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.15F), 1.2F);
   private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F);
   private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.3F);
   private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 1.0F), 1.2F);
   private static final DustParticleOptions[] PRISM = new DustParticleOptions[]{
      new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 1.2F), new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.0F), 1.2F),
      GOLD, new DustParticleOptions(new Vector3f(0.2F, 1.0F, 0.3F), 1.2F), CYAN,
      new DustParticleOptions(new Vector3f(0.3F, 0.3F, 1.0F), 1.2F), VIOLET
   };

   private Shards() {
   }

   public static boolean isShattered(ItemStack s) {
      CompoundTag t = s.getTag();
      return t != null && t.getBoolean("Shattered");
   }

   public static void shatter(ItemStack s) {
      s.getOrCreateTag().putBoolean("Shattered", true);
   }

   private static boolean friend(Entity e, Player u) {
      return e == u || e.getTags().contains("lbm_ally");
   }

   private static void hit(ServerLevel sw, Player u, LivingEntity e, float dmg) {
      if (e instanceof Player p && p.isSpectator()) {
         return;
      }
      e.hurt(sw.damageSources().indirectMagic(u, u), dmg * 10.0F); // shard abilities hit MASSIVELY hard
   }

   private static List<LivingEntity> near(ServerLevel sw, Player u, Vec3 c, double r) {
      return sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> !friend(e, u) && e.distanceToSqr(c) <= r * r);
   }

   private static Vec3 aim(ServerLevel sw, Player u, double range) {
      Vec3 eye = u.getEyePosition();
      Vec3 end = eye.add(u.getLookAngle().scale(range));
      BlockHitResult r = sw.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, u));
      return r.getType() == HitResult.Type.MISS ? end : r.getLocation();
   }

   private static void bolt(ServerLevel sw, Player u, Vec3 from, Vec3 dir, double len, float dmg, ParticleOptions p) {
      Vec3 d = dir.normalize();
      Set<Integer> hits = new HashSet<>();
      for (double s = 1.0; s <= len; s += 1.5) {
         Vec3 q = from.add(d.scale(s));
         Fx.burst(sw, p, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
         if (((int)s) % 6 == 0) {
            Fx.burst(sw, ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.01);
         }
         for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.3), x -> !friend(x, u))) {
            if (hits.add(e.getId())) {
               hit(sw, u, e, dmg);
            }
         }
         BlockPos bp = BlockPos.containing(q);
         if (!sw.getBlockState(bp).getCollisionShape(sw, bp).isEmpty()) {
            Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 6, 0.3, 0.3, 0.3, 0.1);
            break;
         }
      }
   }

   private static void fall(ServerLevel sw, Player u, double x, double y, double z, float dmg, ParticleOptions p) {
      Fx.line(sw, p, x, y + 16.0, z, x, y, z, 6);
      Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 8, 0.4, 0.2, 0.4, 0.15);
      for (LivingEntity e : near(sw, u, new Vec3(x, y + 0.5, z), 2.0)) {
         hit(sw, u, e, dmg);
      }
   }

   public static void cast(ServerLevel sw, Player u, int id) {
      Vec3 eye = u.getEyePosition();
      Vec3 look = u.getLookAngle();
      Vec3 feet = u.position();
      switch (id - FIRST) {
         case 0 -> {
            msg(u, "SHARD BARRAGE!");
            Fx.sound(sw, u.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, 3.0F, 0.8F);
            for (int t = 0; t < 24; t++) {
               Scheduler.later(t + 1, () -> {
                  for (int k = 0; k < 2; k++) {
                     Vec3 d = u.getLookAngle().add((sw.random.nextDouble() - 0.5) * 0.35, (sw.random.nextDouble() - 0.5) * 0.35, (sw.random.nextDouble() - 0.5) * 0.35);
                     bolt(sw, u, u.getEyePosition(), d, 36.0, 30.0F, GOLD);
                  }
               });
            }
         }
         case 1 -> {
            msg(u, "FRACTURE FIELD!");
            Fx.sound(sw, u.blockPosition(), SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
            for (int r = 1; r <= 5; r++) {
               double rad = r * 3.0;
               Scheduler.later(r * 3, () -> {
                  Fx.ring(sw, GOLD, feet.x, feet.y + 0.2, feet.z, rad, 24);
                  for (int c = 0; c < 8; c++) {
                     double a = c * Math.PI / 4.0 + rad * 0.1;
                     Fx.line(sw, WHITE, feet.x + Math.cos(a) * (rad - 3.0), feet.y + 0.15, feet.z + Math.sin(a) * (rad - 3.0), feet.x + Math.cos(a) * rad, feet.y + 0.15, feet.z + Math.sin(a) * rad, 4);
                  }
                  for (LivingEntity e : near(sw, u, feet, rad + 2.0)) {
                     if (e.distanceToSqr(feet) >= (rad - 2.5) * (rad - 2.5)) {
                        hit(sw, u, e, 25.0F);
                        e.push(0.0, 0.9, 0.0);
                        e.hurtMarked = true;
                     }
                  }
               });
            }
         }
         case 2 -> {
            msg(u, "PRISM BEAM!");
            Fx.sound(sw, u.blockPosition(), SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
            double base = Math.atan2(look.z, look.x);
            for (int i = 0; i < 7; i++) {
               double a = base + (i - 3) * 0.17;
               bolt(sw, u, eye, new Vec3(Math.cos(a), look.y, Math.sin(a)), 44.0, 45.0F, PRISM[i]);
            }
         }
         case 3 -> {
            msg(u, "SHARD ORBIT!");
            for (int t = 0; t < 100; t++) {
               int tick = t;
               Scheduler.later(t * 2 + 1, () -> {
                  if (!u.isAlive()) {
                     return;
                  }
                  for (int s = 0; s < 8; s++) {
                     double a = tick * 0.35 + s * Math.PI / 4.0;
                     double px = u.getX() + Math.cos(a) * 3.0;
                     double py = u.getY() + 1.0 + Math.sin(tick * 0.2 + s) * 0.6;
                     double pz = u.getZ() + Math.sin(a) * 3.0;
                     Fx.burst(sw, s % 2 == 0 ? GOLD : WHITE, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
                     if (tick % 5 == 0) {
                        for (LivingEntity e : near(sw, u, new Vec3(px, py, pz), 1.8)) {
                           hit(sw, u, e, 14.0F);
                        }
                     }
                  }
               });
            }
         }
         case 4 -> {
            msg(u, "MIRROR STEP!");
            Vec3 dest = aim(sw, u, 18.0);
            Vec3 to = dest.subtract(look.scale(1.0));
            double len = eye.distanceTo(to);
            for (double s = 0.0; s <= len; s += 1.0) {
               Vec3 q = eye.add(look.scale(s));
               Fx.burst(sw, s % 3 == 0 ? CYAN : WHITE, q.x, q.y - 0.8, q.z, 2, 0.25, 0.5, 0.25, 0.01);
               for (LivingEntity e : near(sw, u, q, 2.5)) {
                  hit(sw, u, e, 40.0F);
               }
            }
            Fx.sound(sw, u.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.5F);
            if (u instanceof ServerPlayer sp) {
               sp.teleportTo(to.x, to.y - 1.6, to.z);
            }
            Fx.ring(sw, CYAN, to.x, to.y - 1.5, to.z, 2.5, 18);
         }
         case 5 -> {
            msg(u, "GRAVITY SHATTER!");
            Vec3 c = aim(sw, u, 40.0);
            for (int t = 0; t < 20; t++) {
               int tick = t;
               Scheduler.later(t * 2 + 1, () -> {
                  Shapes.poly4(sw, Shapes.Poly.CELL5, GOLD, ParticleTypes.END_ROD, c.x, c.y + 3.0, c.z, 3.0 - tick * 0.12, tick * 0.3, 3);
                  for (LivingEntity e : near(sw, u, c, 14.0)) {
                     Vec3 in = c.subtract(e.position());
                     if (in.lengthSqr() > 0.2) {
                        Vec3 n = in.normalize();
                        e.push(n.x * 0.4, 0.08, n.z * 0.4);
                        e.hurtMarked = true;
                     }
                  }
               });
            }
            Scheduler.later(44, () -> {
               Fx.shatter(sw, GOLD, WHITE, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, c.x, c.y + 3.0, c.z, 3.0, 1.0);
               Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GENERIC_EXPLODE, 4.0F, 0.6F);
               Fx.ring(sw, GOLD, c.x, c.y + 0.3, c.z, 12.0, 30);
               for (LivingEntity e : near(sw, u, c, 12.0)) {
                  hit(sw, u, e, 80.0F);
                  e.push(0.0, 1.4, 0.0);
                  e.hurtMarked = true;
               }
            });
         }
         case 6 -> {
            msg(u, "TIME CRACK!");
            Fx.sound(sw, u.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.4F);
            Fx.ring(sw, CYAN, feet.x, feet.y + 0.2, feet.z, 20.0, 40);
            Shapes.armillary(sw, CYAN, WHITE, feet.x, feet.y + 1.5, feet.z, 3.0, 0.0);
            for (LivingEntity e : near(sw, u, feet, 20.0)) {
               e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 9));
               e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 3));
               e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
            }
            u.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 160, 3));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 2));
         }
         case 7 -> {
            msg(u, "SHARD RAIN!");
            Vec3 c = aim(sw, u, 60.0);
            for (int t = 0; t < 40; t++) {
               Scheduler.later(t + 1, () -> {
                  for (int k = 0; k < 2; k++) {
                     fall(sw, u, c.x + (sw.random.nextDouble() - 0.5) * 20.0, c.y, c.z + (sw.random.nextDouble() - 0.5) * 20.0, 35.0F, sw.random.nextBoolean() ? GOLD : WHITE);
                  }
               });
            }
         }
         case 8 -> {
            msg(u, "VOID RIFT!");
            Vec3 c = aim(sw, u, 50.0).add(0.0, 2.0, 0.0);
            Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.6F);
            for (int t = 0; t < 12; t++) {
               int tick = t;
               Scheduler.later(t * 5 + 1, () -> {
                  Shapes.armillary(sw, VIOLET, WHITE, c.x, c.y, c.z, 2.5 + tick * 0.2, tick * 0.5);
                  Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 3.0, 3.0, 3.0, 0.2);
                  for (LivingEntity e : near(sw, u, c, 16.0)) {
                     Vec3 in = c.subtract(e.position());
                     if (in.lengthSqr() > 0.2) {
                        Vec3 n = in.normalize();
                        e.push(n.x * 0.35, n.y * 0.2, n.z * 0.35);
                        e.hurtMarked = true;
                     }
                     hit(sw, u, e, 15.0F);
                     e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                  }
               });
            }
            Scheduler.later(62, () -> {
               Fx.epicImpact(sw, c, VIOLET, ParticleTypes.REVERSE_PORTAL, 8.0, 11897599);
               for (LivingEntity e : near(sw, u, c, 7.0)) {
                  hit(sw, u, e, 100.0F);
               }
            });
         }
         case 9 -> {
            msg(u, "CRYSTAL ARMOR!");
            Fx.sound(sw, u.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 3.0F, 0.7F);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 800, 3));
            u.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 800, 6));
            u.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 800, 2));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 800, 1));
            u.addEffect(new MobEffectInstance(MobEffects.GLOWING, 800, 0));
            for (int t = 0; t < 40; t++) {
               int tick = t;
               Scheduler.later(t + 1, () -> {
                  if (u.isAlive()) {
                     Fx.helix(sw, GOLD, WHITE, u.getX(), u.getY(), u.getZ(), 1.2, 2.2, tick * 0.4);
                  }
               });
            }
         }
         case 10 -> {
            msg(u, "SHARD STORM!");
            for (int t = 0; t < 50; t++) {
               Scheduler.later(t * 4 + 1, () -> {
                  if (!u.isAlive()) {
                     return;
                  }
                  for (int k = 0; k < 5; k++) {
                     double a = sw.random.nextDouble() * Math.PI * 2.0;
                     double r = 3.0 + sw.random.nextDouble() * 10.0;
                     fall(sw, u, u.getX() + Math.cos(a) * r, u.getY(), u.getZ() + Math.sin(a) * r, 25.0F, sw.random.nextBoolean() ? VIOLET : GOLD);
                  }
               });
            }
         }
         default -> {
            msg(u, "REASSEMBLE!");
            u.setHealth(u.getMaxHealth());
            u.removeEffect(MobEffects.POISON);
            u.removeEffect(MobEffects.WITHER);
            u.removeEffect(MobEffects.WEAKNESS);
            u.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 2));
            Fx.sound(sw, u.blockPosition(), SoundEvents.TOTEM_USE, 2.0F, 1.2F);
            Fx.epicImpact(sw, feet.add(0.0, 1.0, 0.0), GOLD, ParticleTypes.END_ROD, 10.0, 16773222);
            for (LivingEntity e : near(sw, u, feet, 10.0)) {
               hit(sw, u, e, 60.0F);
               Vec3 away = e.position().subtract(feet);
               away = away.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
               e.push(away.x * 1.8, 0.7, away.z * 1.8);
               e.hurtMarked = true;
            }
         }
      }
   }

   private static void msg(Player p, String s) {
      p.displayClientMessage(net.minecraft.network.chat.Component.literal("§e§l" + s), true);
   }
}
