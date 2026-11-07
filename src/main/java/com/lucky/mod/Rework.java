package com.lucky.mod;

import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Presentation overhaul for the base abilities 0-35 (Golden Might / the two big tesseracts have their own rework).
 * Runs right after the ability's own logic: layered particles, sigils, sound design. Gameplay numbers are untouched.
 */
public final class Rework {
   private static final DustParticleOptions GOLD = dust(1.0F, 0.82F, 0.15F);
   private static final DustParticleOptions WHITE = dust(1.0F, 1.0F, 0.95F);
   private static final DustParticleOptions RED = dust(1.0F, 0.15F, 0.1F);
   private static final DustParticleOptions ORANGE = dust(1.0F, 0.5F, 0.05F);
   private static final DustParticleOptions CYAN = dust(0.2F, 0.9F, 1.0F);
   private static final DustParticleOptions BLUE = dust(0.25F, 0.45F, 1.0F);
   private static final DustParticleOptions GREEN = dust(0.3F, 1.0F, 0.4F);
   private static final DustParticleOptions VIOLET = dust(0.62F, 0.25F, 1.0F);

   private Rework() {
   }

   private static DustParticleOptions dust(float r, float g, float b) {
      return new DustParticleOptions(new Vector3f(r, g, b), 1.4F);
   }

