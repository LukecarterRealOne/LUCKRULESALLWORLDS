package com.lucky.mod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public final class WardenGuards {
   private static final String OWN_TEAM = "lbm_guard";
   private static final int LIFETIME = 2420;
   private static final Map<UUID, WardenGuards.State> OWNERS = new HashMap<>();
   private static boolean resolved;
   private static Class<?> wardenClass;
   private static Method increaseAnger;
   private static Method digCooldown;
   private static Method getTeam;
   private static Method teamName;

   private WardenGuards() {
   }

   public static EntityType<?> wardenType() {
      try {
         Object var0 = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft", "warden"));
         if (var0 instanceof EntityType) {
            return (EntityType<?>)var0;
         }
      } catch (Throwable var2) {
      }

      return EntityType.IRON_GOLEM;
   }

   private static void resolve() {
      if (!resolved) {
         resolved = true;

         try {
            wardenClass = Class.forName("net.minecraft.world.entity.monster.warden.Warden");

            for (Method var3 : wardenClass.getDeclaredMethods()) {
               Class[] var4 = var3.getParameterTypes();
               if (Modifier.isPublic(var3.getModifiers())
                  && !Modifier.isStatic(var3.getModifiers())
                  && var3.getReturnType() == void.class
                  && var4.length == 3
                  && var4[0] == Entity.class
                  && var4[1] == int.class
                  && var4[2] == boolean.class) {
                  increaseAnger = var3;
               }
            }
         } catch (Throwable var11) {
         }

         try {
            Class var12 = Class.forName("net.minecraft.world.entity.monster.warden.WardenAi");
            Method var14 = null;
            int var16 = 0;

            for (Method var6 : var12.getDeclaredMethods()) {
               Class[] var7 = var6.getParameterTypes();
               if (Modifier.isPublic(var6.getModifiers())
                  && Modifier.isStatic(var6.getModifiers())
                  && var6.getReturnType() == void.class
                  && var7.length == 1
                  && var7[0] == LivingEntity.class) {
                  var14 = var6;
                  var16++;
               }
            }

            if (var16 == 1) {
               digCooldown = var14;
            }
         } catch (Throwable var10) {
         }

         try {
            Class var13 = Class.forName("net.minecraft.world.scores.Team");
            Method var15 = null;
            int var17 = 0;

            for (Method var26 : Entity.class.getDeclaredMethods()) {
               if (Modifier.isPublic(var26.getModifiers()) && var26.getParameterCount() == 0 && var26.getReturnType() == var13) {
                  var15 = var26;
                  var17++;
               }
            }

            if (var17 == 1) {
               getTeam = var15;
            }

            Method var20 = null;
            int var23 = 0;

            for (Method var8 : var13.getMethods()) {
               if (var8.getParameterCount() == 0
                  && var8.getReturnType() == String.class
                  && var8.getDeclaringClass() != Object.class
                  && !var8.getName().equals("toString")) {
                  var20 = var8;
                  var23++;
               }
            }

            if (var23 == 1) {
               teamName = var20;
            }
         } catch (Throwable var9) {
         }
      }
   }

   private static String[] ownerTeam(Player var0) {
      resolve();
      if (getTeam != null && teamName != null) {
         try {
            Object var1 = getTeam.invoke(var0);
            return var1 == null ? new String[]{null} : new String[]{(String)teamName.invoke(var1)};
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean friend(Entity var0, Player var1) {
      return var0 == var1 || var0.getTags().contains("lbm_ally");
   }

   public static void tune(ServerLevel var0, Player var1, Mob var2) {
      try {
         resolve();
         if (wardenClass == null || !wardenClass.isInstance(var2)) {
            return;
         }

         UUID var3 = var1.getUUID();
         WardenGuards.State var4 = OWNERS.get(var3);
         if (var4 == null) {
            var4 = new WardenGuards.State();
            OWNERS.put(var3, var4);
            String[] var5 = ownerTeam(var1);
            if (var5 != null && var5[0] == null) {
               Fx.cmd(var0, "team add lbm_guard");
               Fx.cmd(var0, "team join lbm_guard " + var3);
               var4.team = "lbm_guard";
               var4.joined = true;
            } else if (var5 != null) {
               var4.team = var5[0];
            }
         }

         var4.live++;
         if (var4.team != null) {
            Fx.cmd(var0, "team join " + var4.team + " " + var2.getUUID());
         }

         WardenGuards.State var7 = var4;
         Scheduler.later(5, () -> loop(var0, var1, var2, var7, 5));
      } catch (Throwable var6) {
         var6.printStackTrace();
      }
   }

   private static void loop(ServerLevel var0, Player var1, Mob var2, WardenGuards.State var3, int var4) {
      boolean var5 = var4 >= 2420 || !var2.isAlive() || var2.isRemoved() || !var1.isAlive();
      if (var5) {
         release(var0, var1, var3);
      } else {
         try {
            Fx.cmd(var0, "effect clear " + var1.getUUID() + " minecraft:darkness");
            if (digCooldown != null) {
               digCooldown.invoke(null, var2);
            }

            LivingEntity var6 = chooseTarget(var0, var1, var2);
            if (var6 != null && increaseAnger != null) {
               increaseAnger.invoke(var2, var6, 150, false);
            }
         } catch (Throwable var7) {
         }

         Scheduler.later(10, () -> loop(var0, var1, var2, var3, var4 + 10));
      }
   }

   private static LivingEntity chooseTarget(ServerLevel var0, Player var1, Mob var2) {
      LivingEntity var3 = var1.getLastHurtByMob();
      if (var3 != null && var3.isAlive() && !friend(var3, var1) && !wardenClass.isInstance(var3)) {
         return var3;
      } else {
         double var4 = var1.getX();
         double var6 = var1.getY();
         double var8 = var1.getZ();
         List<Mob> var10 = var0.getEntitiesOfClass(
            Mob.class,
            new AABB(var4 - 24.0, var6 - 12.0, var8 - 24.0, var4 + 24.0, var6 + 12.0, var8 + 24.0),
            var1x -> var1x instanceof Enemy && var1x.isAlive() && !friend(var1x, var1) && !wardenClass.isInstance(var1x)
         );
         Mob var11 = null;
         double var12 = Double.MAX_VALUE;

         for (Mob var15 : var10) {
            double var16 = var15.distanceToSqr(var2);
            if (var16 < var12) {
               var12 = var16;
               var11 = var15;
            }
         }

         return var11;
      }
   }

   private static void release(ServerLevel var0, Player var1, WardenGuards.State var2) {
      var2.live--;
      if (var2.live <= 0) {
         OWNERS.remove(var1.getUUID());
         if (var2.joined) {
            Fx.cmd(var0, "team leave " + var1.getUUID());
         }

         Fx.cmd(var0, "effect clear " + var1.getUUID() + " minecraft:darkness");
      }
   }

   private static final class State {
      int live;
      String team;
      boolean joined;

      private State() {
      }
   }
}
