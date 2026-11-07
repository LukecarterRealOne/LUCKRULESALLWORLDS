package com.lucky.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GoldenMight {
   static final boolean HIT_OTHER_PLAYERS = true;
   static final double RANGE_H = 72.0;
   static final double RANGE_DOWN = 56.0;
   static final double RANGE_UP = 110.0;
   static final float ORB_BOLT_DAMAGE = 60.0F;
   static final float QUASAR_BEAM_DAMAGE = 200.0F;
   static final float GRB_DAMAGE = 1000.0F;
   static final float GRB_AFTERSHOCK_DAMAGE = 400.0F;
   static final float TESSERACT_DAMAGE = 25000.0F;
   static final int MAX_BEAM_TARGETS = 48;
   static final int MAX_BOXED = 40;
   static final int T_FORM = 50;
   static final int T_COLLAPSE = 200;
   static final int T_QUASAR = 245;
   static final int T_CHARGE = 395;
   static final int T_GRB = 445;
   static final int T_TESS = 520;
   static final int T_LIFT_END = 590;
   static final int T_SQUEEZE_END = 640;
   static final int T_END = 680;
   private static final Set<UUID> ACTIVE = new HashSet<>();
   private static int sessionCounter = 0;
   private static final double[] Q_ID = new double[]{0.0, 0.0, 0.0, 1.0};

   private GoldenMight() {
   }

   public static void cast(ServerLevel var0, Player var1) {
      UUID var2 = var1.getUUID();
      if (ACTIVE.contains(var2)) {
         var1.displayClientMessage(Component.literal("\u00a7e\u00a7lGolden Might is already unleashed!"), true);
      } else {
         ACTIVE.add(var2);
         GoldenMight.Run var3 = new GoldenMight.Run(var0, var1, ++sessionCounter);
         Scheduler.later(1, var3::step);
      }
   }

   private static double[] qSpin(double var0, double var2, double var4, double var6) {
      double var8 = Math.sqrt(var0 * var0 + var2 * var2 + var4 * var4);
      if (var8 < 1.0E-9) {
         return new double[]{0.0, 0.0, 0.0, 1.0};
      } else {
         double var10 = Math.sin(var6 / 2.0) / var8;
         return new double[]{var0 * var10, var2 * var10, var4 * var10, Math.cos(var6 / 2.0)};
      }
   }

   private static double[] qMul(double[] var0, double[] var1) {
      return new double[]{
         var0[3] * var1[0] + var0[0] * var1[3] + var0[1] * var1[2] - var0[2] * var1[1],
         var0[3] * var1[1] - var0[0] * var1[2] + var0[1] * var1[3] + var0[2] * var1[0],
         var0[3] * var1[2] + var0[0] * var1[1] - var0[1] * var1[0] + var0[2] * var1[3],
         var0[3] * var1[3] - var0[0] * var1[0] - var0[1] * var1[1] - var0[2] * var1[2]
      };
   }

   private static double[] alignY(double var0, double var2, double var4) {
      double var6 = 1.0 + var2;
      if (var6 < 1.0E-6) {
         return new double[]{1.0, 0.0, 0.0, 0.0};
      } else {
         double var10 = 0.0;
         double var12 = -var0;
         double var14 = Math.sqrt(var4 * var4 + var10 * var10 + var12 * var12 + var6 * var6);
         return new double[]{var4 / var14, var10 / var14, var12 / var14, var6 / var14};
      }
   }

   private static double[] rot(double[] var0, double var1, double var3, double var5) {
      double var7 = 2.0 * (var0[1] * var5 - var0[2] * var3);
      double var9 = 2.0 * (var0[2] * var1 - var0[0] * var5);
      double var11 = 2.0 * (var0[0] * var3 - var0[1] * var1);
      return new double[]{
         var1 + var0[3] * var7 + (var0[1] * var11 - var0[2] * var9),
         var3 + var0[3] * var9 + (var0[2] * var7 - var0[0] * var11),
         var5 + var0[3] * var11 + (var0[0] * var9 - var0[1] * var7)
      };
   }

   private static String f4(double var0) {
      return String.format(Locale.ROOT, "%.4f", var0) + "f";
   }

   private static String xform(double[] var0, double var1, double var3, double var5, double var7, double var9, double var11) {
      double[] var13 = rot(var0, var7 * var1, var9 * var3, var11 * var5);
      return "transformation:{left_rotation:["
         + f4(var0[0])
         + ","
         + f4(var0[1])
         + ","
         + f4(var0[2])
         + ","
         + f4(var0[3])
         + "],right_rotation:[0f,0f,0f,1f],scale:["
         + f4(var1)
         + ","
         + f4(var3)
         + ","
         + f4(var5)
         + "],translation:["
         + f4(-var13[0])
         + ","
         + f4(-var13[1])
         + ","
         + f4(-var13[2])
         + "]}";
   }

   private static final class Run {
      final ServerLevel lv;
      final Player p;
      final String tag;
      final double cx;
      final double cy;
      final double cz;
      final double ox;
      final double oy;
      final double oz;
      final Random rnd = new Random();
      final DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.12F), 1.7F);
      final DustParticleOptions white = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.92F), 1.9F);
      final DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.72F, 0.45F, 1.0F), 1.6F);
      int t = 0;
      int beamSeq = 0;
      boolean finished = false;
      List<LivingEntity> grbTargets = new ArrayList<>();
      final List<LivingEntity> boxed = new ArrayList<>();
      final List<double[]> boxOffset = new ArrayList<>();
      final List<String> cageTags = new ArrayList<>();
      final Set<UUID> aiFrozen = new HashSet<>();

      Run(ServerLevel var1, Player var2, int var3) {
         this.lv = var1;
         this.p = var2;
         this.tag = "gm" + var3;
         this.cx = var2.getX();
         this.cy = var2.getY();
         this.cz = var2.getZ();
         double var4 = 36.0;

         try {
            String var6 = var1.dimension().location().toString();
            if (var6.contains("nether")) {
               var4 = 20.0;
            }
         } catch (Throwable var7) {
         }

         this.ox = this.cx;
         this.oy = Math.min(this.cy + var4, (double)(var1.getMaxBuildHeight() - 16));
         this.oz = this.cz;
      }

      void step() {
         if (!this.finished) {
            try {
               if (this.callerGone()) {
                  this.finish(false);
                  return;
               }

               this.tick(this.t);
            } catch (Throwable var2) {
               var2.printStackTrace();
            }

            this.t++;
            if (this.t > 680) {
               this.finish(true);
            } else {
               Scheduler.later(1, this::step);
            }
         }
      }

      boolean callerGone() {
         return !this.p.isAlive() || this.p.isRemoved() || this.p.level() != this.lv;
      }

      void finish(boolean var1) {
         this.finished = true;
         if (this.hyper != null) {
            this.hyper.remove();
            this.hyper = null;
         }
         this.cmd("kill @e[tag=" + this.tag + "]");

         for (UUID var3 : this.aiFrozen) {
            for (LivingEntity var5 : this.boxed) {
               if (var5.getUUID().equals(var3) && var5 instanceof Mob) {
                  Mob var6 = (Mob)var5;
                  if (var6.isAlive()) {
                     var6.setNoAi(false);
                  }
               }
            }
         }

         GoldenMight.ACTIVE.remove(this.p.getUUID());
         if (var1) {
            this.msg("\u00a76\u00a7lGolden Might has run its course.");
         }
      }

      void cmd(String var1) {
         Fx.cmd(this.lv, var1);
      }

      void msg(String var1) {
         this.p.displayClientMessage(Component.literal(var1), true);
      }

      void snd(String var1, double var2, double var4, double var6, float var8, float var9) {
         this.cmd(String.format(Locale.ROOT, "playsound %s master @a %.2f %.2f %.2f %.2f %.2f", var1, var2, var4, var6, var8, var9));
      }

      void part(String var1, double var2, double var4, double var6, double var8, double var10, double var12, double var14, int var16) {
         this.cmd(
            String.format(Locale.ROOT, "particle %s %.2f %.2f %.2f %.2f %.2f %.2f %.3f %d force", var1, var2, var4, var6, var8, var10, var12, var14, var16)
         );
      }

      void spawn(String var1, double var2, double var4, double var6, String var8, String var9, String var10, int var11) {
         String var12 = "\"lbm_beam\",\"" + this.tag + "\",\"" + var8 + "\"" + (var9 == null ? "" : ",\"" + var9 + "\"");
         this.cmd(
            String.format(
               Locale.ROOT,
               "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},Tags:[%s],view_range:%df,brightness:{sky:15,block:15},%s}",
               var2,
               var4,
               var6,
               var1,
               var12,
               var11,
               var10
            )
         );
      }

      void merge(String var1, String var2, int var3) {
         this.cmd("data merge entity @e[tag=" + var1 + ",limit=1] {" + var2 + ",start_interpolation:0,interpolation_duration:" + var3 + "}");
      }

      void beam(
         double var1, double var3, double var5, double var7, double var9, double var11, double var13, String var15, String var16, int var17, boolean var18
      ) {
         double var19 = var7 - var1;
         double var21 = var9 - var3;
         double var23 = var11 - var5;
         double var25 = Math.sqrt(var19 * var19 + var21 * var21 + var23 * var23);
         if (!(var25 < 0.5)) {
            double[] var27 = GoldenMight.alignY(var19 / var25, var21 / var25, var23 / var25);
            String var28 = this.tag + "b" + this.beamSeq++;
            this.spawn(var15, var1, var3, var5, var28 + "o", var28, GoldenMight.xform(var27, var13, var25, var13, 0.5, 0.0, 0.5), 8);
            this.spawn(var16, var1, var3, var5, var28 + "i", var28, GoldenMight.xform(var27, var13 * 0.42, var25, var13 * 0.42, 0.5, 0.0, 0.5), 8);
            if (var18 && var17 > 3) {
               String var29 = GoldenMight.xform(var27, var13 * 0.12, var25, var13 * 0.12, 0.5, 0.0, 0.5);
               String var30 = GoldenMight.xform(var27, var13 * 0.05, var25, var13 * 0.05, 0.5, 0.0, 0.5);
               int var31 = var17 - 2;
               Scheduler.later(2, () -> {
                  if (!this.finished) {
                     this.merge(var28 + "o", var29, var31);
                     this.merge(var28 + "i", var30, var31);
                  }
               });
            }

            Scheduler.later(var17, () -> this.cmd("kill @e[tag=" + var28 + "]"));
         }
      }

      boolean friend(Entity var1) {
         return var1 == this.p || var1.getTags().contains("lbm_ally");
      }

      List<LivingEntity> targets() {
         AABB var1 = new AABB(this.cx - 72.0, this.cy - 56.0, this.cz - 72.0, this.cx + 72.0, this.cy + 110.0, this.cz + 72.0);
         return this.lv.getEntitiesOfClass(LivingEntity.class, var1, var1x -> {
            if (!var1x.isAlive() || var1x.isRemoved() || this.friend(var1x)) {
               return false;
            } else {
               return var1x instanceof Player var2 ? !var2.isSpectator() : true;
            }
         });
      }

      List<LivingEntity> pick(List<LivingEntity> var1, int var2) {
         ArrayList var3 = new ArrayList(var1);
         Collections.shuffle(var3, this.rnd);
         return var3.size() > var2 ? new ArrayList<>(var3.subList(0, var2)) : var3;
      }

      void hurt(LivingEntity var1, float var2) {
         if (var1 instanceof Player var9 && var9.isSpectator()) {
            return;
         }
         DamageSource var3 = this.lv.damageSources().indirectMagic(this.p, this.p);
         var1.hurt(var3, Float.MAX_VALUE);
         if (var1.isAlive()) {
            var1.hurt(this.lv.damageSources().genericKill(), Float.MAX_VALUE);
         }
         if (var1.isAlive() && !(var1 instanceof Player)) {
            var1.setHealth(0.0F);
         }
      }

      double midY(LivingEntity var1) {
         return var1.getY() + (double)var1.getBbHeight() * 0.5;
      }

      void tick(int var1) {
         if (var1 == 0) {
            this.begin();
         }

         if (var1 < 200) {
            this.orbPhase(var1);
         } else if (var1 < 395) {
            this.quasarPhase(var1);
         } else if (var1 < 520) {
            this.grbPhase(var1);
         } else {
            this.tesseractPhase(var1);
         }
      }

      void begin() {
         String var1 = this.p.getUUID().toString();
         this.msg("\u00a76\u00a7l\u2600 GOLDEN MIGHT \u2600");
         this.cmd("effect give " + var1 + " minecraft:resistance 36 3 true");
         this.cmd("effect give " + var1 + " minecraft:regeneration 36 2 true");
         this.cmd("effect give " + var1 + " minecraft:strength 36 3 true");
         this.cmd("effect give " + var1 + " minecraft:absorption 36 4 true");
         this.cmd("effect give " + var1 + " minecraft:fire_resistance 40 0 true");
         this.snd("minecraft:block.beacon.activate", this.cx, this.cy, this.cz, 5.0F, 0.6F);
         this.snd("minecraft:entity.lightning_bolt.thunder", this.cx, this.cy, this.cz, 6.0F, 0.7F);
         Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 90.0, 0.5F, 0.7F, 14, 16765514);
         Fx.ring(this.lv, this.gold, this.cx, this.cy + 0.2, this.cz, 3.0, 18);
         Fx.ring(this.lv, this.gold, this.cx, this.cy + 0.2, this.cz, 6.0, 18);
         Fx.ring(this.lv, this.white, this.cx, this.cy + 0.2, this.cz, 9.0, 18);
         double[] var2 = GoldenMight.Q_ID;
         String var3 = GoldenMight.xform(var2, 0.3, 0.3, 0.3, 0.5, 0.5, 0.5);
         this.spawn("minecraft:yellow_stained_glass", this.ox, this.oy, this.oz, this.tag + "_oA", null, var3, 16);
         this.spawn("minecraft:orange_stained_glass", this.ox, this.oy, this.oz, this.tag + "_oB", null, var3, 16);
         this.spawn("minecraft:yellow_stained_glass", this.ox, this.oy, this.oz, this.tag + "_oC", null, var3, 16);
         this.spawn("minecraft:white_concrete", this.ox, this.oy, this.oz, this.tag + "_oK", null, var3, 16);
         this.beam(this.cx, this.cy, this.cz, this.ox, this.oy, this.oz, 1.6, "minecraft:yellow_stained_glass", "minecraft:white_concrete", 46, true);
      }

      double orbRadius(int var1) {
         if (var1 < 50) {
            double var2 = (double)var1 / 50.0;
            return 0.6 + 8.4 * (1.0 - Math.pow(1.0 - var2, 3.0));
         } else {
            return 9.0 + 0.45 * Math.sin((double)var1 * 0.28);
         }
      }

      void updateOrb(int var1, double var2, double var4) {
         double var6 = (double)var1 * var4;
         double var8 = var2 * 1.6;
         double[] var10 = GoldenMight.qSpin(0.0, 1.0, 0.0, var6);
         double[] var11 = GoldenMight.qMul(GoldenMight.qSpin(0.0, 1.0, 0.0, -var6 * 0.8), GoldenMight.qSpin(1.0, 0.0, 1.0, 0.9553));
         double[] var12 = GoldenMight.qMul(GoldenMight.qSpin(1.0, 0.0, 0.0, var6 * 0.6), GoldenMight.qSpin(0.0, 0.0, 1.0, 0.7854));
         double[] var13 = GoldenMight.qMul(GoldenMight.qSpin(1.0, 1.0, 0.0, var6 * 1.7), GoldenMight.qSpin(0.0, 1.0, 1.0, 0.6));
         this.merge(this.tag + "_oA", GoldenMight.xform(var10, var8, var8, var8, 0.5, 0.5, 0.5), 4);
         this.merge(this.tag + "_oB", GoldenMight.xform(var11, var8 * 0.92, var8 * 0.92, var8 * 0.92, 0.5, 0.5, 0.5), 4);
         this.merge(this.tag + "_oC", GoldenMight.xform(var12, var8 * 0.84, var8 * 0.84, var8 * 0.84, 0.5, 0.5, 0.5), 4);
         this.merge(this.tag + "_oK", GoldenMight.xform(var13, var2 * 0.62, var2 * 0.62, var2 * 0.62, 0.5, 0.5, 0.5), 4);
      }

      void orbPhase(int var1) {
         double var2 = this.orbRadius(var1);
         this.fxOrb(var1, var2);
         if (var1 % 4 == 0) {
            this.updateOrb(var1, var2, 0.05);
         }

         if (var1 % 3 == 0) {
            this.part("minecraft:end_rod", this.ox, this.oy, this.oz, var2 * 0.7, var2 * 0.7, var2 * 0.7, 0.02, 14);
            Fx.ring(this.lv, this.gold, this.ox, this.oy, this.oz, var2 * 1.05, 18);
         }

         if (var1 == 50) {
            this.msg("\u00a7e\u00a7lSOLAR ORB \u2014 nothing escapes it");
            this.snd("minecraft:entity.generic.explode", this.ox, this.oy, this.oz, 8.0F, 0.5F);
            this.snd("minecraft:block.beacon.power_select", this.ox, this.oy, this.oz, 8.0F, 0.5F);
            this.part("minecraft:flash", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
            Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 120.0, 0.6F, 0.9F, 16, 16769126);
         }

         if (var1 >= 50 && var1 % 4 == 0) {
            this.orbBolts();
         }

         if (var1 > 0 && var1 % 40 == 0 && var1 < 50) {
            this.snd("minecraft:block.beacon.ambient", this.ox, this.oy, this.oz, 6.0F, 0.8F);
         }
      }

      void orbBolts() {
         double var1 = this.orbRadius(this.t);
         List<LivingEntity> var3 = this.targets();
         List<LivingEntity> var4 = this.pick(var3, 2);
         if (var4.isEmpty()) {
            for (int var13 = 0; var13 < 2; var13++) {
               double var14 = this.rnd.nextDouble() * Math.PI * 2.0;
               double var8 = 6.0 + this.rnd.nextDouble() * 26.0;
               this.beam(
                  this.ox,
                  this.oy - var1 * 0.5,
                  this.oz,
                  this.cx + Math.cos(var14) * var8,
                  this.cy,
                  this.cz + Math.sin(var14) * var8,
                  0.9,
                  "minecraft:yellow_stained_glass",
                  "minecraft:white_concrete",
                  5,
                  false
               );
            }

            this.snd("minecraft:entity.lightning_bolt.impact", this.ox, this.oy - 10.0, this.oz, 3.0F, 1.6F);
         } else {
            for (LivingEntity var6 : var4) {
               double var7 = var6.getX();
               double var9 = this.midY(var6);
               double var11 = var6.getZ();
               this.beam(this.ox, this.oy - var1 * 0.5, this.oz, var7, var9, var11, 1.1, "minecraft:yellow_stained_glass", "minecraft:white_concrete", 6, false);
               this.hurt(var6, 60.0F);
               var6.igniteForSeconds(4);
               Fx.burst(this.lv, ParticleTypes.EXPLOSION, var7, var9, var11, 14, 0.4, 0.6, 0.4, 0.12);
               Fx.ring(this.lv, this.gold, var7, var6.getY() + 0.1, var11, 1.6, 12);
               this.snd("minecraft:entity.lightning_bolt.impact", var7, var9, var11, 2.5F, 1.8F);
            }
         }
      }

      double[] axisAt(int var1) {
         double var2 = Math.toRadians(32.0);
         double var4 = (double)var1 * 0.045;
         return new double[]{Math.sin(var2) * Math.cos(var4), Math.cos(var2), Math.sin(var2) * Math.sin(var4)};
      }

      void quasarPhase(int var1) {
         if (var1 < 245) {
            double var6 = (double)(var1 - 200) / 45.0;
            double var4 = 9.0 - 7.2 * var6 * var6;
            this.fxCollapse(var1, var6);
            if (var1 == 200) {
               this.msg("\u00a76\u00a7lCOLLAPSE");
               this.snd("minecraft:block.beacon.deactivate", this.ox, this.oy, this.oz, 8.0F, 0.5F);
               this.snd("minecraft:entity.warden.sonic_charge", this.ox, this.oy, this.oz, 8.0F, 0.6F);
            }

            if (var1 % 2 == 0) {
               this.updateOrb(var1, var4, 0.05 + 0.25 * var6);
            }

            if (var1 % 3 == 0) {
               Fx.ring(this.lv, this.white, this.ox, this.oy, this.oz, var4 * 1.2 + 4.0 * (1.0 - var6), 18);
               this.part("minecraft:end_rod", this.ox, this.oy, this.oz, 5.0 * (1.0 - var6) + 1.0, 5.0 * (1.0 - var6) + 1.0, 5.0 * (1.0 - var6) + 1.0, -0.0, 10);
            }

            Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 90.0, 0.0F, (float)(0.15 + 0.5 * var6), 3, 16777215);
            if (var1 == 244) {
               this.cmd("kill @e[tag=" + this.tag + "_oA]");
               this.cmd("kill @e[tag=" + this.tag + "_oB]");
               this.cmd("kill @e[tag=" + this.tag + "_oC]");
            }
         } else {
            if (var1 == 245) {
               this.msg("\u00a7b\u00a7lQUASAR \u2014 it sees everything");
               this.snd("minecraft:entity.generic.explode", this.ox, this.oy, this.oz, 8.0F, 0.4F);
               this.snd("minecraft:entity.ender_dragon.growl", this.ox, this.oy, this.oz, 6.0F, 0.5F);
               this.part("minecraft:flash", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
               this.part("minecraft:explosion_emitter", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
               Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 120.0, 0.7F, 1.0F, 18, 16777215);
               double[] var2 = this.axisAt(var1);
               double[] var3 = GoldenMight.alignY(var2[0], var2[1], var2[2]);
               this.spawn(
                  "minecraft:light_blue_stained_glass",
                  this.ox,
                  this.oy,
                  this.oz,
                  this.tag + "_jo",
                  null,
                  GoldenMight.xform(var3, 0.5, 4.0, 0.5, 0.5, 0.5, 0.5),
                  16
               );
               this.spawn(
                  "minecraft:white_concrete", this.ox, this.oy, this.oz, this.tag + "_ji", null, GoldenMight.xform(var3, 0.2, 4.0, 0.2, 0.5, 0.5, 0.5), 16
               );
               this.spawn(
                  "minecraft:orange_stained_glass",
                  this.ox,
                  this.oy,
                  this.oz,
                  this.tag + "_d0",
                  null,
                  GoldenMight.xform(var3, 2.0, 0.2, 2.0, 0.5, 0.5, 0.5),
                  16
               );
               this.spawn(
                  "minecraft:yellow_stained_glass",
                  this.ox,
                  this.oy + 0.04,
                  this.oz,
                  this.tag + "_d1",
                  null,
                  GoldenMight.xform(var3, 2.0, 0.2, 2.0, 0.5, 0.5, 0.5),
                  16
               );
               this.spawn(
                  "minecraft:red_stained_glass",
                  this.ox,
                  this.oy - 0.04,
                  this.oz,
                  this.tag + "_d2",
                  null,
                  GoldenMight.xform(var3, 2.0, 0.2, 2.0, 0.5, 0.5, 0.5),
                  16
               );
            }

            this.fxQuasar(var1);
            this.updateQuasar(var1, 3.0, 1.6, 55.0, 1.0);
            this.quasarBeams(var1);
         }
      }

      void updateQuasar(int var1, double var2, double var4, double var6, double var8) {
         if (var1 % 3 == 0) {
            int var10 = Math.max(0, var1 - 245);
            double var11 = Math.min(1.0, (double)var10 / 24.0) * var8;
            double[] var13 = this.axisAt(var1);
            double[] var14 = GoldenMight.alignY(var13[0], var13[1], var13[2]);
            this.merge(this.tag + "_jo", GoldenMight.xform(var14, var4, var6 * 2.0, var4, 0.5, 0.5, 0.5), 3);
            this.merge(this.tag + "_ji", GoldenMight.xform(var14, var4 * 0.4, var6 * 2.0, var4 * 0.4, 0.5, 0.5, 0.5), 3);
            double[] var15 = new double[]{26.0 * var11, 19.0 * var11, 12.0 * var11};
            double[] var16 = new double[]{(double)var1 * 0.21, (double)(-var1) * 0.29, (double)var1 * 0.37};

            for (int var17 = 0; var17 < 3; var17++) {
               double[] var18 = GoldenMight.qMul(GoldenMight.qSpin(var13[0], var13[1], var13[2], var16[var17]), var14);
               this.merge(this.tag + "_d" + var17, GoldenMight.xform(var18, Math.max(0.2, var15[var17]), 0.22, Math.max(0.2, var15[var17]), 0.5, 0.5, 0.5), 3);
            }

            double[] var19 = GoldenMight.qMul(GoldenMight.qSpin(1.0, 1.0, 0.0, (double)var1 * 0.12), GoldenMight.qSpin(0.0, 1.0, 1.0, 0.6));
            this.merge(this.tag + "_oK", GoldenMight.xform(var19, var2, var2, var2, 0.5, 0.5, 0.5), 3);
            if (var1 % 6 == 0) {
               Fx.ring(this.lv, this.gold, this.ox, this.oy, this.oz, 6.0 + 2.0 * Math.sin((double)var1 * 0.2), 18);
               this.part("minecraft:end_rod", this.ox, this.oy, this.oz, 4.0, 1.0, 4.0, 0.05, 8);
            }
         }
      }

      void quasarBeams(int var1) {
         int var2 = (var1 - 245) % 16;
         if (var1 >= 257 && (var2 == 0 || var2 == 5 || var2 == 10)) {
            List<LivingEntity> var3 = this.pick(this.targets(), 1);
            if (!var3.isEmpty()) {
               LivingEntity var4 = (LivingEntity)var3.get(0);
               double var5 = var4.getX();
               double var7 = this.midY(var4);
               double var9 = var4.getZ();
               this.beam(this.ox, this.oy, this.oz, var5, var7, var9, 2.6, "minecraft:orange_stained_glass", "minecraft:white_concrete", 10, true);
               this.hurt(var4, 200.0F);
               var4.igniteForSeconds(8);
               Fx.epicImpact(this.lv, new Vec3(var5, var7, var9), ParticleTypes.EXPLOSION, ParticleTypes.PORTAL, 4.0, 8);
               Fx.ring(this.lv, this.white, var5, var4.getY() + 0.1, var9, 2.4, 14);
               this.snd("minecraft:entity.generic.explode", var5, var7, var9, 3.0F, 1.3F);
               this.snd("minecraft:entity.guardian.attack", this.ox, this.oy, this.oz, 6.0F, 0.7F);
               var4.setDeltaMovement(0.0, 0.4, 0.0);
               var4.hurtMarked = true;
            }
         }
      }

      void grbPhase(int var1) {
         double[] var2 = this.axisAt(var1);
         double[] var3 = GoldenMight.alignY(var2[0], var2[1], var2[2]);
         if (var1 < 445) {
            double var17 = (double)(var1 - 395) / 50.0;
            this.fxGrbCharge(var1, var17);
            if (var1 == 395) {
               this.msg("\u00a7f\u00a7lGAMMA RAY BURST \u2014 charging");
               this.snd("minecraft:entity.warden.sonic_charge", this.ox, this.oy, this.oz, 10.0F, 0.5F);
               this.snd("minecraft:block.beacon.deactivate", this.ox, this.oy, this.oz, 10.0F, 0.4F);
            }

            if (var1 % 2 == 0) {
               double var23 = 3.0 + 7.0 * var17;
               double[] var8 = GoldenMight.qMul(GoldenMight.qSpin(1.0, 1.0, 0.0, (double)var1 * 0.3), GoldenMight.qSpin(0.0, 1.0, 1.0, 0.6));
               this.merge(this.tag + "_oK", GoldenMight.xform(var8, var23, var23, var23, 0.5, 0.5, 0.5), 2);
               double var24 = 1.6 + 5.0 * var17;
               this.merge(this.tag + "_jo", GoldenMight.xform(var3, var24, 110.0, var24, 0.5, 0.5, 0.5), 2);
               this.merge(this.tag + "_ji", GoldenMight.xform(var3, var24 * 0.4, 110.0, var24 * 0.4, 0.5, 0.5, 0.5), 2);
               double var25 = 1.0 - 0.8 * var17;

               for (int var13 = 0; var13 < 3; var13++) {
                  double var14 = Math.max(0.3, new double[]{26.0, 19.0, 12.0}[var13] * var25);
                  double[] var16 = GoldenMight.qMul(
                     GoldenMight.qSpin(var2[0], var2[1], var2[2], (double)var1 * (0.4 + 0.1 * (double)var13) * (double)(var13 == 1 ? -1 : 1)), var3
                  );
                  this.merge(this.tag + "_d" + var13, GoldenMight.xform(var16, var14, 0.22, var14, 0.5, 0.5, 0.5), 2);
               }

               Fx.ring(this.lv, this.white, this.ox, this.oy, this.oz, 14.0 * (1.0 - var17) + 2.0, 18);
            }

            if (var1 % 5 == 0) {
               this.snd("minecraft:block.beacon.ambient", this.ox, this.oy, this.oz, 8.0F, (float)(0.5 + 1.2 * var17));
            }

            Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 110.0, (float)(0.1 * var17), (float)(0.5 + 1.0 * var17), 3, 16777215);
         } else {
            if (var1 == 445) {
               this.fireBurst(var3);
            }

            int var4 = var1 - 445;
            this.fxGrbFire(var4, var2);
            if (var4 >= 0 && var4 < 6) {
               for (int var5 = var4; var5 < this.grbTargets.size(); var5 += 6) {
                  LivingEntity var6 = this.grbTargets.get(var5);
                  if (var6.isAlive()) {
                     double var7 = var6.getX();
                     double var9 = this.midY(var6);
                     double var11 = var6.getZ();
                     this.beam(this.ox, this.oy, this.oz, var7, var9, var11, 5.0, "minecraft:yellow_stained_glass", "minecraft:white_concrete", 30, true);
                     this.hurt(var6, 1000.0F);
                     var6.igniteForSeconds(15);
                     Fx.epicImpact(this.lv, new Vec3(var7, var9, var11), ParticleTypes.EXPLOSION, ParticleTypes.PORTAL, 6.0, 10);
                     var6.setDeltaMovement(0.0, 0.8, 0.0);
                     var6.hurtMarked = true;
                  }
               }
            }

            if (var4 == 14) {
               for (LivingEntity var22 : this.grbTargets) {
                  if (var22.isAlive()) {
                     this.hurt(var22, 400.0F);
                     Fx.burst(this.lv, ParticleTypes.EXPLOSION, var22.getX(), this.midY(var22), var22.getZ(), 16, 0.5, 0.8, 0.5, 0.2);
                  }
               }

               this.snd("minecraft:entity.generic.explode", this.cx, this.cy, this.cz, 10.0F, 0.6F);
            }

            if (var4 >= 0 && var4 < 30 && var4 % 2 == 0) {
               double var19 = 4.0 + (double)var4 * 2.2;
               Fx.ring(this.lv, this.white, this.cx, this.cy + 0.3, this.cz, var19, 18);
               Fx.ring(this.lv, this.gold, this.cx, this.cy + 0.5, this.cz, var19 * 0.92, 18);
            }

            if (var4 > 0 && var4 % 4 == 0 && var4 < 60) {
               double var20 = Math.max(0.5, 16.0 * (1.0 - (double)var4 / 60.0));
               this.merge(this.tag + "_jo", GoldenMight.xform(var3, var20, 240.0, var20, 0.5, 0.5, 0.5), 4);
               this.merge(this.tag + "_ji", GoldenMight.xform(var3, var20 * 0.45, 240.0, var20 * 0.45, 0.5, 0.5, 0.5), 4);
               if (var4 % 8 == 0) {
                  Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 120.0, 0.25F, 1.2F, 6, 16777215);
               }
            }

            if (var1 == 519) {
               for (int var21 = 0; var21 < 3; var21++) {
                  this.cmd("kill @e[tag=" + this.tag + "_d" + var21 + "]");
               }

               this.cmd("kill @e[tag=" + this.tag + "_jo]");
               this.cmd("kill @e[tag=" + this.tag + "_ji]");
            }
         }
      }

      void fireBurst(double[] var1) {
         this.msg("\u00a7f\u00a7l\u00a7nGAMMA RAY BURST!");
         this.snd("minecraft:entity.lightning_bolt.thunder", this.ox, this.oy, this.oz, 12.0F, 0.5F);
         this.snd("minecraft:entity.ender_dragon.death", this.ox, this.oy, this.oz, 12.0F, 0.6F);
         this.snd("minecraft:entity.generic.explode", this.ox, this.oy, this.oz, 12.0F, 0.3F);
         this.part("minecraft:flash", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
         this.part("minecraft:explosion_emitter", this.ox, this.oy, this.oz, 4.0, 4.0, 4.0, 0.0, 6);
         Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 160.0, 1.0F, 2.0F, 40, 16777215);
         this.merge(this.tag + "_jo", GoldenMight.xform(var1, 16.0, 240.0, 16.0, 0.5, 0.5, 0.5), 3);
         this.merge(this.tag + "_ji", GoldenMight.xform(var1, 7.0, 240.0, 7.0, 0.5, 0.5, 0.5), 3);
         this.grbTargets = this.pick(this.targets(), 48);
      }

      // ================= visual rework layers (particles only; the damage/logic above is untouched) =================
      private double[][] basis(double[] a) {
         double[] tmp = Math.abs(a[0]) < 0.9 ? new double[]{1.0, 0.0, 0.0} : new double[]{0.0, 1.0, 0.0};
         double[] u = new double[]{a[1] * tmp[2] - a[2] * tmp[1], a[2] * tmp[0] - a[0] * tmp[2], a[0] * tmp[1] - a[1] * tmp[0]};
         double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
         u[0] /= ul;
         u[1] /= ul;
         u[2] /= ul;
         double[] w = new double[]{a[1] * u[2] - a[2] * u[1], a[2] * u[0] - a[0] * u[2], a[0] * u[1] - a[1] * u[0]};
         return new double[][]{u, w};
      }

      private double[] randDir() {
         double a = this.rnd.nextDouble() * Math.PI * 2.0;
         double z = this.rnd.nextDouble() * 2.0 - 1.0;
         double s = Math.sqrt(1.0 - z * z);
         return new double[]{Math.cos(a) * s, z, Math.sin(a) * s};
      }

      /** Solar orb: curling prominences, spinning corona rings, god rays and a ground sun sigil. */
      void fxOrb(int t, double r) {
         if (t % 2 == 0) {
            for (int i = 0; i < 3; i++) {
               double[] d = this.randDir();
               double[] tan = new double[]{-d[2], 0.0, d[0]};
               double h = r * (0.35 + this.rnd.nextDouble() * 0.45);
               for (int k = 0; k <= 6; k++) {
                  double u = k / 6.0;
                  double lift = Math.sin(u * Math.PI) * h;
                  double side = u * h * 0.8;
                  double rad = r * 0.9 + lift;
                  Fx.burst(this.lv, k % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                     this.ox + d[0] * rad + tan[0] * side, this.oy + d[1] * rad, this.oz + d[2] * rad + tan[2] * side, 1, 0.03, 0.03, 0.03, 0.0);
               }
            }
         }
         if (t % 4 == 0) {
            Shapes.armillary(this.lv, this.gold, this.white, this.ox, this.oy, this.oz, r * 1.25, (double)t * 0.05);
         }
         if (t % 8 == 0) {
            for (int i = 0; i < 8; i++) {
               double a = i * Math.PI / 4.0 + t * 0.02;
               Fx.line(this.lv, i % 2 == 0 ? this.white : this.gold, this.ox, this.oy - r * 0.6, this.oz,
                  this.cx + Math.cos(a) * r * 2.3, this.cy + 0.2, this.cz + Math.sin(a) * r * 2.3, 8);
            }
         }
         if (t % 6 == 0) {
            Fx.rune(this.lv, this.gold, this.white, this.cx, this.cy + 0.2, this.cz, r * 1.8, (double)t * 0.04);
         }
      }

      /** Collapse: light streams converge on the shrinking sun. */
      void fxCollapse(int t, double prog) {
         if (t % 3 == 0) {
            double start = 14.0 * (1.0 - prog) + 3.0;
            for (int i = 0; i < 9; i++) {
               double[] d = this.randDir();
               Fx.line(this.lv, i % 2 == 0 ? this.white : this.gold, this.ox + d[0] * start, this.oy + d[1] * start, this.oz + d[2] * start,
                  this.ox + d[0] * 1.5, this.oy + d[1] * 1.5, this.oz + d[2] * 1.5, 5);
            }
            Shapes.armillary(this.lv, this.white, this.violet, this.ox, this.oy, this.oz, 2.0 + 6.0 * (1.0 - prog), (double)t * 0.2);
         }
      }

      /** Quasar: spiral accretion arms, relativistic jets, an Einstein ring and infalling streams. */
      void fxQuasar(int t) {
         double[] a = this.axisAt(t);
         double[][] b = this.basis(a);
         double[] u = b[0];
         double[] w = b[1];
         if (t % 2 == 0) {
            for (int arm = 0; arm < 3; arm++) {
               for (int j = 0; j < 6; j++) {
                  double rr = 3.5 + j * 3.2;
                  double ang = t * 0.18 - j * 0.55 + arm * Math.PI * 2.0 / 3.0;
                  double c = Math.cos(ang) * rr;
                  double s = Math.sin(ang) * rr;
                  net.minecraft.core.particles.ParticleOptions p = arm == 0 ? this.gold : (arm == 1 ? (net.minecraft.core.particles.ParticleOptions)ParticleTypes.FLAME : this.white);
                  Fx.burst(this.lv, p, this.ox + u[0] * c + w[0] * s, this.oy + u[1] * c + w[1] * s, this.oz + u[2] * c + w[2] * s, 1, 0.05, 0.05, 0.05, 0.0);
               }
            }
            for (int sgn = -1; sgn <= 1; sgn += 2) {
               for (int k = 0; k < 8; k++) {
                  double dist = (4.0 + k * 5.0) * sgn;
                  Fx.burst(this.lv, ParticleTypes.END_ROD, this.ox + a[0] * dist, this.oy + a[1] * dist, this.oz + a[2] * dist, 1, 0.12, 0.12, 0.12, 0.01);
               }
            }
         }
         if (t % 4 == 0) {
            double tc = Math.cos(1.05);
            double ts = Math.sin(1.05);
            for (int i = 0; i < 24; i++) {
               double ang = i * Math.PI * 2.0 / 24.0 + t * 0.05;
               double c = Math.cos(ang) * 5.5;
               double s = Math.sin(ang) * 5.5;
               double ux = u[0] * tc + a[0] * ts;
               double uy = u[1] * tc + a[1] * ts;
               double uz = u[2] * tc + a[2] * ts;
               Fx.burst(this.lv, this.white, this.ox + ux * c + w[0] * s, this.oy + uy * c + w[1] * s, this.oz + uz * c + w[2] * s, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }
         if (t % 6 == 0) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
               Fx.line(this.lv, this.white, this.ox, this.oy, this.oz, this.ox + a[0] * 55.0 * sgn, this.oy + a[1] * 55.0 * sgn, this.oz + a[2] * 55.0 * sgn, 14);
            }
         }
         if (t % 3 == 0) {
            for (int s = 0; s < 5; s++) {
               double ang0 = this.rnd.nextDouble() * Math.PI * 2.0;
               for (int k = 0; k < 4; k++) {
                  double rr = 30.0 - k * 6.5;
                  double ang = ang0 + k * 0.7;
                  double c = Math.cos(ang) * rr;
                  double sn = Math.sin(ang) * rr;
                  Fx.burst(this.lv, ParticleTypes.REVERSE_PORTAL, this.ox + u[0] * c + w[0] * sn, this.oy + u[1] * c + w[1] * sn, this.oz + u[2] * c + w[2] * sn, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
      }

      /** Gamma ray charge: converging beams, tightening armillary, a rising helix. */
      void fxGrbCharge(int t, double prog) {
         if (t % 2 == 0) {
            double start = 18.0 * (1.0 - prog) + 4.0;
            for (int i = 0; i < 10; i++) {
               double[] d = this.randDir();
               Fx.line(this.lv, ParticleTypes.ELECTRIC_SPARK, this.ox + d[0] * start, this.oy + d[1] * start, this.oz + d[2] * start,
                  this.ox + d[0] * 2.5, this.oy + d[1] * 2.5, this.oz + d[2] * 2.5, 4);
            }
         }
         if (t % 6 == 0) {
            Shapes.armillary(this.lv, this.white, this.gold, this.ox, this.oy, this.oz, 3.0 + 7.0 * (1.0 - prog), (double)t * 0.3);
         }
         if (t % 4 == 0) {
            Fx.helix(this.lv, this.white, ParticleTypes.END_ROD, this.ox, this.cy, this.oz, 2.5, Math.max(6.0, this.oy - this.cy), (double)t * 0.6);
         }
      }

      /** Gamma ray burst: tilted expanding shock shells, ground cracks, hypercube shatter, light pillars. */
      void fxGrbFire(int n, double[] axis) {
         if (n >= 0 && n < 40 && n % 2 == 0) {
            double rad = 3.0 + n * 1.4;
            for (int tilt = 0; tilt < 3; tilt++) {
               double tr = tilt * Math.PI / 3.0;
               for (int i = 0; i < 16; i++) {
                  double ang = i * Math.PI * 2.0 / 16.0;
                  double x = Math.cos(ang) * rad;
                  double z = Math.sin(ang) * rad;
                  Fx.burst(this.lv, tilt == 1 ? this.gold : this.white, this.ox + x, this.oy + z * Math.sin(tr), this.oz + z * Math.cos(tr), 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
         if (n == 2) {
            for (int i = 0; i < 8; i++) {
               double ang = i * Math.PI / 4.0 + 0.2;
               Fx.line(this.lv, i % 2 == 0 ? this.white : this.gold, this.cx, this.cy + 0.2, this.cz, this.cx + Math.cos(ang) * 40.0, this.cy + 0.2, this.cz + Math.sin(ang) * 40.0, 8);
            }
         }
         if (n == 4) {
            Fx.shatter(this.lv, this.white, this.gold, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, this.ox, this.oy, this.oz, 6.0, 2.0);
         }
         if (n > 0 && n % 4 == 0 && n < 60) {
            for (int i = 0; i < 3; i++) {
               double ang = this.rnd.nextDouble() * Math.PI * 2.0;
               double rr = 8.0 + this.rnd.nextDouble() * 22.0;
               Fx.pillar(this.lv, this.white, this.cx + Math.cos(ang) * rr, this.cy, this.cy + 30.0, this.cz + Math.sin(ang) * rr);
            }
         }
      }

      HyperFx hyper;

      void tesseractPhase(int var1) {
         if (var1 == 520) {
            this.startTesseract();
         }

         int var2 = var1 - 520;
         if (var1 % 2 == 0 && var1 < 640) {
            double var3 = var1 < 590 ? 15.0 : 15.0 - 9.0 * ((double)(var1 - 590) / 50.0);
            if (this.hyper != null) {
               this.hyper.update(var3, (double)var1 * 0.05, 3);
            }
         }

         if (var1 % 4 == 0 && var1 >= 520 && var1 < 640) {
            double var20 = 16.0 - 8.0 * Math.max(0.0, (double)(var1 - 590) / 50.0);
            Fx.rune(this.lv, this.white, ParticleTypes.REVERSE_PORTAL, this.ox, this.cy + 0.3, this.oz, var20 + 6.0, (double)var1 * 0.05);
            Fx.helix(this.lv, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, this.ox, this.cy, this.oz, var20 * 0.5, Math.max(8.0, this.oy - this.cy), (double)var1 * 0.3);
         }

         if (var1 % 4 == 0 && var1 >= 520 && var1 < 640) {
            Shapes.poly4(this.lv, Shapes.Poly.CELL16, this.violet, ParticleTypes.END_ROD, this.ox, this.oy, this.oz, 6.0, (double)var1 * 0.07, 1);
         }

         if (var1 == 640) {
            if (this.hyper != null) {
               this.hyper.update(34.0, 3.0, 4);
               HyperFx hf = this.hyper;
               this.hyper = null;
               Scheduler.later(5, hf::remove);
            }
            Fx.shatter(this.lv, this.white, ParticleTypes.REVERSE_PORTAL, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, this.ox, this.oy, this.oz, 8.0, 4.0);
         }

         if (var1 >= 520 && var1 < 640) {
            this.holdAndGather(var1, var2);
         }

         if (var1 == 590) {
            this.msg("\u00a75\u00a7lThe hypercube closes...");
            this.snd("minecraft:block.beacon.deactivate", this.ox, this.oy, this.oz, 10.0F, 0.4F);
            this.snd("minecraft:entity.warden.sonic_charge", this.ox, this.oy, this.oz, 10.0F, 0.5F);
         }

         if (var1 > 590 && var1 < 640) {
            double var6 = (double)(var1 - 590) / 50.0;
            Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 100.0, 0.0F, (float)(0.5 + 1.5 * var6), 3, 11563263);
            if (var1 % 4 == 0) {
               Fx.ring(this.lv, this.violet, this.ox, this.oy, this.oz, 10.0 * (1.0 - var6) + 1.5, 18);
            }
         }

         if (var1 == 640) {
            this.detonate();
         }

         if (var1 > 640 && var1 % 6 == 0) {
            this.part("minecraft:totem_of_undying", this.ox, this.oy - 4.0, this.oz, 14.0, 10.0, 14.0, 0.4, 30);
         }
      }

      void startTesseract() {
         this.msg("\u00a7d\u00a7lTESSERACT");
         this.snd("minecraft:block.end_portal.spawn", this.ox, this.oy, this.oz, 12.0F, 0.5F);
         this.snd("minecraft:block.beacon.activate", this.ox, this.oy, this.oz, 10.0F, 0.4F);
         this.part("minecraft:flash", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
         Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 140.0, 0.8F, 1.2F, 20, 13607167);
         this.cmd("kill @e[tag=" + this.tag + "_oK]");
         this.hyper = HyperFx.spawn(this.lv, this.ox, this.oy, this.oz, "minecraft:gold_block", "minecraft:white_concrete", "minecraft:amethyst_block", "minecraft:glowstone");
         List<LivingEntity> var2 = this.pick(this.targets(), 40);

         for (int var3 = 0; var3 < var2.size(); var3++) {
            LivingEntity var4 = (LivingEntity)var2.get(var3);
            this.boxed.add(var4);
            this.boxOffset.add(new double[]{(this.rnd.nextDouble() - 0.5) * 12.0, (this.rnd.nextDouble() - 0.5) * 10.0, (this.rnd.nextDouble() - 0.5) * 12.0});
            if (var4 instanceof Mob var5 && !var5.isNoAi()) {
               var5.setNoAi(true);
               this.aiFrozen.add(var5.getUUID());
            }

            if (var4 instanceof Player) {
               this.cmd("effect give " + var4.getUUID() + " minecraft:slowness 5 255 true");
            }

            String var15 = this.tag + "_c" + var3;
            this.cageTags.add(var15);
            double var6 = Math.max(1.0, (double)var4.getBbWidth()) + 9.0;
            double var8 = Math.max(1.0, (double)var4.getBbHeight()) + 9.0;
            this.spawn(
               "minecraft:light_blue_stained_glass",
               var4.getX(),
               var4.getY(),
               var4.getZ(),
               var15,
               null,
               GoldenMight.xform(GoldenMight.Q_ID, var6, var8, var6, 0.5, 0.5, 0.5),
               8
            );
            double var10 = Math.max(1.0, (double)var4.getBbWidth()) + 0.8;
            double var12 = Math.max(1.0, (double)var4.getBbHeight()) + 0.8;
            Scheduler.later(2, () -> {
               if (!this.finished) {
                  this.merge(var15, GoldenMight.xform(GoldenMight.Q_ID, var10, var12, var10, 0.5, 0.5, 0.5), 8);
               }
            });
            Fx.burst(this.lv, ParticleTypes.END_ROD, var4.getX(), this.midY(var4), var4.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
         }
      }

      void updateCubes(int var1) {
         double var2 = var1 < 590 ? Math.min(1.0, (double)(var1 - 520) / 30.0) : 1.0;
         double var4 = 30.0 * var2;
         double var6 = 20.0 * var2;
         double var8 = 10.0 * var2;
         if (var1 < 590) {
            this.merge(
               this.tag + "_tO",
               GoldenMight.xform(
                  GoldenMight.qSpin(1.0, 1.0, 0.0, (double)var1 * 0.03), Math.max(0.3, var4), Math.max(0.3, var4), Math.max(0.3, var4), 0.5, 0.5, 0.5
               ),
               3
            );
         }

         this.merge(
            this.tag + "_tM",
            GoldenMight.xform(
               GoldenMight.qMul(GoldenMight.qSpin(0.0, 1.0, 0.0, (double)(-var1) * 0.045), GoldenMight.qSpin(1.0, 0.0, 1.0, 0.9553)),
               Math.max(0.3, var6),
               Math.max(0.3, var6),
               Math.max(0.3, var6),
               0.5,
               0.5,
               0.5
            ),
            3
         );
         this.merge(
            this.tag + "_tI",
            GoldenMight.xform(
               GoldenMight.qMul(GoldenMight.qSpin(1.0, 0.0, 1.0, (double)var1 * 0.07), GoldenMight.qSpin(0.0, 1.0, 0.0, 0.6)),
               Math.max(0.3, var8),
               Math.max(0.3, var8),
               Math.max(0.3, var8),
               0.5,
               0.5,
               0.5
            ),
            3
         );
      }

      void holdAndGather(int var1, int var2) {
         double var3 = var1 < 590 ? 0.06 : 0.14;

         for (int var5 = 0; var5 < this.boxed.size(); var5++) {
            LivingEntity var6 = this.boxed.get(var5);
            if (var6.isAlive() && !var6.isRemoved()) {
               double[] var7 = this.boxOffset.get(var5);
               double var8 = var1 < 590 ? 1.0 : Math.max(0.12, 1.0 - (double)(var1 - 590) / 50.0);
               double var10 = this.ox + var7[0] * var8;
               double var12 = this.oy + var7[1] * var8 - (double)var6.getBbHeight() * 0.5;
               double var14 = this.oz + var7[2] * var8;
               double var16 = var6.getX() + (var10 - var6.getX()) * var3;
               double var18 = var6.getY() + (var12 - var6.getY()) * var3;
               double var20 = var6.getZ() + (var14 - var6.getZ()) * var3;
               var6.teleportTo(var16, var18, var20);
               var6.setDeltaMovement(0.0, 0.0, 0.0);
               if (var1 % 2 == 0) {
                  this.cmd(String.format(Locale.ROOT, "tp @e[tag=%s,limit=1] %.3f %.3f %.3f", this.cageTags.get(var5), var16, var18, var20));
               }
            }
         }

         if (var2 % 20 == 10) {
            this.snd("minecraft:block.portal.ambient", this.ox, this.oy, this.oz, 10.0F, 0.6F);
         }
      }

      void detonate() {
         this.msg("\u00a7c\u00a7lDETONATION");
         this.snd("minecraft:entity.generic.explode", this.ox, this.oy, this.oz, 14.0F, 0.4F);
         this.snd("minecraft:entity.generic.explode", this.cx, this.cy, this.cz, 14.0F, 0.5F);
         this.snd("minecraft:entity.ender_dragon.death", this.ox, this.oy, this.oz, 14.0F, 0.5F);
         this.snd("minecraft:entity.lightning_bolt.thunder", this.cx, this.cy, this.cz, 14.0F, 0.5F);
         this.part("minecraft:flash", this.ox, this.oy, this.oz, 0.0, 0.0, 0.0, 0.0, 1);
         this.part("minecraft:explosion_emitter", this.ox, this.oy, this.oz, 6.0, 6.0, 6.0, 0.0, 10);
         this.part("minecraft:end_rod", this.ox, this.oy, this.oz, 8.0, 8.0, 8.0, 0.9, 300);
         this.part("minecraft:firework", this.ox, this.oy, this.oz, 10.0, 10.0, 10.0, 0.5, 120);
         Fx.screen(this.lv, new Vec3(this.cx, this.cy, this.cz), 200.0, 1.0F, 3.0F, 50, 16777215);
         Fx.epicImpact(this.lv, new Vec3(this.ox, this.oy, this.oz), ParticleTypes.EXPLOSION, ParticleTypes.PORTAL, 24.0, 16);

         for (int var1 = 0; var1 < 4; var1++) {
            this.lv
               .explode(
                  null,
                  this.ox + (this.rnd.nextDouble() - 0.5) * 8.0,
                  this.oy + (this.rnd.nextDouble() - 0.5) * 6.0,
                  this.oz + (this.rnd.nextDouble() - 0.5) * 8.0,
                  6.0F,
                  ExplosionInteraction.NONE
               );
         }

         for (int var12 = 0; var12 < this.boxed.size(); var12++) {
            LivingEntity var2 = this.boxed.get(var12);
            if (var2.isAlive()) {
               this.hurt(var2, 25000.0F);
               var2.igniteForSeconds(20);
               if (var2 instanceof Mob) {
                  Mob var3 = (Mob)var2;
                  if (this.aiFrozen.contains(var3.getUUID()) && var3.isAlive()) {
                     var3.setNoAi(false);
                  }
               }
            }
         }

         double var13 = 36.0;

         for (LivingEntity var5 : this.lv
            .getEntitiesOfClass(
               LivingEntity.class,
               new AABB(this.ox - var13, this.oy - var13, this.oz - var13, this.ox + var13, this.oy + var13, this.oz + var13),
               var1x -> var1x.isAlive() && !this.friend(var1x) && (!(var1x instanceof Player var2x) || !var2x.isSpectator())
            )) {
            this.hurt(var5, 25000.0F);
            double var6 = var5.getX() - this.ox;
            double var8 = var5.getZ() - this.oz;
            double var10 = Math.max(1.0, Math.sqrt(var6 * var6 + var8 * var8));
            var5.setDeltaMovement(var6 / var10 * 2.5, 1.2, var8 / var10 * 2.5);
            var5.hurtMarked = true;
         }

         this.cmd("kill @e[tag=" + this.tag + "_tO]");
         this.cmd("kill @e[tag=" + this.tag + "_tM]");
         this.cmd("kill @e[tag=" + this.tag + "_tI]");

         for (String var16 : this.cageTags) {
            this.cmd("kill @e[tag=" + var16 + "]");
         }
      }
   }
}