   private static Vec3 aim(ServerLevel sw, Player u, double range) {
      Vec3 eye = u.getEyePosition();
      Vec3 end = eye.add(u.getLookAngle().scale(range));
      BlockHitResult r = sw.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, u));
      return r.getType() == HitResult.Type.MISS ? end : r.getLocation();
   }

   private static void loop(int ticks, int step, IntConsumer body) {
      for (int t = 0; t <= ticks; t += step) {
         int tick = t;
         Scheduler.later(t + 1, () -> body.accept(tick));
      }
   }

   private static void snd(ServerLevel sw, Vec3 p, net.minecraft.sounds.SoundEvent s, float v, float pitch) {
      Fx.sound(sw, BlockPos.containing(p.x, p.y, p.z), s, v, pitch);
   }

   /** A twisting ribbon of particles around a ray. */
   private static void spiral(ServerLevel sw, ParticleOptions p, Vec3 from, Vec3 dir, double len, double radius, double phase) {
      Vec3 d = dir.normalize();
      Vec3 tmp = Math.abs(d.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
      Vec3 u = d.cross(tmp).normalize();
      Vec3 w = d.cross(u).normalize();
      for (double s = 1.0; s <= len; s += 1.6) {
         double a = phase + s * 0.9;
         Vec3 c = from.add(d.scale(s)).add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
         Fx.burst(sw, p, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static void aura(ServerLevel sw, Player u, ParticleOptions a, ParticleOptions b, int ticks) {
      loop(ticks, 2, t -> {
         if (u.isAlive()) {
            Fx.helix(sw, a, b, u.getX(), u.getY(), u.getZ(), 1.1, 2.3, t * 0.45);
         }
      });
   }

   public static void after(ServerLevel sw, Player u, int id) {
      Vec3 eye = u.getEyePosition();
      Vec3 look = u.getLookAngle();
      Vec3 feet = u.position();
      Vec3 target = aim(sw, u, 60.0);
      switch (id) {
         case 0 -> loop(40, 2, t -> {
            Vec3 e = u.getEyePosition();
            Vec3 l = u.getLookAngle();
            spiral(sw, GOLD, e, l, 34.0, 0.9, t * 0.5);
            spiral(sw, WHITE, e, l, 34.0, 0.5, -t * 0.7);
            Fx.burst(sw, ParticleTypes.END_ROD, target.x, target.y, target.z, 3, 0.4, 0.4, 0.4, 0.1);
         });
         case 1 -> {
            Fx.rune(sw, WHITE, CYAN, feet.x, feet.y + 0.2, feet.z, 20.0, 0.0);
            loop(20, 4, t -> Fx.rune(sw, WHITE, CYAN, feet.x, feet.y + 0.2, feet.z, 20.0 - t * 0.3, t * 0.2));
            for (int i = 0; i < 8; i++) {
               double a = i * Math.PI / 4.0;
               Fx.pillar(sw, ParticleTypes.ELECTRIC_SPARK, feet.x + Math.cos(a) * 20.0, feet.y, feet.y + 40.0, feet.z + Math.sin(a) * 20.0);
            }
            snd(sw, feet, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.7F);
         }
         case 2 -> {
            Fx.laserMark(sw, ORANGE, RED, target.x, target.y, target.z, 30.0, 0.0);
            loop(36, 4, t -> Fx.rune(sw, ORANGE, RED, target.x, target.y + 0.2, target.z, 10.0, t * 0.15));
         }
         case 3 -> {
            aura(sw, u, GREEN, WHITE, 30);
            loop(20, 5, t -> Fx.ring(sw, GREEN, feet.x, feet.y + 0.2, feet.z, 3.0 + t * 0.6, 24));
            snd(sw, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.4F);
            snd(sw, feet, SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.6F);
         }
         case 4 -> loop(30, 2, t -> {
            Fx.helix(sw, WHITE, CYAN, u.getX(), u.getY() - 2.0, u.getZ(), 0.9, 4.0, t * 0.5);
            Fx.burst(sw, ParticleTypes.CLOUD, u.getX(), u.getY(), u.getZ(), 4, 0.3, 0.1, 0.3, 0.05);
         });
         case 5 -> {
            Fx.rune(sw, GOLD, WHITE, feet.x, feet.y + 0.2, feet.z, 8.0, 0.0);
            Fx.burst(sw, ParticleTypes.FIREWORK, feet.x, feet.y + 8.0, feet.z, 40, 5.0, 2.0, 5.0, 0.2);
            snd(sw, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.9F);
         }
         case 6 -> loop(40, 3, t -> {
            Shapes.poly4(sw, Shapes.Poly.CELL5, VIOLET, ParticleTypes.END_ROD, target.x, target.y + 4.0, target.z, 2.4, t * 0.25, 3);
            Fx.rune(sw, VIOLET, WHITE, target.x, target.y + 0.2, target.z, 8.0, t * 0.1);
         });
         case 7 -> {
            Fx.rune(sw, CYAN, GOLD, feet.x, feet.y + 0.2, feet.z, 6.0, 0.0);
            for (int i = 0; i < 5; i++) {
               double a = i * Math.PI * 2.0 / 5.0;
               Fx.pillar(sw, ParticleTypes.END_ROD, feet.x + Math.cos(a) * 3.5, feet.y, feet.y + 12.0, feet.z + Math.sin(a) * 3.5);
            }
            snd(sw, feet, SoundEvents.BEACON_ACTIVATE, 2.0F, 1.5F);
         }
         case 8 -> {
            Fx.laserMark(sw, RED, ORANGE, target.x, target.y, target.z, 50.0, 0.0);
            loop(40, 4, t -> Shapes.armillary(sw, WHITE, GOLD, target.x, target.y + 20.0, target.z, 4.0 + t * 0.1, t * 0.2));
            Scheduler.later(45, () -> loop(16, 2, t -> Fx.beamColumn(sw, ParticleTypes.END_ROD, GOLD, target.x, target.y, target.z, 50.0, 1.5 + t * 0.3, t * 0.5)));
         }
         case 9 -> loop(60, 4, t -> {
            Shapes.armillary(sw, BLUE, CYAN, u.getX(), u.getY() + 1.0, u.getZ(), 6.0, t * 0.1);
            Fx.ring(sw, CYAN, u.getX(), u.getY() + 0.2, u.getZ(), 6.0 + Math.sin(t * 0.3), 24);
         });
         case 10 -> {
            Fx.rune(sw, CYAN, WHITE, feet.x, feet.y + 0.2, feet.z, 8.0, 0.0);
            for (int i = 0; i < 6; i++) {
               double a = i * Math.PI / 3.0;
               Fx.pillar(sw, ParticleTypes.SOUL_FIRE_FLAME, feet.x + Math.cos(a) * 4.5, feet.y, feet.y + 10.0, feet.z + Math.sin(a) * 4.5);
            }
            snd(sw, feet, SoundEvents.WARDEN_EMERGE, 3.0F, 1.0F);
         }
         case 11 -> loop(100, 4, t -> {
            Shapes.armillary(sw, VIOLET, WHITE, target.x, target.y + 1.5, target.z, 2.5 + Math.sin(t * 0.2), t * 0.3);
            Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, target.x, target.y + 1.5, target.z, 24, 5.0, 4.0, 5.0, 0.2);
         });
         case 12 -> {
            Shapes.poly4(sw, Shapes.Poly.CELL16, CYAN, ParticleTypes.SNOWFLAKE, feet.x, feet.y + 1.5, feet.z, 3.0, 0.5, 3);
            Fx.ring(sw, CYAN, feet.x, feet.y + 0.2, feet.z, 8.0, 32);
            Fx.burst(sw, ParticleTypes.SNOWFLAKE, feet.x, feet.y + 1.5, feet.z, 120, 8.0, 3.0, 8.0, 0.1);
            Fx.shatter(sw, CYAN, WHITE, ParticleTypes.SNOWFLAKE, ParticleTypes.END_ROD, feet.x, feet.y + 1.5, feet.z, 2.0, 0.3);
            snd(sw, feet, SoundEvents.GLASS_BREAK, 3.0F, 0.6F);
         }
         case 13 -> loop(80, 2, t -> {
            for (int h = 0; h < 3; h++) {
               Fx.helix(sw, ParticleTypes.CLOUD, WHITE, u.getX(), u.getY(), u.getZ(), 3.0 + h * 2.0, 10.0, t * 0.3 + h * 2.0);
            }
         });
         case 14 -> loop(30, 2, t -> {
            Vec3 e = u.getEyePosition();
            Vec3 l = u.getLookAngle();
            spiral(sw, VIOLET, e, l, 14.0, 1.8, t * 0.6);
            spiral(sw, ParticleTypes.DRAGON_BREATH, e, l, 14.0, 2.6, -t * 0.4);
         });
         case 15 -> {
            Fx.laserMark(sw, CYAN, GOLD, target.x, target.y, target.z, 40.0, 0.0);
            loop(40, 4, t -> Fx.rune(sw, CYAN, GOLD, target.x, target.y + 0.2, target.z, 12.0, t * 0.12));
         }
         case 16 -> {
            Fx.line(sw, VIOLET, eye.x, eye.y - 0.5, eye.z, target.x, target.y + 1.0, target.z, 24);
            Fx.rune(sw, VIOLET, WHITE, target.x, target.y + 0.2, target.z, 5.0, 0.0);
            Fx.burst(sw, ParticleTypes.PORTAL, target.x, target.y + 1.0, target.z, 60, 0.6, 1.0, 0.6, 0.6);
            snd(sw, target, SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.8F);
         }
         case 17 -> {
            aura(sw, u, RED, ORANGE, 40);
            Shapes.armillary(sw, RED, ORANGE, feet.x, feet.y + 1.0, feet.z, 2.0, 0.0);
            snd(sw, feet, SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
         }
         case 19 -> loop(60, 5, t -> {
            Fx.ring(sw, WHITE, u.getX(), u.getY() + 0.3, u.getZ(), 10.0, 36);
            for (int k = 0; k < 12; k++) {
               double a = k * Math.PI / 6.0;
               Fx.line(sw, k % 3 == 0 ? GOLD : WHITE, u.getX() + Math.cos(a) * 9.0, u.getY() + 0.3, u.getZ() + Math.sin(a) * 9.0, u.getX() + Math.cos(a) * 10.0, u.getY() + 0.3, u.getZ() + Math.sin(a) * 10.0, 2);
            }
            double h = t * 0.1;
            Fx.line(sw, GOLD, u.getX(), u.getY() + 0.3, u.getZ(), u.getX() + Math.cos(h) * 8.0, u.getY() + 0.3, u.getZ() + Math.sin(h) * 8.0, 8);
            Shapes.poly4(sw, Shapes.Poly.CELL24, WHITE, ParticleTypes.END_ROD, u.getX(), u.getY() + 3.0, u.getZ(), 1.8, t * 0.05, 1);
         });
         case 20 -> loop(16, 2, t -> {
            Fx.burst(sw, WHITE, u.getX(), u.getY() + 0.5, u.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            Fx.burst(sw, ParticleTypes.CLOUD, u.getX(), u.getY() + 0.2, u.getZ(), 3, 0.2, 0.0, 0.2, 0.05);
         });
         case 21 -> {
            Shapes.armillary(sw, VIOLET, WHITE, target.x, target.y + 1.5, target.z, 3.5, 0.0);
            loop(30, 5, t -> Fx.ring(sw, VIOLET, target.x, target.y + 0.2, target.z, 8.0 - t * 0.2, 24));
         }
         case 23 -> {
            for (Mob m : sw.getEntitiesOfClass(Mob.class, new AABB(feet, feet).inflate(60.0), x -> x.getTags().contains("lbm_owner_" + u.getUUID()))) {
               Fx.line(sw, GOLD, eye.x, eye.y - 0.4, eye.z, m.getX(), m.getY() + m.getBbHeight() / 2.0, m.getZ(), 14);
               Fx.ring(sw, GOLD, m.getX(), m.getY() + 0.1, m.getZ(), 1.4, 14);
            }
         }
         case 24 -> {
            Adapt.chime(sw, u);
            Fx.rune(sw, GOLD, WHITE, feet.x, feet.y + 0.2, feet.z, 6.0, 0.0);
            loop(30, 3, t -> Fx.beamColumn(sw, ParticleTypes.END_ROD, GOLD, u.getX(), u.getY(), u.getZ(), 30.0, 1.2 + t * 0.05, t * 0.4));
            Fx.shatter(sw, GOLD, WHITE, ParticleTypes.END_ROD, ParticleTypes.FIREWORK, feet.x, feet.y + 1.2, feet.z, 1.6, 0.4);
         }
         case 25 -> loop(30, 3, t -> {
            Shapes.poly4(sw, Shapes.Poly.CELL16, VIOLET, ParticleTypes.REVERSE_PORTAL, target.x, target.y + 2.0, target.z, 3.0, t * 0.3, 2);
            Fx.burst(sw, ParticleTypes.GLOW, target.x, target.y + 1.5, target.z, 20, 5.0, 2.0, 5.0, 0.3);
         });
         case 26 -> {
            loop(40, 4, t -> {
               Fx.ring(sw, GOLD, target.x, target.y + 0.2, target.z, 8.0, 36);
               double h = t * 0.4;
               Fx.line(sw, WHITE, target.x, target.y + 0.2, target.z, target.x + Math.cos(h) * 7.0, target.y + 0.2, target.z + Math.sin(h) * 7.0, 8);
               Fx.line(sw, GOLD, target.x, target.y + 0.2, target.z, target.x + Math.cos(h / 12.0) * 5.0, target.y + 0.2, target.z + Math.sin(h / 12.0) * 5.0, 6);
            });
            Scheduler.later(44, () -> Fx.shatter(sw, GOLD, WHITE, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, target.x, target.y + 1.5, target.z, 2.5, 0.2));
         }
         case 28 -> loop(20, 4, t -> {
            double rad = 3.0 + t * 1.2;
            for (int tilt = 0; tilt < 3; tilt++) {
               double tr = tilt * Math.PI / 3.0;
               for (int i = 0; i < 18; i++) {
                  double a = i * Math.PI * 2.0 / 18.0;
                  Fx.burst(sw, tilt == 1 ? GOLD : CYAN, feet.x + Math.cos(a) * rad, feet.y + 1.0 + Math.sin(a) * rad * Math.sin(tr), feet.z + Math.sin(a) * rad * Math.cos(tr), 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         });
         case 30 -> {
            loop(20, 2, t -> Fx.helix(sw, CYAN, WHITE, u.getX(), u.getY(), u.getZ(), 1.2, 3.0 - t * 0.15, -t * 0.5));
            Shapes.armillary(sw, CYAN, WHITE, feet.x, feet.y + 1.0, feet.z, 2.0, 0.0);
            snd(sw, feet, SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 1.5F);
         }
         case 32 -> loop(60, 4, t -> {
            Shapes.armillary(sw, GOLD, WHITE, u.getX(), u.getY() + 1.0, u.getZ(), 2.4, t * 0.15);
            Fx.ring(sw, GOLD, u.getX(), u.getY() + 0.2, u.getZ(), 2.4, 18);
         });
         case 33 -> {
            Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1.0, target.z, 60, 1.0, 1.0, 1.0, 0.5);
            Fx.ring(sw, CYAN, target.x, target.y + 0.2, target.z, 5.0, 24);
            Fx.line(sw, CYAN, eye.x, eye.y - 0.4, eye.z, target.x, target.y + 1.0, target.z, 20);
         }
         case 35 -> {
            Fx.rune(sw, VIOLET, WHITE, target.x, target.y + 0.2, target.z, 10.0, 0.0);
            loop(30, 3, t -> Shapes.armillary(sw, VIOLET, WHITE, target.x, target.y + 3.0, target.z, 4.0 + t * 0.1, t * 0.3));
            Scheduler.later(32, () -> Fx.shatter(sw, VIOLET, WHITE, ParticleTypes.REVERSE_PORTAL, ParticleTypes.END_ROD, target.x, target.y + 3.0, target.z, 3.0, 0.4));
         }
         default -> {
         }
      }
   }
}
