package com.lucky.mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LuckyBlockItem extends Item {
   private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.0F), 2.0F);
   private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.65F, 0.0F), 1.4F);
   private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.3F, 1.0F, 0.4F), 1.3F);
   public static final Map<UUID, Integer> SELECTED = new HashMap<>();
   private static final Map<UUID, ResourceKey<Level>> SELECTED_DIMENSIONS = new HashMap<>();
   public static final Set<UUID> SHIELDED = new HashSet<>();
   private static final Map<UUID, Integer> SHIELD_TOKENS = new HashMap<>();
   private static final Set<Integer> ENDED = new HashSet<>();
   private static final int[] COOLDOWNS = new int[]{
      100, 24, 28, 20, 24, 20, 40, 45, 70, 200, 180, 55, 25, 35, 30, 25, 20, 120, 800, 80, 30, 45, 90, 25, 120, 60, 25, 45, 55, 15, 60, 45, 180, 35, 10, 240
   };
   private static final Map<UUID, Deque<LuckyBlockItem.PlayerSnapshot>> PLAYER_HISTORY = new HashMap<>();
   private static final int SNAPSHOT_CAPACITY = 100;
   private static int beamCounter = 0;
   private static List<EntityType<?>> PROJECTILE_TYPE_CACHE;
   private static final double MUZZLE = 3.5;
   private static final String[] BEAM_BLOCKS = new String[]{
      "minecraft:yellow_stained_glass", "minecraft:yellow_stained_glass", "minecraft:yellow_concrete", "minecraft:white_concrete"
   };
   private static final double[] BEAM_WIDTHS = new double[]{6.0, 3.4, 1.7, 0.8};
   private static final String[] ORB_BLOCKS = new String[]{"minecraft:cyan_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:white_concrete"};
   private static final double[] ORB_WIDTHS = new double[]{18.0, 11.0, 5.5};

   public LuckyBlockItem(Properties properties) {
      super(properties);
   }

   public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.literal("\u00a7ePassive: \u00a7fDouble-speed flight while carried"));
      tooltip.add(Component.literal("\u00a77Flight speed: 2\u00d7 vanilla creative speed"));
      tooltip.add(Component.literal("\u00a78Double-tap jump to take off"));
      if (Shards.isShattered(stack)) {
         tooltip.add(Component.literal("\u00a7c\u00a7lSHATTERED \u00a77- 12 shard abilities unlocked"));
      } else {
         tooltip.add(Component.literal("\u00a78If it takes a killing blow, it shatters..."));
      }
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
      ItemStack stack = user.getItemInHand(hand);
      if (level instanceof ServerLevel sw) {
         int a = user.isShiftKeyDown() ? 0 : SELECTED.getOrDefault(user.getUUID(), -1);
         int pool = Shards.isShattered(stack) ? Abilities.COUNT : Abilities.BASE;
         if (a >= pool) {
            msg(user, "\u00a7cThat ability needs a shattered Lucky Block");
            return InteractionResultHolder.fail(stack);
         }
         if (a < 0 || a >= Abilities.COUNT) {
            a = level.random.nextInt(pool);
         }

         this.castAbility(sw, user, a);
         user.getCooldowns().addCooldown(this, COOLDOWNS[a]);
         return InteractionResultHolder.success(stack);
      } else {
         return InteractionResultHolder.success(stack);
      }
   }

   public void castAbility(ServerLevel sw, Player user, int a) {
      Shapes.signature(sw, user, a);
      this.castCore(sw, user, a);
      if (a < Abilities.BASE) {
         Rework.after(sw, user, a);
      }
   }

   private void castCore(ServerLevel sw, Player user, int a) {
      if (a >= Abilities.BASE) {
         Shards.cast(sw, user, a);
         return;
      }
      switch (a) {
         case 0:
            this.omegaBeam(sw, user);
            break;
         case 1:
            this.lightningStorm(sw, user);
            break;
         case 2:
            this.meteorShower(sw, user);
            break;
         case 3:
            this.healingNova(sw, user);
            break;
         case 4:
            this.skyLaunch(sw, user);
            break;
         case 5:
            this.itemRain(sw, user);
            break;
         case 6:
            this.gravityWarp(sw, user);
            break;
         case 7:
            this.summonAllies(sw, user);
            break;
         case 8:
            this.orbitalStrike(sw, user);
            break;
         case 9:
            this.aegisShield(sw, user);
            break;
         case 10:
            this.guardianLegion(sw, user);
            break;
         case 11:
            this.blackHole(sw, user);
            break;
         case 12:
            this.frostNova(sw, user);
            break;
         case 13:
            this.tornado(sw, user);
            break;
         case 14:
            this.dragonBreath(sw, user);
            break;
         case 15:
            this.arrowStorm(sw, user);
            break;
         case 16:
            this.blinkStrike(sw, user);
            break;
         case 17:
            this.overdrive(sw, user);
            break;
         case 18:
            this.yellowFlight(sw, user);
            break;
         case 19:
            this.timeStop(sw, user);
            break;
         case 20:
            this.timeSurge(sw, user);
            break;
         case 21:
            this.voidRift(sw, user);
            break;
         case 22:
            this.giantTesseract(sw, user);
            break;
         case 23:
            this.allyCommand(sw, user);
            break;
         case 24:
            this.luckyAscension(sw, user);
            break;
         case 25:
            this.realityBreak(sw, user);
            break;
         case 26:
            this.chronoBurst(sw, user);
            break;
         case 27:
            this.dimensionTravel(sw, user);
            break;
         case 28:
            this.cosmicNova(sw, user);
            break;
         case 29:
            this.itemWish(sw, user);
            break;
         case 30:
            this.timeReplay(sw, user);
            break;
         case 31:
            this.spatialDeflection(sw, user);
            break;
         case 32:
            this.absoluteAegis(sw, user);
            break;
         case 33:
            this.thunderGod(sw, user);
            break;
         case 34:
            this.chooseDestinationDimension(sw, user);
            break;
         case 35:
            this.banishAreaToSelectedDimension(sw, user);
      }
   }

   public static void onSelect(ServerPlayer player, int ability, boolean castNow) {
      if (ability >= -1 && ability < Abilities.COUNT) {
         if (ability < 0) {
            SELECTED.remove(player.getUUID());
         } else {
            SELECTED.put(player.getUUID(), ability);
         }

         String name = ability < 0 ? "Random Roll" : Abilities.NAMES[ability];
         msg(player, "\u00a76Lucky Block ability: \u00a7e\u00a7l" + name);
         if (castNow && ability >= 0 && player.level() instanceof ServerLevel sw) {
            ItemStack held = player.getMainHandItem().getItem() instanceof LuckyBlockItem ? player.getMainHandItem() : player.getOffhandItem();
            if (!(held.getItem() instanceof LuckyBlockItem item)) {
               msg(player, "\u00a7cHold the Lucky Block to cast from the menu");
               return;
            }

            if (player.getCooldowns().isOnCooldown(item)) {
               msg(player, "\u00a7cLucky Block is recharging!");
               return;
            }

            if (ability >= Abilities.BASE && !Shards.isShattered(held)) {
               msg(player, "\u00a7cThat ability needs a shattered Lucky Block");
               return;
            }

            item.castAbility(sw, player, ability);
            player.getCooldowns().addCooldown(item, COOLDOWNS[ability]);
         }
      }
   }

   private static void msg(Player p, String text) {
      p.displayClientMessage(Component.literal(text), true);
   }

   private static boolean gone(ServerLevel sw, Player user) {
      return !user.isAlive() || user.isRemoved() || user.level() != sw;
   }

   private static boolean friend(Entity e, Player user) {
      return e == user || e.getTags().contains("lbm_ally");
   }

   private static void launch(Entity e, double x, double y, double z) {
      e.setDeltaMovement(x, y, z);
      e.hurtMarked = true;
      e.hasImpulse = true;
   }

   private static void push(Entity e, double x, double y, double z) {
      Vec3 v = e.getDeltaMovement();
      launch(e, v.x + x, v.y + y, v.z + z);
   }

   private static LuckyBlockItem.Beam aim(ServerLevel sw, Player user) {
      Vec3 eye = user.getEyePosition();
      Vec3 dir = user.getLookAngle();
      Vec3 end = eye.add(dir.scale(96.0));
      BlockHitResult hit = sw.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, user));
      boolean miss = hit.getType() == Type.MISS;
      double len = miss ? 96.0 : eye.distanceTo(hit.getLocation());
      return new LuckyBlockItem.Beam(eye, dir, len, miss ? end : hit.getLocation());
   }

   private static Vec3[] basis(Vec3 dir) {
      Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 right = dir.cross(up).normalize();
      Vec3 realUp = right.cross(dir).normalize();
      return new Vec3[]{right, realUp};
   }

   private static LuckyBlockItem.Target pickTarget(ServerLevel sw, Player user, double range) {
      Vec3 eye = user.getEyePosition();
      Vec3 dir = user.getLookAngle();
      Vec3 end = eye.add(dir.scale(range));
      BlockHitResult hit = sw.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, user));
      boolean miss = hit.getType() == Type.MISS;
      double bestT = miss ? range : eye.distanceTo(hit.getLocation());
      LivingEntity best = null;

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(2.0), x -> x != user)) {
         Vec3 c = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0);
         double t = c.subtract(eye).dot(dir);
         if (!(t < 0.0) && !(t > bestT) && eye.add(dir.scale(t)).distanceTo(c) <= 1.5 + (double)(e.getBbWidth() / 2.0F)) {
            best = e;
            bestT = t;
         }
      }

      return best != null
         ? new LuckyBlockItem.Target(best.position().add(0.0, (double)(best.getBbHeight() / 2.0F), 0.0), best)
         : new LuckyBlockItem.Target(miss ? end : hit.getLocation(), null);
   }

   private void omegaBeam(ServerLevel sw, Player user) {
      msg(user, "\u00a7e\u00a7l\u26a1 CHARGING YELLOW LASER BEAM... \u26a1");
      int charge = 30;
      int fire = 36;
      int id = ++beamCounter;
      user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 4, false, false));
      Fx.sound(sw, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
      Fx.screen(sw, user.position(), 48.0, 0.0F, 0.5F, 30, 16773242);

      for (int t = 0; t < 30; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> this.chargeTick(sw, user, tt, 30));
      }

      Scheduler.later(31, () -> this.fireStart(sw, user));

      for (int t = 0; t < 36; t++) {
         int tt = t;
         Scheduler.later(31 + t, () -> this.beamTick(sw, user, tt, 36, id));
      }

      Scheduler.later(68, () -> this.finale(sw, user, id));
   }

   private void chargeTick(ServerLevel sw, Player user, int t, int total) {
      if (!gone(sw, user)) {
         float p = (float)t / (float)total;
         Vec3 dir = user.getLookAngle();
         Vec3 c = user.getEyePosition().add(dir.scale(3.5)).add(0.0, -0.3, 0.0);
         Vec3[] ax = basis(dir);
         double radius = 6.0 * (double)(1.0F - p) + 0.4;
         int pts = 20;

         for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2) * (double)i / (double)pts + (double)t * 0.5;
            Vec3 o = ax[0].scale(Math.cos(a) * radius).add(ax[1].scale(Math.sin(a) * radius));
            Fx.burst(sw, GOLD, c.x + o.x, c.y + o.y, c.z + o.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
               Fx.burst(sw, ParticleTypes.END_ROD, c.x + o.x, c.y + o.y, c.z + o.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }

         DustParticleOptions orb = new DustParticleOptions(new Vector3f(1.0F, 0.92F, 0.1F), 1.0F + 3.0F * p);
         Fx.burst(sw, orb, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.0);
         if (t % 4 == 0) {
            Fx.sound(sw, user.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F, 0.5F + 1.5F * p);
         }

         if (t == total - 4) {
            Fx.screen(sw, user.position(), 48.0, 0.35F, 1.2F, 6, 16773242);
         }
      }
   }

   private void fireStart(ServerLevel sw, Player user) {
      if (!gone(sw, user)) {
         msg(user, "\u00a7e\u00a7l\u26a1 YELLOW LASER BEAM FIRED! \u26a1");
         Vec3 m = user.getEyePosition().add(user.getLookAngle().scale(3.5));
         Fx.sound(sw, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.7F);
         Fx.sound(sw, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 1.4F);
         Fx.burst(sw, ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0.0, 0.0, 0.0, 0.0);
         Fx.burst(sw, ParticleTypes.SONIC_BOOM, m.x, m.y, m.z, 1, 0.0, 0.0, 0.0, 0.0);
         Fx.screen(sw, user.position(), 80.0, 0.6F, 2.0F, 18, 16773242);
      }
   }

   private static String transformSnbt(Quaternionf q, double w, double len) {
      Vector3f tr = new Vector3f((float)(-w / 2.0), (float)(-w / 2.0), 0.0F);
      q.transform(tr);
      return String.format(
         Locale.ROOT,
         "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,%.4ff,%.4ff]}",
         q.x,
         q.y,
         q.z,
         q.w,
         w,
         w,
         len,
         tr.x,
         tr.y,
         tr.z
      );
   }

   private void beamTick(ServerLevel sw, Player user, int t, int total, int id) {
      if (!gone(sw, user)) {
         LuckyBlockItem.Beam b = aim(sw, user);
         Vec3 dir = b.dir();
         Vec3 origin = b.eye().add(dir.scale(3.5)).add(0.0, -0.3, 0.0);
         double len = Math.max(0.5, b.len() - 3.5);
         double grow = Math.min(1.0, (double)(t + 1) / 4.0);
         if (t >= total - 4) {
            grow = Math.min(grow, (double)(total - t) / 4.0);
         }

         double pulse = 1.0 + 0.06 * Math.sin((double)t * 1.7);
         Quaternionf q = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float)dir.x, (float)dir.y, (float)dir.z);

         for (int k = 0; k < BEAM_BLOCKS.length; k++) {
            double w = BEAM_WIDTHS[k] * grow * pulse;
            String tf = transformSnbt(q, w, len);
            if (t == 0) {
               Fx.cmd(
                  sw,
                  String.format(
                     Locale.ROOT,
                     "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},Tags:[\"lbm_beam\",\"lbm%d\",\"lbm%d_%d\"],view_range:4f,brightness:{sky:15,block:15},%s}",
                     origin.x,
                     origin.y,
                     origin.z,
                     BEAM_BLOCKS[k],
                     id,
                     id,
                     k,
                     tf
                  )
               );
            } else {
               Fx.cmd(
                  sw, String.format(Locale.ROOT, "data merge entity @e[tag=lbm%d_%d,limit=1] {%s,start_interpolation:0,interpolation_duration:1}", id, k, tf)
               );
            }
         }

         if (t > 0) {
            Fx.cmd(sw, String.format(Locale.ROOT, "tp @e[tag=lbm%d] %.3f %.3f %.3f", id, origin.x, origin.y, origin.z));
         }

         Vec3[] ax = basis(dir);

         for (double d = 3.5; d <= b.len(); d++) {
            Vec3 p = b.eye().add(dir.scale(d)).add(0.0, -0.3, 0.0);
            int idx = (int)(d / 1.5);
            if (idx % 2 == 0) {
               Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 2.0, 2.0, 2.0, 0.3);
            }

            if (idx % 3 == 0) {
               Fx.burst(sw, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 1.2, 1.2, 1.2, 0.03);
            }

            if (idx % 2 == 0) {
               double ang = d * 0.9 + (double)t * 0.9;

               for (int side = 0; side < 2; side++) {
                  double a = ang + (double)side * Math.PI;
                  double r = 3.0 * grow;
                  Vec3 o = ax[0].scale(Math.cos(a) * r).add(ax[1].scale(Math.sin(a) * r));
                  Fx.burst(sw, GOLD, p.x + o.x, p.y + o.y, p.z + o.z, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }

         if (t % 4 == 0) {
            this.damageAlong(sw, user, b, 70.0F, 4.5);
         }

         Vec3 im = b.impact();
         Fx.burst(sw, ParticleTypes.FLAME, im.x, im.y + 0.3, im.z, 14, 2.0, 0.6, 2.0, 0.08);
         Fx.burst(sw, ParticleTypes.LAVA, im.x, im.y + 0.3, im.z, 3, 2.0, 0.3, 2.0, 0.0);
         Fx.burst(sw, ParticleTypes.LARGE_SMOKE, im.x, im.y + 0.3, im.z, 7, 2.0, 0.8, 2.0, 0.04);
         Fx.burst(sw, YELLOW, im.x, im.y + 0.5, im.z, 14, 2.2, 0.8, 2.2, 0.0);
         if (t % 2 == 0) {
            Fx.ring(sw, YELLOW, im.x, im.y + 0.2, im.z, 2.0 + (double)(t % 10) * 0.8, 20);
         }

         for (double y = 0.0; y <= 60.0; y += 2.0) {
            Fx.burst(sw, YELLOW, im.x, im.y + y, im.z, 4, 0.9, 0.1, 0.9, 0.0);
         }

         if (t % 6 == 0) {
            Fx.sound(sw, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F);
         }
      }
   }

   private void damageAlong(ServerLevel sw, Player user, LuckyBlockItem.Beam b, float dmg, double radius) {
      Vec3 end = b.eye().add(b.dir().scale(b.len()));

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(b.eye(), end).inflate(radius + 0.5), ex -> !friend(ex, user))) {
         Vec3 center = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0);
         double t = Mth.clamp(center.subtract(b.eye()).dot(b.dir()), 0.0, b.len());
         Vec3 closest = b.eye().add(b.dir().scale(t));
         if (closest.distanceTo(center) <= radius) {
            e.hurt(sw.damageSources().indirectMagic(user, user), dmg);
            e.igniteForSeconds(10);
            push(e, b.dir().x * 0.8, 0.3, b.dir().z * 0.8);
            Fx.burst(sw, ParticleTypes.EXPLOSION, e.getX(), e.getY() + (double)(e.getBbHeight() / 2.0F), e.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   private void finale(ServerLevel sw, Player user, int id) {
      Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");
      if (!gone(sw, user)) {
         LuckyBlockItem.Beam b = aim(sw, user);
         Vec3 im = b.impact();
         sw.explode(user, im.x, im.y, im.z, 8.0F, ExplosionInteraction.NONE);
         Fx.epicImpact(sw, im, YELLOW, ParticleTypes.ELECTRIC_SPARK, 10.5, 16773242);
         Fx.sound(sw, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.6F);
         this.damageAlong(sw, user, b, 100.0F, 6.0);

         for (int k = 0; k < 4; k++) {
            double r = (double)(2 + k * 4);
            Scheduler.later(k * 2 + 1, () -> {
               Fx.ring(sw, YELLOW, im.x, im.y + 0.3, im.z, r, 28);
               Fx.ring(sw, ParticleTypes.END_ROD, im.x, im.y + 0.6, im.z, r, 20);
            });
         }
      }
   }

   private void lightningStorm(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7l24-STRIKE LIGHTNING \u2014 RANDOM AREA!");

      for (int i = 0; i < 24; i++) {
         double a = sw.random.nextDouble() * Math.PI * 2.0;
         double r = 4.0 + sw.random.nextDouble() * 7.0;
         Vec3 pos = user.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
         boolean screenFlashThisStrike = i % 4 == 0;
         Scheduler.later(i * 2 + 1, () -> {
            if (!gone(sw, user)) {
               Fx.ring(sw, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.1, pos.z, 2.0, 24);
               Fx.sound(sw, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.8F);
            }
         });
         Scheduler.later(
            i * 2 + 8,
            () -> {
               if (!gone(sw, user)) {
                  LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(sw);
                  if (bolt != null) {
                     bolt.moveTo(pos.x, pos.y, pos.z);
                     sw.addFreshEntity(bolt);
                  }

                  Fx.burst(sw, ParticleTypes.FLASH, pos.x, pos.y + 0.8, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
                  Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 18, 0.45, 0.9, 0.45, 0.25);
                  Fx.line(
                     sw,
                     ParticleTypes.ELECTRIC_SPARK,
                     pos.x,
                     pos.y + 10.0,
                     pos.z,
                     pos.x + (sw.random.nextDouble() - 0.5) * 1.5,
                     pos.y + 1.0,
                     pos.z + (sw.random.nextDouble() - 0.5) * 1.5,
                     7
                  );
                  Fx.ring(sw, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.1, pos.z, 1.6, 14);
                  Fx.ring(sw, ParticleTypes.END_ROD, pos.x, pos.y + 0.15, pos.z, 2.5, 12);
                  if (screenFlashThisStrike) {
                     Fx.screen(sw, pos, 64.0, 0.28F, 0.7F, 8, 12577279);
                  }
               }
            }
         );
      }
   }

   private void meteorShower(ServerLevel sw, Player user) {
      msg(user, "\u00a7c\u00a7l8-FIREBALL METEOR SHOWER!");
      Vec3 target = user.getEyePosition().add(user.getLookAngle().scale(18.0));
      List<LargeFireball> fireballs = new ArrayList<>();
      Fx.screen(sw, user.position(), 64.0, 0.3F, 0.8F, 20, 16742938);

      for (int i = 0; i < 8; i++) {
         Vec3 start = target.add((sw.random.nextDouble() - 0.5) * 12.0, 25.0 + sw.random.nextDouble() * 8.0, (sw.random.nextDouble() - 0.5) * 12.0);
         Scheduler.later(i * 4 + 1, () -> {
            LargeFireball fb = new LargeFireball(sw, user, new Vec3(0.0, -1.2, 0.0), 2);
            fb.setPos(start.x, start.y, start.z);
            sw.addFreshEntity(fb);
            fireballs.add(fb);
            Fx.burst(sw, ParticleTypes.FLAME, start.x, start.y, start.z, 16, 0.45, 0.45, 0.45, 0.08);
            Fx.sound(sw, user.blockPosition(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
         });
      }

      this.trail(sw, fireballs, 70);
   }

   private void trail(ServerLevel sw, List<LargeFireball> fireballs, int remaining) {
      if (remaining % 2 == 0) {
         for (LargeFireball fb : new ArrayList<>(fireballs)) {
            if (!fb.isRemoved()) {
               Fx.burst(sw, ParticleTypes.FLAME, fb.getX(), fb.getY(), fb.getZ(), 3, 0.22, 0.22, 0.22, 0.02);
               Fx.burst(sw, ParticleTypes.LARGE_SMOKE, fb.getX(), fb.getY(), fb.getZ(), 1, 0.18, 0.18, 0.18, 0.01);
               if (remaining % 12 == 0) {
                  Fx.burst(sw, ParticleTypes.LAVA, fb.getX(), fb.getY(), fb.getZ(), 1, 0.08, 0.08, 0.08, 0.0);
               }
            } else {
               Vec3 impact = new Vec3(fb.getX(), fb.getY(), fb.getZ());
               DustParticleOptions ember = new DustParticleOptions(new Vector3f(1.0F, 0.28F, 0.045F), 1.7F);
               Fx.epicImpact(sw, impact, ember, ParticleTypes.FLAME, 4.8, 16742938);
               Fx.sound(sw, BlockPos.containing(impact.x, impact.y, impact.z), SoundEvents.GENERIC_EXPLODE, 2.2F, 0.78F);
               fireballs.remove(fb);
            }
         }
      }

      if (remaining > 0) {
         Scheduler.later(1, () -> this.trail(sw, fireballs, remaining - 1));
      }
   }

   private void healingNova(ServerLevel sw, Player user) {
      msg(user, "\u00a7a\u00a7lAREA HEAL & CLEANSE!");
      user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
      user.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 3));
      user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
      user.setHealth(user.getMaxHealth());

      for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, new AABB(user.blockPosition()).inflate(18.0), e -> e.isAlive())) {
         entity.setHealth(entity.getMaxHealth());
         entity.removeEffect(MobEffects.POISON);
         entity.removeEffect(MobEffects.WITHER);
         entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
         entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 3));
         entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1));
      }

      Fx.sound(sw, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.2F);
      Fx.screen(sw, user.position(), 32.0, 0.3F, 0.0F, 1, 6750105);

      for (int t = 0; t < 10; t++) {
         int tt = t;
         Scheduler.later(t * 2 + 1, () -> {
            if (!gone(sw, user)) {
               double x = user.getX();
               double y = user.getY();
               double z = user.getZ();
               Fx.ring(sw, ParticleTypes.HAPPY_VILLAGER, x, y + 0.1, z, 0.6 + (double)tt * 0.65, 24);
               if (tt % 2 == 0) {
                  Fx.ring(sw, GREEN, x, y + 0.2, z, 0.6 + (double)tt * 0.65, 20);
               }

               double a = (double)tt * 0.7;
               Fx.burst(sw, GOLD, x + Math.cos(a), y + (double)tt * 0.12, z + Math.sin(a), 1, 0.05, 0.05, 0.05, 0.0);
               if (tt == 9) {
                  Fx.burst(sw, ParticleTypes.HEART, x, y + 1.5, z, 14, 0.5, 0.6, 0.5, 0.08);
               }
            }
         });
      }
   }

   private void skyLaunch(ServerLevel sw, Player user) {
      msg(user, "\u00a7d\u00a7lUPWARD LAUNCH + SLOW FALL!");
      push(user, 0.0, 2.2, 0.0);
      user.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
      Fx.sound(sw, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 1.0F);
      Fx.screen(sw, user.position(), 48.0, 0.5F, 2.0F, 8, 16777215);

      for (int k = 1; k <= 4; k++) {
         Fx.ring(sw, ParticleTypes.CLOUD, user.getX(), user.getY() + 0.1, user.getZ(), (double)k, 32);
      }

      for (int t = 0; t < 25; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> {
            if (!gone(sw, user)) {
               double x = user.getX();
               double y = user.getY();
               double z = user.getZ();
               Fx.burst(sw, ParticleTypes.FIREWORK, x, y, z, 6, 0.2, 0.1, 0.2, 0.05);
               Fx.burst(sw, ParticleTypes.CLOUD, x, y, z, 4, 0.3, 0.1, 0.3, 0.02);
               Fx.burst(sw, ParticleTypes.FLAME, x, y, z, 3, 0.15, 0.05, 0.15, 0.02);
               if (tt % 5 == 0) {
                  Fx.ring(sw, ParticleTypes.END_ROD, x, y, z, 1.0 + (double)tt * 0.1, 20);
               }
            }
         });
      }
   }

   private void itemRain(ServerLevel sw, Player user) {
      List<Item> all = new ArrayList<>();

      for (Item item : BuiltInRegistries.ITEM) {
         if (item != Items.AIR) {
            all.add(item);
         }
      }

      msg(user, "\u00a76\u00a7lRANDOM ITEM RAIN! (30 drops from " + all.size() + " registered items)");
      Vec3 base = user.position();
      Fx.sound(sw, user.blockPosition(), SoundEvents.PLAYER_LEVELUP, 2.0F, 1.0F);
      Fx.screen(sw, base, 32.0, 0.35F, 0.6F, 10, 16767050);

      for (int t = 0; t < 14; t += 2) {
         int tick = t;
         Scheduler.later(t + 1, () -> {
            for (double yx = 1.0; yx <= 13.0; yx += 3.0) {
               Fx.burst(sw, GOLD, base.x, base.y + yx, base.z, 1, 0.25, 0.0, 0.25, 0.0);
            }

            if (tick == 12) {
               Fx.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, base.x, base.y + 1.0, base.z, 24, 1.0, 1.0, 1.0, 0.12);
            }
         });
      }

      for (int i = 0; i < 30; i++) {
         Item itemx = all.get(sw.random.nextInt(all.size()));
         int count = 1 + sw.random.nextInt(Math.max(1, Math.min(itemx.getMaxStackSize(), 16)));
         ItemStack loot = new ItemStack(itemx, count);
         double x = base.x + (sw.random.nextDouble() - 0.5) * 6.0;
         double z = base.z + (sw.random.nextDouble() - 0.5) * 6.0;
         double y = base.y + 5.0 + sw.random.nextDouble() * 3.0;
         Scheduler.later(i * 2 + 2, () -> {
            sw.addFreshEntity(new ItemEntity(sw, x, y, z, loot));
            Fx.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 4, 0.16, 0.16, 0.16, 0.2);
         });
      }
   }

   private void gravityWarp(ServerLevel sw, Player user) {
      msg(user, "\u00a75\u00a7lGRAVITY LIFT & SLAM!");
      LuckyBlockItem.Target tg = pickTarget(sw, user, 48.0);
      Vec3 c = tg.pos();
      double r = 14.0;
      List<LivingEntity> victims = sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(14.0), e -> !friend(e, user) && e.distanceToSqr(c) <= 196.0);
      DustParticleOptions purple = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.6F);
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
      Fx.screen(sw, c, 64.0, 0.3F, 1.0F, 10, 11167487);

      for (LivingEntity e : victims) {
         e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 3, false, true));
         e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
      }

      for (int t = 0; t < 36; t += 2) {
         int tt = t;
         Scheduler.later(
            t + 1,
            () -> {
               double rise = (double)(tt % 24) * 0.5;

               for (int point = 0; point < 10; point++) {
                  double a = (double)point * Math.PI * 2.0 / 10.0 + (double)tt * 0.12;
                  double h = (double)point / 10.0 * 7.0;
                  Fx.burst(
                     sw, purple, c.x + Math.cos(a) * 14.0 * 0.55, c.y + rise + h, c.z + Math.sin(a) * 14.0 * 0.55, 1, 0.0, 0.0, 0.0, 0.0
                  );
               }

               for (LivingEntity e : victims) {
                  if (e.isAlive() && tt % 6 == 0) {
                     Fx.burst(sw, ParticleTypes.PORTAL, e.getX(), e.getY() + (double)(e.getBbHeight() / 2.0F), e.getZ(), 2, 0.2, 0.3, 0.2, 0.08);
                  }
               }

               if (tt % 12 == 0) {
                  Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.BEACON_AMBIENT, 1.6F, 0.6F);
               }
            }
         );
      }

      Scheduler.later(62, () -> {
         for (LivingEntity e : victims) {
            if (e.isAlive()) {
               e.removeEffect(MobEffects.LEVITATION);
               launch(e, 0.0, -3.5, 0.0);
               e.hurt(sw.damageSources().magic(), 36.0F);
            }
         }

         Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GENERIC_EXPLODE, 3.2F, 0.62F);
         Fx.epicImpact(sw, c, purple, ParticleTypes.PORTAL, 9.5, 11167487);

         for (int k = 0; k < 4; k++) {
            double rr = 3.0 + (double)k * 3.2;
            Scheduler.later(k * 2 + 1, () -> Fx.ring(sw, purple, c.x, c.y + 0.15, c.z, rr, 18));
         }
      });
   }

   private Mob spawnAlly(ServerLevel sw, Player user, EntityType<?> type, Vec3 pos, boolean guardian) {
      Entity e = null;

      try {
         e = type.create(sw);
      } catch (Exception var12) {
      }

      if (e instanceof Mob mob) {
         mob.moveTo(pos.x, pos.y, pos.z, user.getYRot(), 0.0F);

         try {
            mob.finalizeSpawn(sw, sw.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
         } catch (Exception var11) {
         }

         if (mob instanceof IronGolem golem) {
            golem.setPlayerCreated(true);
         }

         AllyCode.bind(mob, user);
         int dur = guardian ? 2500 : 1300;
         int amp = guardian ? 2 : 1;
         mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, amp));
         mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, amp));
         mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur, 1));
         mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
         if (guardian) {
            mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, 4));
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, dur, 0));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
            mob.setCustomName(Component.literal("\u00a7b\u00a7lGuardian"));
            mob.setCustomNameVisible(true);
         }

         mob.setTarget(null);
         if (!sw.addFreshEntity(mob)) {
            return null;
         }
         LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(sw);
         if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            sw.addFreshEntity(bolt);
         }

         Fx.ring(sw, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.1, pos.z, 2.0, 24);
         Fx.burst(sw, ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 1.2, 0.3, 0.1);
         Fx.burst(sw, ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 0.6, 1.0, 0.6, 0.5);
         return mob;
      } else {
         return null;
      }
   }

   private static List<EntityType<?>> ALLY_POOL;

   /** Every entity type in the game (vanilla + modded) that actually produces a Mob/NPC: golems, villagers, animals, monsters... */
   private static List<EntityType<?>> allyPool(ServerLevel sw) {
      if (ALLY_POOL == null) {
         List<EntityType<?>> out = new ArrayList<>();
         for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null || !type.canSummon() || type == EntityType.ENDER_DRAGON || type == EntityType.WITHER
               || type == EntityType.GIANT || type == EntityType.PLAYER || id.getNamespace().equals("luckyblockmod")) {
               continue;
            }
            try {
               Entity probe = type.create(sw);
               if (probe instanceof Mob) {
                  out.add(type);
               }
               if (probe != null) {
                  probe.discard();
               }
            } catch (Throwable ignored) {
            }
         }
         ALLY_POOL = out;
      }
      return new ArrayList<>(ALLY_POOL);
   }

   private void summonAllies(ServerLevel sw, Player user) {
      List<EntityType<?>> pool = allyPool(sw);
      java.util.Collections.shuffle(pool, new java.util.Random(sw.random.nextLong())); // 5 DIFFERENT types, no repeats

      msg(user, "\u00a7b\u00a7lCOMPLETELY RANDOM ALLY SQUAD! (" + pool.size() + " mob types in the pool)");
      List<Mob> allies = new ArrayList<>();
      Set<UUID> ids = new HashSet<>();
      StringBuilder names = new StringBuilder();

      for (int i = 0; i < 5; i++) {
         double a = (Math.PI * 2) * (double)i / 5.0;
         Vec3 pos = user.position().add(Math.cos(a) * 3.5, 0.0, Math.sin(a) * 3.5);
         Mob mob = null;

         for (int attempt = 0; attempt < 40 && mob == null && !pool.isEmpty(); attempt++) {
            mob = this.spawnAlly(sw, user, pool.remove(pool.size() - 1), pos, false);
         }

         if (mob != null) {
            allies.add(mob);
            ids.add(mob.getUUID());
            if (names.length() > 0) {
               names.append(", ");
            }

            names.append(mob.getType().getDescription().getString());
         }
      }

      user.sendSystemMessage(Component.literal("\u00a7bSummoned: " + names));
      Fx.sound(sw, user.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 1.0F);
      this.allyTick(sw, user, allies, ids, 1200, 100.0);
   }

   private void guardianLegion(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7lUP TO 6 WARDEN GUARDS!");
      List<Mob> allies = new ArrayList<>();
      Set<UUID> ids = new HashSet<>();
      int total = 6;

      for (int i = 0; i < total; i++) {
         double a = (Math.PI * 2) * (double)i / (double)total;
         Vec3 pos = user.position().add(Math.cos(a) * 4.5, 0.0, Math.sin(a) * 4.5);
         Mob mob = this.spawnAlly(sw, user, WardenGuards.wardenType(), pos, true);
         if (mob != null) {
            WardenGuards.tune(sw, user, mob);
            allies.add(mob);
            ids.add(mob.getUUID());
         }
      }

      Fx.sound(sw, user.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.7F);
      Fx.screen(sw, user.position(), 48.0, 0.4F, 2.0F, 12, 8969727);

      for (int k = 0; k < 5; k++) {
         double r = 2.0 + (double)k * 2.5;
         Scheduler.later(k + 1, () -> Fx.ring(sw, ParticleTypes.END_ROD, user.getX(), user.getY() + 0.2, user.getZ(), r, 40));
      }

      this.allyTick(sw, user, allies, ids, 2400, 36.0);
   }

   private void allyTick(ServerLevel sw, Player user, List<Mob> allies, Set<UUID> ids, int remaining, double followSq) {
      boolean any = false;
      LivingEntity attacker = user.getLastHurtByMob();

      for (Mob a : allies) {
         if (!a.isRemoved() && a.isAlive()) {
            any = true;
            if (remaining <= 0) {
               Fx.burst(sw, ParticleTypes.POOF, a.getX(), a.getY() + 1.0, a.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
               a.discard();
            } else {
               AllyCode.tick(sw, user, a, ids, remaining, followSq);

               LivingEntity cur = a.getTarget();
               if ((cur == null || !cur.isAlive()) && remaining % 5 == 0) {
                  Mob best = null;
                  double bd = Double.MAX_VALUE;

                  for (Mob f : sw.getEntitiesOfClass(Mob.class, a.getBoundingBox().inflate(20.0), m -> AllyCode.isFoe(m, ids))) {
                     double d = a.distanceToSqr(f);
                     if (d < bd) {
                        bd = d;
                        best = f;
                     }
                  }

                  if (best != null) {
                     a.setTarget(best);
                  } else if (a.distanceToSqr(user) > followSq) {
                     a.getNavigation().moveTo(user, 1.3);
                  }
               }
            }
         }
      }

      if (any && remaining > 0) {
         Scheduler.later(1, () -> this.allyTick(sw, user, allies, ids, remaining - 1, followSq));
      }
   }

   private static String verticalBeamSnbt(double w, double h) {
      return String.format(
         Locale.ROOT,
         "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,0f,%.4ff]}",
         w,
         h,
         w,
         -w / 2.0,
         -w / 2.0
      );
   }

   private void orbitalStrike(ServerLevel sw, Player user) {
      LuckyBlockItem.Target tg = pickTarget(sw, user, 128.0);
      Vec3 c = tg.pos();
      int charge = 32;
      int fire = 42;
      int id = ++beamCounter;
      BlockPos bp = BlockPos.containing(c.x, c.y, c.z);
      DustParticleOptions cyan = new DustParticleOptions(new Vector3f(0.3F, 0.9F, 1.0F), 2.0F);
      msg(user, "\u00a7b\u00a7lTARGETED ORBITAL BEAM CHARGING!");
      Fx.sound(sw, bp, SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);

      for (int t = 0; t < 32; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> {
            float p = (float)tt / 32.0F;
            double radius = (double)(12.0F * (1.0F - p) + 1.0F);
            if (tt % 2 == 0) {
               Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, radius, 24);
               Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, 12.0, 24);
               Fx.ring(sw, ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, 4.0, 12);
            }

            for (double y = 0.0; y <= 80.0; y += 8.0) {
               Fx.burst(sw, ParticleTypes.END_ROD, c.x, c.y + y, c.z, 1, 0.05, 0.0, 0.05, 0.0);
            }

            if (tt % 5 == 0) {
               Fx.sound(sw, bp, SoundEvents.EXPERIENCE_ORB_PICKUP, 3.0F, 0.5F + 1.5F * p);
            }

            if (tt == 27) {
               Fx.screen(sw, c, 200.0, 0.4F, 1.5F, 6, 12578815);
            }
         });
      }

      for (int t = 0; t < 42; t++) {
         int tt = t;
         Scheduler.later(33 + t, () -> this.orbitalTick(sw, user, c, cyan, tt, 42, id));
      }

      Scheduler.later(76, () -> this.orbitalFinale(sw, user, c, cyan, id));
   }

   private void orbitalTick(ServerLevel sw, Player user, Vec3 c, DustParticleOptions cyan, int t, int total, int id) {
      double grow = Math.min(1.0, (double)(t + 1) / 2.0);
      if (t >= total - 6) {
         grow = Math.min(grow, (double)(total - t) / 6.0);
      }

      double pulse = 1.0 + 0.05 * Math.sin((double)t * 1.9);

      for (int k = 0; k < ORB_BLOCKS.length; k++) {
         double w = ORB_WIDTHS[k] * grow * pulse;
         String tf = verticalBeamSnbt(w, 400.0);
         if (t == 0) {
            Fx.cmd(
               sw,
               String.format(
                  Locale.ROOT,
                  "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},Tags:[\"lbm_beam\",\"lbm%d\",\"lbm%d_%d\"],view_range:8f,brightness:{sky:15,block:15},%s}",
                  c.x,
                  c.y - 2.0,
                  c.z,
                  ORB_BLOCKS[k],
                  id,
                  id,
                  k,
                  tf
               )
            );
         } else if (t % 2 == 0) {
            Fx.cmd(sw, String.format(Locale.ROOT, "data merge entity @e[tag=lbm%d_%d,limit=1] {%s,start_interpolation:0,interpolation_duration:2}", id, k, tf));
         }
      }

      if (t == 0) {
         BlockPos bp = BlockPos.containing(c.x, c.y, c.z);
         Fx.sound(sw, bp, SoundEvents.WARDEN_SONIC_BOOM, 6.0F, 0.5F);
         Fx.sound(sw, bp, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.8F);
         Fx.screen(sw, c, 180.0, 0.65F, 3.0F, 28, 12578815);
      }

      if (t % 3 == 0) {
         AABB zone = new AABB(c.x - 9.0, c.y - 6.0, c.z - 9.0, c.x + 9.0, c.y + 60.0, c.z + 9.0);

         for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, zone, ex -> !friend(ex, user))) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            if (dx * dx + dz * dz <= 81.0) {
               e.hurt(sw.damageSources().indirectMagic(user, user), 60.0F);
               e.igniteForSeconds(10);
               push(e, 0.0, 0.6, 0.0);
            }
         }
      }

      Fx.burst(sw, ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 9, 3.0, 0.7, 3.0, 0.06);
      Fx.burst(sw, ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 2, 2.5, 0.3, 2.5, 0.0);
      Fx.burst(sw, ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 4, 3.0, 0.8, 3.0, 0.03);
      Fx.burst(sw, cyan, c.x, c.y + 0.5, c.z, 8, 3.0, 0.8, 3.0, 0.0);
      if (t % 3 == 0) {
         Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, 3.0 + (double)(t % 12) * 1.0, 24);
      }

      if (t % 6 == 0) {
         for (double y = 0.0; y <= 40.0; y += 8.0) {
            Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, c.x, c.y + y, c.z, 1, 2.0, 0.8, 2.0, 0.2);
         }
      }

      if (t % 6 == 0) {
         Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GENERIC_EXPLODE, 4.0F, 0.5F);
      }
   }

   private void orbitalFinale(ServerLevel sw, Player user, Vec3 c, DustParticleOptions cyan, int id) {
      Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");
      sw.explode(user, c.x, c.y, c.z, 10.0F, ExplosionInteraction.NONE);
      Fx.epicImpact(sw, c, cyan, ParticleTypes.END_ROD, 15.0, 12578815);
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.5F);
      AABB zone = new AABB(c.x - 12.0, c.y - 8.0, c.z - 12.0, c.x + 12.0, c.y + 20.0, c.z + 12.0);

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, zone, ex -> !friend(ex, user))) {
         if (e.distanceToSqr(c) <= 144.0) {
            e.hurt(sw.damageSources().indirectMagic(user, user), 100.0F);
            e.igniteForSeconds(10);
         }
      }

      for (int k = 0; k < 5; k++) {
         double r = (double)(3 + k * 4);
         Scheduler.later(k * 2 + 1, () -> {
            Fx.ring(sw, cyan, c.x, c.y + 0.3, c.z, r, 18);
            Fx.ring(sw, ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, r * 0.78, 18);
         });
      }
   }

   private void aegisShield(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7lPROJECTILE DEFLECTION FIELD ACTIVE!");
      int id = ++beamCounter;
      int total = 400;
      user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 2));
      user.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1));
      Fx.sound(sw, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, 3.0F, 1.4F);
      Fx.screen(sw, user.position(), 32.0, 0.4F, 1.0F, 8, 8969727);

      for (int t = 0; t < 400; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> this.shieldTick(sw, user, tt, 400, id));
      }

      Scheduler.later(402, () -> this.shieldEnd(sw, user, id));
   }

   private void shieldTick(ServerLevel sw, Player user, int t, int total, int id) {
      if (!ENDED.contains(id)) {
         if (gone(sw, user)) {
            this.shieldEnd(sw, user, id);
         } else {
            DustParticleOptions blue = new DustParticleOptions(new Vector3f(0.4F, 0.85F, 1.0F), 1.5F);
            Vec3 c = user.position().add(0.0, 1.0, 0.0);
            if (t == 0) {
               Fx.cmd(
                  sw,
                  String.format(
                     Locale.ROOT,
                     "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"minecraft:light_blue_stained_glass\"},Tags:[\"lbm_beam\",\"lbm%d\"],view_range:4f,brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[6.5f,6.5f,6.5f],translation:[-3.25f,-2.2f,-3.25f]}}",
                     user.getX(),
                     user.getY(),
                     user.getZ(),
                     id
                  )
               );
            } else if (t % 4 == 0) {
               Fx.cmd(sw, String.format(Locale.ROOT, "tp @e[tag=lbm%d] %.3f %.3f %.3f", id, user.getX(), user.getY(), user.getZ()));
            }

            if (t % 4 == 0) {
               for (int i = 0; i < 14; i++) {
                  double y = 1.0 - 2.0 * ((double)i + 0.5) / 14.0;
                  double rr = Math.sqrt(1.0 - y * y);
                  double a = (double)i * 2.399963 + (double)t * 0.08;
                  double px = c.x + Math.cos(a) * rr * 3.5;
                  double py = c.y + y * 3.5;
                  double pz = c.z + Math.sin(a) * rr * 3.5;
                  Fx.burst(sw, blue, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                  if (i % 5 == 0) {
                     Fx.burst(sw, ParticleTypes.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                  }
               }
            }

            user.clearFire();

            for (Projectile p : sw.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(7.0), px -> px.getOwner() != user && !px.getTags().contains("lbm_ally"))) {
               if (!(p.position().distanceTo(c) > 6.5)) {
                  Vec3 away = p.position().subtract(c);
                  if (away.lengthSqr() < 0.01) {
                     away = new Vec3(0.0, 1.0, 0.0);
                  }

                  away = away.normalize();
                  double speed = Math.max(1.5, p.getDeltaMovement().length() * 1.2);
                  launch(p, away.x * speed, away.y * speed + 0.05, away.z * speed);
                  p.setOwner(user);
                  Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY(), p.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
                  Fx.sound(sw, p.blockPosition(), SoundEvents.SHIELD_BLOCK, 1.5F, 1.2F);
               }
            }

            if (t % 2 == 0) {
               for (Mob m : sw.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(6.0), mx -> mx instanceof Enemy && !mx.getTags().contains("lbm_ally"))) {
                  Vec3 away = m.position().subtract(user.position());
                  away = new Vec3(away.x, 0.0, away.z);
                  if (away.lengthSqr() < 0.01) {
                     away = new Vec3(1.0, 0.0, 0.0);
                  }

                  away = away.normalize().scale(1.3);
                  push(m, away.x, 0.35, away.z);
                  Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + 1.0, m.getZ(), 6, 0.3, 0.5, 0.3, 0.2);
               }
            }

            if (t == total - 40) {
               msg(user, "\u00a7c\u00a7lShield ending soon...");
            }
         }
      }
   }

   private void shieldEnd(ServerLevel sw, Player user, int id) {
      if (ENDED.add(id)) {
         Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");
         Fx.sound(sw, user.blockPosition(), SoundEvents.GLASS_BREAK, 3.0F, 0.8F);
         Fx.burst(sw, ParticleTypes.END_ROD, user.getX(), user.getY() + 1.0, user.getZ(), 80, 3.0, 2.0, 3.0, 0.2);
      }
   }

   private void blackHole(ServerLevel sw, Player user) {
      msg(user, "\u00a78\u00a7lSUSTAINED BLACK HOLE PULL FIELD!");
      LuckyBlockItem.Target tg = pickTarget(sw, user, 60.0);
      Vec3 c = tg.pos().add(0.0, 1.5, 0.0);
      int id = ++beamCounter;
      int total = 120;
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);
      Fx.cmd(
         sw,
         String.format(
            Locale.ROOT,
            "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"minecraft:black_concrete\"},Tags:[\"lbm_beam\",\"lbm%d\"],view_range:4f,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[3f,3f,3f],translation:[-1.5f,-1.5f,-1.5f]}}",
            c.x,
            c.y,
            c.z,
            id
         )
      );

      for (int t = 0; t < 120; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> this.holeTick(sw, user, c, tt));
      }

      Scheduler.later(121, () -> this.holeEnd(sw, user, c, id));
   }

   private void holeTick(ServerLevel sw, Player user, Vec3 c, int t) {
      DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.5F, 0.1F, 0.9F), 1.8F);

      for (int ring = 0; ring < 3; ring++) {
         double r = 3.0 + (double)ring * 2.6;

         for (int i = 0; i < 9; i++) {
            double a = (Math.PI * 2) * (double)i / 9.0 + (double)t * (0.38 - (double)ring * 0.06);
            Fx.burst(
               sw,
               violet,
               c.x + Math.cos(a) * r,
               c.y + Math.sin(a * 2.0 + (double)t * 0.1) * 0.3,
               c.z + Math.sin(a) * r,
               1,
               0.0,
               0.0,
               0.0,
               0.0
            );
         }
      }

      if (t % 2 == 0) {
         Fx.burst(sw, ParticleTypes.PORTAL, c.x, c.y, c.z, 12, 3.0, 3.0, 3.0, 0.55);
      }

      if (t % 4 == 0) {
         Fx.burst(sw, ParticleTypes.SQUID_INK, c.x, c.y, c.z, 4, 1.2, 1.2, 1.2, 0.04);
      }

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(16.0), ex -> !friend(ex, user))) {
         Vec3 mid = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0);
         Vec3 to = c.subtract(mid);
         double d = to.length();
         if (!(d < 0.5)) {
            Vec3 pull = to.normalize().scale(Math.min(0.9, 6.0 / (d + 2.0)));
            Vec3 v = e.getDeltaMovement().scale(0.5).add(pull);
            launch(e, v.x, v.y, v.z);
            e.fallDistance = 0.0F;
            if (d < 3.0 && t % 10 == 0) {
               e.hurt(sw.damageSources().magic(), 8.0F);
            }
         }
      }

      if (t % 10 == 0) {
         Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.BEACON_AMBIENT, 3.0F, 0.5F);
         Fx.screen(sw, c, 40.0, 0.0F, 0.8F, 10, 7812044);
      }
   }

   private void holeEnd(ServerLevel sw, Player user, Vec3 c, int id) {
      Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");
      sw.explode(user, c.x, c.y, c.z, 6.0F, ExplosionInteraction.NONE);
      DustParticleOptions violetImpact = new DustParticleOptions(new Vector3f(0.64F, 0.22F, 1.0F), 2.1F);
      Fx.epicImpact(sw, c, violetImpact, ParticleTypes.REVERSE_PORTAL, 9.0, 11167487);
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GENERIC_EXPLODE, 4.0F, 0.5F);

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(16.0), ex -> !friend(ex, user))) {
         Vec3 away = e.position().subtract(c);
         away = away.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
         launch(e, away.x * 2.5, 0.8, away.z * 2.5);
         e.hurt(sw.damageSources().magic(), 40.0F);
      }

      DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.5F, 0.1F, 0.9F), 2.0F);

      for (int k = 0; k < 5; k++) {
         double r = 2.5 + (double)k * 3.1;
         Scheduler.later(k + 1, () -> {
            Fx.ring(sw, violet, c.x, c.y + 0.1, c.z, r, 18);
            Fx.ring(sw, ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.4, c.z, r * 0.72, 18);
         });
      }
   }

   private void frostNova(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7lFREEZE + 100K MAGIC HIT!");
      Vec3 c = user.position();
      double radius = 14.0;
      List<LivingEntity> victims = sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(14.0), e -> !friend(e, user) && e.distanceToSqr(c) <= 196.0);
      DustParticleOptions ice = new DustParticleOptions(new Vector3f(0.6F, 0.9F, 1.0F), 1.8F);
      Fx.sound(sw, user.blockPosition(), SoundEvents.GLASS_BREAK, 3.0F, 0.6F);
      Fx.screen(sw, c, 48.0, 0.5F, 1.5F, 10, 12578815);

      for (LivingEntity e : victims) {
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
         e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
         e.setTicksFrozen(300);
         e.hurt(sw.damageSources().magic(), 100000.0F);
      }

      for (int t = 0; t < 7; t++) {
         int tick = t;
         double r = (double)(t + 1) * 2.0;
         Scheduler.later(t + 1, () -> {
            Fx.ring(sw, ice, c.x, c.y + 0.3, c.z, r, 28);
            if (tick % 2 == 0) {
               Fx.ring(sw, ParticleTypes.SNOWFLAKE, c.x, c.y + 0.6, c.z, r, 24);
            }
         });
      }

      Scheduler.later(10, () -> {
         for (LivingEntity e : victims) {
            if (e.isAlive()) {
               e.hurt(sw.damageSources().magic(), 30.0F);
               Fx.burst(sw, ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + (double)(e.getBbHeight() / 2.0F), e.getZ(), 18, 0.45, 0.6, 0.45, 0.12);
            }
         }

         Fx.sound(sw, user.blockPosition(), SoundEvents.GLASS_BREAK, 3.0F, 1.1F);
      });
   }

   private void tornado(ServerLevel sw, Player user) {
      msg(user, "\u00a77\u00a7lHURRICANE LIFT & SPIN!");
      LuckyBlockItem.Target tg = pickTarget(sw, user, 50.0);
      Vec3 c = tg.pos();
      int total = 90;
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.PHANTOM_FLAP, 3.0F, 0.5F);
      DustParticleOptions gray = new DustParticleOptions(new Vector3f(0.8F, 0.8F, 0.85F), 1.8F);

      for (int t = 0; t < 90; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> {
            for (int h = 0; h <= 12; h += 3) {
               double r = 1.0 + (double)h * 0.48;

               for (int side = 0; side < 2; side++) {
                  double a = (double)tt * 0.42 + (double)h * 0.62 + (double)side * Math.PI;
                  double px = c.x + Math.cos(a) * r;
                  double py = c.y + (double)h * 1.0;
                  double pz = c.z + Math.sin(a) * r;
                  Fx.burst(sw, (ParticleOptions)(tt % 2 == 0 ? ParticleTypes.CLOUD : gray), px, py, pz, 1, 0.03, 0.03, 0.03, 0.01);
               }
            }

            if (tt % 4 == 0) {
               for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(18.0, 30.0, 18.0), ex -> !friend(ex, user))) {
                  double dx = e.getX() - c.x;
                  double dz = e.getZ() - c.z;
                  double d = Math.sqrt(dx * dx + dz * dz);
                  if (!(d > 18.0)) {
                     double len = Math.max(0.5, d);
                     double tx = -dz / len * 0.55;
                     double tz = dx / len * 0.55;
                     launch(e, tx - dx / len * 0.1, 0.2, tz - dz / len * 0.1);
                     e.fallDistance = 0.0F;
                     if (tt % 12 == 0) {
                        e.hurt(sw.damageSources().magic(), 5.0F);
                     }
                  }
               }
            }

            if (tt % 20 == 0) {
               Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.PHANTOM_FLAP, 3.0F, 0.6F);
               Fx.screen(sw, c, 40.0, 0.0F, 0.7F, 20, 14540253);
            }
         });
      }
   }

   private void dragonBreath(ServerLevel sw, Player user) {
      msg(user, "\u00a76\u00a7lDRAGON FIRE CONE!");
      Fx.sound(sw, user.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
      Fx.screen(sw, user.position(), 48.0, 0.18F, 0.7F, 20, 16742938);

      for (int t = 0; t < 28; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> this.breathTick(sw, user, tt));
      }
   }

   private void breathTick(ServerLevel sw, Player user, int t) {
      if (!gone(sw, user)) {
         Vec3 eye = user.getEyePosition();
         Vec3 dir = user.getLookAngle();

         for (double d = 2.0; d <= 22.0; d += 2.0) {
            Vec3 p = eye.add(dir.scale(d)).add(0.0, -0.3, 0.0);
            double spread = 0.3 + d * 0.22;
            Fx.burst(sw, ParticleTypes.FLAME, p.x, p.y, p.z, 2, spread * 0.4, spread * 0.4, spread * 0.4, 0.012);
            if ((int)d % 8 == 0) {
               Fx.burst(sw, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, spread * 0.3, spread * 0.3, spread * 0.3, 0.008);
            }
         }

         if (t % 4 == 0) {
            Vec3 end = eye.add(dir.scale(22.0));

            for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(6.0), ex -> !friend(ex, user))) {
               Vec3 to = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(eye);
               double dist = to.length();
               if (!(dist > 22.0) && !(dist < 0.1) && !(to.normalize().dot(dir) < 0.88)) {
                  e.hurt(sw.damageSources().magic(), 14.0F);
                  e.igniteForSeconds(8);
               }
            }

            Fx.sound(sw, user.blockPosition(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
         }
      }
   }

   private static List<EntityType<?>> getProjectileTypes(ServerLevel sw) {
      if (PROJECTILE_TYPE_CACHE != null) {
         return PROJECTILE_TYPE_CACHE;
      } else {
         List<EntityType<?>> found = new ArrayList<>();

         for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Entity probe = null;

            try {
               probe = type.create(sw);
            } catch (Exception var6) {
            }

            if (probe instanceof Projectile) {
               found.add(type);
            }

            if (probe != null) {
               probe.discard();
            }
         }

         if (found.isEmpty()) {
            found.add(EntityType.ARROW);
         }

         PROJECTILE_TYPE_CACHE = List.copyOf(found);
         return PROJECTILE_TYPE_CACHE;
      }
   }

   private static List<java.util.function.Function<ServerLevel, Entity>> SPECIAL_PROJECTILES;

   /** Plain/spectral arrow, an arrow for EVERY registered potion (harming, instant damage, poison...), and ender pearls. */
   private static List<java.util.function.Function<ServerLevel, Entity>> specialProjectiles() {
      if (SPECIAL_PROJECTILES == null) {
         List<java.util.function.Function<ServerLevel, Entity>> l = new ArrayList<>();
         l.add(w -> EntityType.ARROW.create(w));
         l.add(w -> EntityType.SPECTRAL_ARROW.create(w));
         l.add(w -> EntityType.ENDER_PEARL.create(w));
         for (net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion : (Iterable<net.minecraft.core.Holder.Reference<net.minecraft.world.item.alchemy.Potion>>)BuiltInRegistries.POTION.holders()::iterator) {
            if (potion.value() == net.minecraft.world.item.alchemy.Potions.WATER.value()) {
               continue;
            }
            l.add(w -> {
               net.minecraft.world.entity.projectile.Arrow a = EntityType.ARROW.create(w);
               if (a != null) {
                  a.setEffectsFromItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.TIPPED_ARROW, potion));
                  a.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
               }
               return a;
            });
         }
         SPECIAL_PROJECTILES = l;
      }
      return SPECIAL_PROJECTILES;
   }

   private void arrowStorm(ServerLevel sw, Player user) {
      msg(user, "\u00a7e\u00a7lVANILLA + MODDED PROJECTILE RAIN!");
      LuckyBlockItem.Target tg = pickTarget(sw, user, 100.0);
      Vec3 c = tg.pos();
      DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.6F);
      List<EntityType<?>> projectileTypes = getProjectileTypes(sw);
      Fx.sound(sw, BlockPos.containing(c.x, c.y, c.z), SoundEvents.ARROW_SHOOT, 3.0F, 0.6F);

      for (int t = 0; t < 40; t++) {
         int tt = t;
         Scheduler.later(t + 1, () -> {
            if (tt % 5 == 0) {
               Fx.ring(sw, red, c.x, c.y + 0.3, c.z, 12.0, 24);
            }

            for (int k = 0; k < 6; k++) {
               Entity spawned = null;

               try {
                  if (sw.random.nextInt(100) < 55) {
                     List<java.util.function.Function<ServerLevel, Entity>> specials = specialProjectiles();
                     spawned = specials.get(sw.random.nextInt(specials.size())).apply(sw);
                  } else {
                     spawned = projectileTypes.get(sw.random.nextInt(projectileTypes.size())).create(sw);
                  }
               } catch (Exception var14) {
               }

               if (spawned instanceof Projectile projectile) {
                  double ox = (sw.random.nextDouble() - 0.5) * 20.0;
                  double oz = (sw.random.nextDouble() - 0.5) * 20.0;
                  projectile.setPos(c.x + ox, c.y + 24.0 + sw.random.nextDouble() * 12.0, c.z + oz);
                  if (!(projectile instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl)) {
                     projectile.setOwner(user);
                  }
                  projectile.setDeltaMovement((sw.random.nextDouble() - 0.5) * 0.35, -2.8 - sw.random.nextDouble(), (sw.random.nextDouble() - 0.5) * 0.35);
                  sw.addFreshEntity(projectile);
               } else if (spawned != null) {
                  spawned.discard();
               }
            }
         });
      }
   }

   private void blinkStrike(ServerLevel sw, Player user) {
      msg(user, "\u00a7e\u00a7lBLINK TELEPORT + STRIKE!");
      Vec3 eye = user.getEyePosition();
      Vec3 dir = user.getLookAngle();
      LuckyBlockItem.Target tg = pickTarget(sw, user, 80.0);
      double dist = eye.distanceTo(tg.pos());
      Vec3 p = eye.add(dir.scale(Math.max(1.0, dist - 2.0)));
      Vec3 from = user.position();
      DustParticleOptions trail = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.0F), 1.8F);

      for (double d = 0.0; d <= dist; d++) {
         Vec3 q = eye.add(dir.scale(d));
         Fx.burst(sw, trail, q.x, q.y - 0.5, q.z, 3, 0.2, 0.2, 0.2, 0.0);
         Fx.burst(sw, ParticleTypes.END_ROD, q.x, q.y - 0.5, q.z, 1, 0.1, 0.1, 0.1, 0.02);
      }

      Fx.sound(sw, user.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.0F);
      if (user instanceof ServerPlayer sp) {
         sp.teleportTo(p.x, p.y - 1.0, p.z);
      } else {
         user.moveTo(p.x, p.y - 1.0, p.z);
      }

      Vec3 to = new Vec3(p.x, p.y - 1.0, p.z);

      for (Vec3 pos : new Vec3[]{from, to}) {
         LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(sw);
         if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            sw.addFreshEntity(bolt);
         }
      }

      Fx.sound(sw, BlockPos.containing(to.x, to.y, to.z), SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.7F);
      Fx.screen(sw, to, 48.0, 0.8F, 3.0F, 12, 16773242);

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(6.0), ex -> !friend(ex, user))) {
         if (!(e.distanceToSqr(to) > 36.0)) {
            e.hurt(sw.damageSources().indirectMagic(user, user), 30.0F);
            Vec3 away = e.position().subtract(to);
            away = away.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            launch(e, away.x * 1.5, 0.6, away.z * 1.5);
         }
      }

      for (int k = 0; k < 4; k++) {
         double r = 1.5 + (double)k * 1.5;
         Scheduler.later(k + 1, () -> Fx.ring(sw, trail, to.x, to.y + 0.3, to.z, r, 36));
      }
   }

   private void overdrive(ServerLevel sw, Player user) {
      msg(user, "\u00a7c\u00a7l15-SECOND OFFENSE BUFFS!");
      int dur = 300;
      user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 3));
      user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 3));
      user.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, dur, 3));
      user.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 2));
      Fx.sound(sw, user.blockPosition(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.65F);
      Fx.screen(sw, user.position(), 28.0, 0.22F, 0.45F, 7, 16733474);
      Fx.ring(sw, ParticleTypes.FLAME, user.getX(), user.getY() + 0.2, user.getZ(), 2.1, 20);
      Fx.burst(sw, ParticleTypes.CRIT, user.getX(), user.getY() + 1.0, user.getZ(), 18, 0.7, 0.8, 0.7, 0.12);

      for (int t = 0; t < 8; t++) {
         int tt = t;
         Scheduler.later(t * 24 + 1, () -> {
            if (!gone(sw, user)) {
               double a = (double)tt * Math.PI / 4.0;
               double x = user.getX() + Math.cos(a) * 0.75;
               double z = user.getZ() + Math.sin(a) * 0.75;
               Fx.burst(sw, ParticleTypes.FLAME, x, user.getY() + 0.8, z, 2, 0.02, 0.05, 0.02, 0.01);
            }
         });
      }
   }

   public static void captureSnapshots(MinecraftServer server) {
      Set<UUID> online = new HashSet<>();

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         UUID id = player.getUUID();
         online.add(id);
         Deque<LuckyBlockItem.PlayerSnapshot> history = PLAYER_HISTORY.computeIfAbsent(id, ignored -> new ArrayDeque<>());
         history.addLast(
            new LuckyBlockItem.PlayerSnapshot(player.position(), player.getHealth(), player.getYRot(), player.getXRot(), player.level().dimension())
         );

         while (history.size() > 100) {
            history.removeFirst();
         }
      }

      PLAYER_HISTORY.keySet().retainAll(online);
      SELECTED_DIMENSIONS.keySet().retainAll(online);
   }

   private static int activateShield(Player user, int duration) {
      UUID playerId = user.getUUID();
      int token = ++beamCounter;
      SHIELD_TOKENS.put(playerId, token);
      SHIELDED.add(playerId);
      Scheduler.later(duration, () -> {
         if (SHIELD_TOKENS.getOrDefault(playerId, -1) == token) {
            SHIELDED.remove(playerId);
            SHIELD_TOKENS.remove(playerId);
         }
      });
      return token;
   }

   private void yellowFlight(ServerLevel var1, Player var2) {
      GoldenMight.cast(var1, var2);
   }

   private void timeStop(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7l3-SECOND NEARBY MOB TIME STOP!");
      Vec3 center = user.position();
      List<Mob> stopped = sw.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(24.0), mob -> !friend(mob, user));
      Map<Mob, Boolean> previousAi = new HashMap<>();

      for (Mob mob : stopped) {
         previousAi.put(mob, mob.isNoAi());
         mob.setNoAi(true);
         mob.setDeltaMovement(Vec3.ZERO);
         mob.hurtMarked = true;
      }

      for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(24.0), target -> target != user && !friend(target, user))) {
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 65, 7, false, false));
         entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 65, 4, false, false));
         entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 65, 0, false, false));
      }

      user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 2));
      Fx.sound(sw, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, 2.4F, 0.6F);
      Fx.screen(sw, center, 80.0, 0.3F, 0.5F, 8, 4513279);

      for (int i = 0; i < 4; i++) {
         int tick = i;
         Scheduler.later(
            i * 3 + 1,
            () -> Fx.ring(sw, ParticleTypes.END_ROD, center.x, center.y + 0.5 + (double)tick * 0.5, center.z, (double)(3 + tick * 2), 24)
         );
      }

      this.holdTimeStop(sw, stopped, previousAi, 60);
   }

   private void holdTimeStop(ServerLevel sw, List<Mob> stopped, Map<Mob, Boolean> previousAi, int ticksRemaining) {
      for (Mob mob : stopped) {
         if (mob.isAlive() && !mob.isRemoved()) {
            mob.setDeltaMovement(Vec3.ZERO);
            mob.hurtMarked = true;
         }
      }

      if (ticksRemaining <= 0) {
         for (Mob mobx : stopped) {
            Boolean hadNoAi = previousAi.get(mobx);
            if (hadNoAi != null && !mobx.isRemoved()) {
               mobx.setNoAi(hadNoAi);
            }
         }
      } else {
         Scheduler.later(1, () -> this.holdTimeStop(sw, stopped, previousAi, ticksRemaining - 1));
      }
   }

   private void timeSurge(ServerLevel sw, Player user) {
      msg(user, "\u00a7d\u00a7lSPEED DASH + HASTE!");
      Vec3 direction = user.getLookAngle();
      push(user, direction.x * 1.8, Math.max(0.15, direction.y * 1.25), direction.z * 1.8);
      user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 180, 5));
      user.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 180, 4));
      user.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 2));
      user.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 180, 0));
      Vec3 start = user.position().subtract(direction.scale(4.0)).add(0.0, 0.7, 0.0);
      Vec3 end = user.position().add(0.0, 0.7, 0.0);
      Fx.line(sw, ParticleTypes.END_ROD, start.x, start.y, start.z, end.x, end.y, end.z, 14);
      Fx.sound(sw, user.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, 1.8F, 1.8F);
      Fx.screen(sw, user.position(), 48.0, 0.28F, 0.5F, 8, 13395711);
   }

   private void voidRift(ServerLevel sw, Player user) {
      msg(user, "\u00a75\u00a7lVOID RIFT \u2014 DAMAGE + DEBUFFS!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 90.0);
      Vec3 center = target.pos();

      for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(12.0), e -> !friend(e, user) && e.distanceToSqr(center) <= 144.0)) {
         entity.hurt(sw.damageSources().magic(), 58.0F);
         if (entity.isAlive()) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 180, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 2));
            Vec3 inward = center.subtract(entity.position());
            if (inward.lengthSqr() > 0.01) {
               push(entity, inward.normalize().x * 1.2, 0.35, inward.normalize().z * 1.2);
            }
         }
      }

      Fx.line(
         sw,
         ParticleTypes.REVERSE_PORTAL,
         center.x - 3.0,
         center.y - 2.0,
         center.z,
         center.x + 2.2,
         center.y + 5.0,
         center.z,
         18
      );
      Fx.line(
         sw,
         ParticleTypes.PORTAL,
         center.x + 3.0,
         center.y - 2.0,
         center.z + 0.15,
         center.x - 2.2,
         center.y + 5.0,
         center.z + 0.15,
         18
      );
      Fx.burst(sw, ParticleTypes.SQUID_INK, center.x, center.y + 1.5, center.z, 32, 1.2, 2.5, 1.2, 0.04);
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.RESPAWN_ANCHOR_DEPLETE, 2.6F, 0.7F);
      Fx.screen(sw, center, 80.0, 0.45F, 1.0F, 10, 6684774);
   }

   private void giantTesseract(ServerLevel sw, Player user) {
      msg(user, "\u00a75\u00a7lGIANT 4D TESSERACT SLAM!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 90.0);
      Vec3 center = target.pos();
      Vec3 frameCenter = center.add(0.0, 10.0, 0.0);
      DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.12F), 1.55F);
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.WARDEN_SONIC_BOOM, 3.2F, 0.42F);
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.6F, 0.55F);
      Fx.screen(sw, center, 100.0, 0.22F, 0.75F, 10, 16773222);
      Fx.ring(sw, gold, center.x, center.y + 0.15, center.z, 5.0, 18);
      Fx.ring(sw, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.25, center.z, 8.0, 18);

      Hypercube hc = new Hypercube(sw, frameCenter, "minecraft:glowstone", "minecraft:gold_block", "minecraft:yellow_concrete", "minecraft:white_concrete");
      Scheduler.later(120, hc::remove);

      for (int tick = 0; tick <= 42; tick += 2) {
         int elapsed = tick;
         Scheduler.later(
            tick + 1,
            () -> {
               double progress = (double)elapsed / 42.0;
               double size = 13.5 - progress * 9.7;
               hc.update(size, (double)elapsed * 0.07);
               Fx.rune(sw, gold, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.15, center.z, 14.0 - progress * 6.0, (double)elapsed * 0.12);
               Fx.helix(sw, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 3.0 + (1.0 - progress) * 5.0, 10.0, (double)elapsed * 0.35);
               if (elapsed % 6 == 0) {
                  for (int c = 0; c < 4; c++) {
                     double ca = (double)elapsed * 0.1 + c * Math.PI / 2.0;
                     Fx.pillar(sw, ParticleTypes.END_ROD, center.x + Math.cos(ca) * size * 0.8, center.y, center.z + Math.sin(ca) * size * 0.8, frameCenter.y);
                  }
                  Fx.burst(sw, ParticleTypes.PORTAL, frameCenter.x, frameCenter.y, frameCenter.z, 7, 0.25, 0.45, 0.25, 0.08);
                  AABB pullZone = new AABB(center, center).inflate(19.0);

                  for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, pullZone, e -> !friend(e, user) && e.distanceToSqr(center) <= 361.0)) {
                     Vec3 inward = center.subtract(entity.position().add(0.0, (double)entity.getBbHeight() * 0.5, 0.0));
                     if (inward.lengthSqr() > 0.05) {
                        Vec3 direction = inward.normalize();
                        push(entity, direction.x * 0.65, direction.y * 0.45 + 0.18, direction.z * 0.65);
                     }
                  }
               }
            }
         );
      }

      Scheduler.later(46, () -> {
         AABB capture = new AABB(center, center).inflate(18.0);

         for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, capture, e -> !friend(e, user) && e.distanceToSqr(center) <= 324.0)) {
            if (entity.isAlive()) {
               Vec3 inward = center.subtract(entity.position());
               double length = Math.max(0.001, Math.sqrt(inward.x * inward.x + inward.z * inward.z));
               launch(entity, inward.x / length * 0.45, -2.6, inward.z / length * 0.45);
               entity.hurt(sw.damageSources().magic(), 65.0F);
            }
         }

         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.END_PORTAL_SPAWN, 4.0F, 0.55F);
         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.GENERIC_EXPLODE, 3.6F, 0.45F);
         Fx.epicImpact(sw, center.add(0.0, 1.2, 0.0), gold, ParticleTypes.ELECTRIC_SPARK, 13.5, 16773222);
         for (int k = 0; k < 7; k++) {
            int kk = k;
            Scheduler.later(kk, () -> {
               if (kk == 6) {
                  hc.remove();
               } else {
                  hc.update(3.8 * (1.0 + kk * 0.7), 3.0 + kk * 0.45);
               }
            });
         }
         Fx.burst(sw, ParticleTypes.FIREWORK, center.x, center.y + 1.0, center.z, 24, 4.0, 2.5, 4.0, 0.35);
         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.6F);

         for (int pulse = 0; pulse < 4; pulse++) {
            int index = pulse;
            Scheduler.later(pulse * 2 + 1, () -> {
               double radius = 3.5 + (double)index * 4.0;
               Fx.ring(sw, gold, center.x, center.y + 0.2, center.z, radius, 18);
               Fx.ring(sw, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.45, center.z, radius * 0.76, 18);
            });
         }
      });
   }

   private void allyCommand(ServerLevel sw, Player user) {
      msg(user, "\u00a7a\u00a7lORDER ALLIES TO ATTACK!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 100.0);
      LivingEntity enemy = target.entity() != null && target.entity().isAlive() && !friend(target.entity(), user) ? target.entity() : null;
      String ownerTag = "lbm_owner_" + user.getUUID();
      int count = 0;

      for (Entity entity : sw.getAllEntities()) {
         if (entity instanceof Mob) {
            Mob mob = (Mob)entity;
            if (mob.isAlive() && mob.getTags().contains(ownerTag)) {
               mob.setTarget(enemy);
               mob.setAggressive(enemy != null);
               mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 3));
               mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 2));
               if (enemy != null) {
                  mob.getNavigation().moveTo(enemy, 1.4);
               }

               count++;
            }
         }
      }

      msg(user, "\u00a7eCommand sent to " + count + " of your allies.");
      Fx.burst(sw, ParticleTypes.HAPPY_VILLAGER, user.getX(), user.getY() + 1.0, user.getZ(), 24, 1.0, 1.0, 1.0, 0.12);
      Fx.sound(sw, user.blockPosition(), SoundEvents.VILLAGER_YES, 1.6F, 1.2F);
   }

   private void luckyAscension(ServerLevel sw, Player user) {
      msg(user, "\u00a76\u00a7lFULL HEAL + ASCENSION BUFFS!");
      user.setHealth(user.getMaxHealth());
      MobEffectInstance[] buffs = new MobEffectInstance[]{
         new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 4),
         new MobEffectInstance(MobEffects.REGENERATION, 2400, 4),
         new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 5),
         new MobEffectInstance(MobEffects.ABSORPTION, 2400, 8),
         new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 3),
         new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0),
         new MobEffectInstance(MobEffects.WATER_BREATHING, 2400, 0),
         new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0)
      };

      for (MobEffectInstance effect : buffs) {
         user.addEffect(effect);
      }

      Fx.screen(sw, user.position(), 80.0, 0.65F, 1.5F, 14, 16776960);
      Fx.sound(sw, user.blockPosition(), SoundEvents.PLAYER_LEVELUP, 3.5F, 0.75F);
      Fx.ring(sw, YELLOW, user.getX(), user.getY() + 0.25, user.getZ(), 3.5, 24);
      Fx.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, user.getX(), user.getY() + 1.0, user.getZ(), 32, 0.6, 0.9, 0.6, 0.12);
   }

   private void realityBreak(ServerLevel sw, Player user) {
      msg(user, "\u00a7c\u00a7lREALITY SHUFFLE \u2014 POSITION SWAP!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 100.0);
      Vec3 center = target.pos();
      List<LivingEntity> victims = sw.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(16.0), e -> !friend(e, user) && e.distanceToSqr(center) <= 256.0);
      List<Vec3> positions = new ArrayList<>(victims.size());

      for (LivingEntity entity : victims) {
         positions.add(entity.position());
      }

      if (positions.size() > 1) {
         int shift = 1 + sw.random.nextInt(positions.size() - 1);
         List<Vec3> rotated = new ArrayList<>(positions.size());

         for (int i = 0; i < positions.size(); i++) {
            rotated.add(positions.get((i + shift) % positions.size()));
         }

         positions = rotated;
      }

      for (int i = 0; i < victims.size(); i++) {
         LivingEntity entity = victims.get(i);
         entity.hurt(sw.damageSources().magic(), 18.0F);
         entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
         Vec3 destination;
         if (victims.size() == 1) {
            double angle = sw.random.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + sw.random.nextDouble() * 5.0;
            destination = entity.position().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
         } else {
            destination = positions.get(i);
         }

         entity.teleportTo(destination.x, destination.y, destination.z);
      }

      Fx.line(
         sw,
         ParticleTypes.CRIT,
         center.x - 7.0,
         center.y - 2.0,
         center.z - 1.0,
         center.x + 7.0,
         center.y + 5.0,
         center.z + 1.0,
         16
      );
      Fx.line(
         sw,
         ParticleTypes.PORTAL,
         center.x + 7.0,
         center.y - 2.0,
         center.z + 1.0,
         center.x - 7.0,
         center.y + 5.0,
         center.z - 1.0,
         16
      );
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.GLASS_BREAK, 2.2F, 0.7F);
      Fx.screen(sw, center, 90.0, 0.24F, 0.8F, 8, 16720554);
   }

   private void chronoBurst(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7lDELAYED CHRONO STRIKE!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 72.0);
      Vec3 center = target.pos();
      List<LivingEntity> marked = sw.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(12.0), e -> !friend(e, user) && e.distanceToSqr(center) <= 144.0);

      for (LivingEntity entity : marked) {
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 7));
         entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 1));
         entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
      }

      user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 2));
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.AMETHYST_BLOCK_CHIME, 2.3F, 0.45F);
      Fx.line(sw, ParticleTypes.END_ROD, center.x, center.y - 2.0, center.z, center.x, center.y + 6.0, center.z, 12);
      Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1.0, center.z, 18, 1.2, 1.4, 1.2, 0.04);
      Fx.screen(sw, center, 64.0, 0.2F, 0.5F, 7, 65535);
      Scheduler.later(
         40,
         () -> {
            for (LivingEntity entityx : marked) {
               if (entityx.isAlive() && !entityx.isRemoved() && entityx.level() == sw && entityx.distanceToSqr(center) <= 196.0) {
                  entityx.hurt(sw.damageSources().magic(), 28.0F);
                  Fx.burst(
                     sw,
                     ParticleTypes.ELECTRIC_SPARK,
                     entityx.getX(),
                     entityx.getY() + (double)(entityx.getBbHeight() / 2.0F),
                     entityx.getZ(),
                     5,
                     0.15,
                     0.25,
                     0.15,
                     0.08
                  );
               }
            }

            Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.2F, 1.3F);
         }
      );
   }

   private void chooseDestinationDimension(ServerLevel sw, Player user) {
      if (user instanceof ServerPlayer player && player.getServer() != null) {
         List<String> dimensions = new ArrayList<>();

         for (ServerLevel level : player.getServer().getAllLevels()) {
            dimensions.add(level.dimension().location().toString());
         }

         dimensions.sort(Comparator.<String>comparingInt(id -> {
            return switch (id) {
               case "minecraft:overworld" -> 0;
               case "minecraft:the_nether" -> 1;
               case "minecraft:the_end" -> 2;
               default -> 3;
            };
         }).thenComparing(String::compareToIgnoreCase));
         String selected = SELECTED_DIMENSIONS.containsKey(player.getUUID()) ? SELECTED_DIMENSIONS.get(player.getUUID()).location().toString() : "";
         net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new OpenDimensionPickerPacket(dimensions, selected));
         msg(player, "\u00a76Choose a destination dimension (vanilla + modded).\u00a7r");
         Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.7, 0.4, 0.08);
         return;
      }
   }

   public static void onDimensionSelected(ServerPlayer player, String dimensionId) {
      if (player.getServer() != null && dimensionId != null && dimensionId.length() <= 256) {
         ResourceLocation location = ResourceLocation.tryParse(dimensionId);
         if (location == null) {
            msg(player, "\u00a7cThat dimension ID is invalid.");
         } else {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
            ServerLevel dimension = player.getServer().getLevel(key);
            if (dimension == null) {
               msg(player, "\u00a7cThat dimension is not active on this server.");
            } else {
               SELECTED_DIMENSIONS.put(player.getUUID(), key);
               msg(
                  player, "\u00a7dDestination set: \u00a7f" + dimensionId + "\u00a7r  | Use Travel to Selected Dimension or Banish Area to Selected Dimension."
               );
               Fx.burst(
                  dimension,
                  ParticleTypes.PORTAL,
                  (double)dimension.getSharedSpawnPos().getX() + 0.5,
                  (double)(dimension.getSharedSpawnPos().getY() + 1),
                  (double)dimension.getSharedSpawnPos().getZ() + 0.5,
                  12,
                  0.4,
                  0.7,
                  0.4,
                  0.06
               );
            }
         }
      }
   }

   private static ServerLevel selectedDestination(ServerPlayer player) {
      if (player.getServer() == null) {
         return null;
      } else {
         ResourceKey<Level> selected = SELECTED_DIMENSIONS.get(player.getUUID());
         return selected == null ? null : player.getServer().getLevel(selected);
      }
   }

   private static BlockPos findArrival(ServerLevel destination, int slot) {
      BlockPos spawn = destination.getSharedSpawnPos();
      int offsetX = slot == 0 ? 0 : (int)Math.round(Math.cos((double)slot * 2.399963) * (double)(2 + slot % 5));
      int offsetZ = slot == 0 ? 0 : (int)Math.round(Math.sin((double)slot * 2.399963) * (double)(2 + slot % 5));

      for (int radius = 0; radius <= 5; radius++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (radius <= 0 || Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                  int x = spawn.getX() + offsetX + dx;
                  int z = spawn.getZ() + offsetZ + dz;

                  for (int dy = -2; dy <= 24; dy++) {
                     int y = spawn.getY() + dy;
                     if (y >= destination.getMinBuildHeight() + 1 && y < destination.getMaxBuildHeight() - 2) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (destination.getBlockState(candidate).isAir()
                           && destination.getBlockState(candidate.above()).isAir()
                           && destination.getBlockState(candidate.below()).blocksMotion()) {
                           return candidate;
                        }
                     }
                  }
               }
            }
         }
      }

      int y = Math.max(destination.getMinBuildHeight() + 2, Math.min(spawn.getY() + 6, destination.getMaxBuildHeight() - 3));
      return new BlockPos(spawn.getX() + offsetX, y, spawn.getZ() + offsetZ);
   }

   private static net.minecraft.world.level.portal.DimensionTransition directTransition(Entity entity, ServerLevel destination, final BlockPos arrival) {
      Vec3 pos = new Vec3((double)arrival.getX() + 0.5, (double)arrival.getY(), (double)arrival.getZ() + 0.5);
      return new net.minecraft.world.level.portal.DimensionTransition(destination, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
   }

   private void dimensionTravel(ServerLevel sw, Player user) {
      if (user instanceof ServerPlayer player) {
         ServerLevel destination = selectedDestination(player);
         if (destination == null) {
            msg(player, "\u00a7ePick a destination first.");
            this.chooseDestinationDimension(sw, user);
         } else if (destination == sw) {
            msg(player, "\u00a7eYou are already in " + destination.dimension().location() + ". Choose another destination first.");
            this.chooseDestinationDimension(sw, user);
         } else {
            BlockPos arrival = findArrival(destination, 0);
            Vec3 from = player.position();
            Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 36, 1.0, 1.0, 1.0, 0.12);
            Fx.sound(sw, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 2.5F, 0.8F);
            if (player.changeDimension(directTransition(player, destination, arrival)) instanceof ServerPlayer arrived) {
               arrived.fallDistance = 0.0F;
               arrived.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 2));
               arrived.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
               Fx.burst(destination, ParticleTypes.PORTAL, arrived.getX(), arrived.getY() + 1.0, arrived.getZ(), 36, 1.0, 1.5, 1.0, 0.1);
               arrived.displayClientMessage(Component.literal("\u00a75Travelled to " + destination.dimension().location()), true);
            } else {
               msg(player, "\u00a7cDimension transfer was cancelled by the destination.");
            }
         }
      } else {
         msg(user, "\u00a7cDimension travel is only available to server players.");
      }
   }

   private void banishAreaToSelectedDimension(ServerLevel sw, Player user) {
      msg(user, "\u00a75\u00a7lBANISH AREA TO CHOSEN DIMENSION!");
      if (user instanceof ServerPlayer player) {
         ServerLevel destination = selectedDestination(player);
         if (destination != null && destination != sw) {
            LuckyBlockItem.Target target = pickTarget(sw, user, 100.0);
            Vec3 center = target.pos();
            double radius = 10.0;
            AABB zone = new AABB(center, center).inflate(10.0);
            List<Entity> targets = new ArrayList<>(
               sw.getEntities(
                  (Entity)null, zone, entityx -> entityx != user && !friend(entityx, user) && entityx.canChangeDimensions() && entityx.position().distanceToSqr(center) <= 100.0
               )
            );
            if (targets.isEmpty()) {
               msg(player, "\u00a7eNo transferable entities in the 10-block target area.");
               Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1.0, center.z, 20, 0.8, 1.0, 0.8, 0.08);
            } else {
               DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.95F), 1.8F);
               Fx.epicImpact(sw, center, violet, ParticleTypes.REVERSE_PORTAL, 8.0, 9323775);
               Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.6F);
               int transferred = 0;
               BlockPos firstArrival = findArrival(destination, 0);

               for (int index = 0; index < targets.size(); index++) {
                  Entity entity = targets.get(index);
                  if (!entity.isRemoved() && entity.canChangeDimensions()) {
                     BlockPos arrival = findArrival(destination, index);
                     Entity moved = entity.changeDimension(directTransition(entity, destination, arrival));
                     if (moved != null) {
                        if (++transferred <= 8) {
                           Fx.burst(destination, ParticleTypes.PORTAL, moved.getX(), moved.getY() + 0.5, moved.getZ(), 7, 0.25, 0.5, 0.25, 0.04);
                        }
                     }
                  }
               }

               Fx.epicImpact(
                  destination,
                  new Vec3((double)firstArrival.getX() + 0.5, (double)firstArrival.getY(), (double)firstArrival.getZ() + 0.5),
                  violet,
                  ParticleTypes.PORTAL,
                  6.0,
                  9323775
               );
               msg(player, "\u00a75Banished " + transferred + " entity/entities to " + destination.dimension().location() + ".");
            }
         } else {
            msg(
               player,
               destination == null
                  ? "\u00a7eChoose a destination dimension first."
                  : "\u00a7eChoose a different destination; these entities are already in that dimension."
            );
            this.chooseDestinationDimension(sw, user);
         }
      }
   }

   private void cosmicNova(ServerLevel sw, Player user) {
      msg(user, "\u00a7d\u00a7lCOSMIC SHOCKWAVE!");
      Vec3 center = user.position();

      for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(22.0), e -> !friend(e, user))) {
         entity.hurt(sw.damageSources().magic(), 70.0F);
         Vec3 away = entity.position().subtract(center);
         if (away.lengthSqr() > 0.01) {
            away = away.normalize();
            push(entity, away.x * 2.2, 1.1, away.z * 2.2);
         }
      }

      Fx.sound(sw, user.blockPosition(), SoundEvents.END_PORTAL_SPAWN, 3.2F, 0.7F);
      DustParticleOptions cosmic = new DustParticleOptions(new Vector3f(0.65F, 0.25F, 1.0F), 1.9F);
      Fx.epicImpact(sw, center, cosmic, ParticleTypes.PORTAL, 13.0, 11167487);

      for (int i = 0; i < 4; i++) {
         int pulse = i;
         Scheduler.later(i * 3 + 1, () -> {
            double radius = 4.0 + (double)pulse * 4.5;
            Fx.ring(sw, cosmic, center.x, center.y + 0.25, center.z, radius, 18);
            Fx.ring(sw, ParticleTypes.END_ROD, center.x, center.y + 0.55, center.z, radius * 0.82, 18);
         });
      }
   }

   private void itemWish(ServerLevel sw, Player user) {
      if (user instanceof ServerPlayer player) {
         msg(user, "\u00a76\u00a7lCHOOSE ANY REGISTERED ITEM \u2014 catalogue opened.");
         net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new OpenItemWishPacket());
         Fx.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, user.getX(), user.getY() + 1.0, user.getZ(), 24, 0.6, 0.8, 0.6, 0.12);
         Fx.sound(sw, user.blockPosition(), SoundEvents.PLAYER_LEVELUP, 1.6F, 1.3F);
      }
   }

   private void timeReplay(ServerLevel sw, Player user) {
      msg(user, "\u00a7d\u00a7lREPLAY RECENT POSITION + HEAL!");
      Deque<LuckyBlockItem.PlayerSnapshot> history = PLAYER_HISTORY.get(user.getUUID());
      LuckyBlockItem.PlayerSnapshot snapshot = null;
      if (history != null && history.size() >= 20) {
         LuckyBlockItem.PlayerSnapshot[] snapshots = history.toArray(new LuckyBlockItem.PlayerSnapshot[0]);
         int targetIndex = Math.max(0, snapshots.length - 61);
         snapshot = snapshots[targetIndex];
      }

      Vec3 from = user.position();
      if (snapshot != null && snapshot.dimension().equals(sw.dimension()) && user instanceof ServerPlayer player) {
         Vec3 old = snapshot.position();
         player.teleportTo(sw, old.x, old.y, old.z, snapshot.yaw(), snapshot.pitch());
         user.setHealth(Math.min(user.getMaxHealth(), Math.max(user.getHealth(), snapshot.health())));
         user.fallDistance = 0.0F;
         user.removeEffect(MobEffects.WITHER);
         user.removeEffect(MobEffects.POISON);
         user.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
         user.removeEffect(MobEffects.CONFUSION);
         user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 2));
         Fx.line(sw, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, old.x, old.y + 1.0, old.z, 20);
         Fx.burst(sw, ParticleTypes.END_ROD, old.x, old.y + 1.0, old.z, 30, 0.6, 1.0, 0.6, 0.08);
         msg(user, "\u00a7dReplayed a recent position and recovered lost health.");
      } else {
         user.setHealth(user.getMaxHealth());
         user.removeEffect(MobEffects.WITHER);
         user.removeEffect(MobEffects.POISON);
         user.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
         user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
         Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 24, 0.8, 1.0, 0.8, 0.08);
         msg(user, "\u00a7dNo recent snapshot was available; health and harmful effects were restored.");
      }

      Fx.sound(sw, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.4F, 0.6F);
      Fx.screen(sw, user.position(), 64.0, 0.25F, 0.45F, 8, 14518527);
   }

   private void spatialDeflection(ServerLevel sw, Player user) {
      msg(user, "\u00a75\u00a7lSKY TESSERACT ERASURE!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 100.0);
      Vec3 center = target.pos();
      Vec3 skyCenter = center.add(0.0, 22.0, 0.0);
      DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0F, 0.88F, 0.16F), 1.7F);
      DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.45F, 0.14F, 0.9F), 1.45F);
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.END_PORTAL_SPAWN, 4.0F, 0.42F);
      Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.WARDEN_SONIC_BOOM, 3.4F, 0.5F);
      Fx.screen(sw, center, 140.0, 0.25F, 0.9F, 12, 11897599);
      Fx.ring(sw, gold, center.x, center.y + 0.2, center.z, 18.0, 18);
      Fx.ring(sw, violet, center.x, center.y + 0.35, center.z, 24.0, 18);
      Fx.line(
         sw,
         ParticleTypes.REVERSE_PORTAL,
         center.x - 18.0,
         center.y + 0.5,
         center.z,
         center.x + 18.0,
         center.y + 0.5,
         center.z,
         18
      );
      Fx.line(
         sw,
         ParticleTypes.REVERSE_PORTAL,
         center.x,
         center.y + 0.5,
         center.z - 18.0,
         center.x,
         center.y + 0.5,
         center.z + 18.0,
         18
      );
      Fx.laserMark(sw, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 40.0, 0.0);
      HyperFx hx2 = HyperFx.spawn(sw, skyCenter.x, skyCenter.y, skyCenter.z, "minecraft:white_concrete", "minecraft:purple_concrete", "minecraft:gold_block", "minecraft:sea_lantern");
      Scheduler.later(60, hx2::remove);
      AABB captureZone = new AABB(center, center).inflate(22.0);

      for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, captureZone, e -> !friend(e, user) && e.distanceToSqr(center) <= 484.0)) {
         entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
         entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 45, 1, false, false));
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 4, false, false));
      }

      for (int tick = 0; tick <= 42; tick += 2) {
         int elapsed = tick;
         Scheduler.later(
            tick + 1,
            () -> {
               double progress = (double)elapsed / 42.0;
               double size = 21.5 - progress * 17.5;
               hx2.update(size, (double)elapsed * 0.055, 3);
               Fx.rune(sw, violet, gold, center.x, center.y + 0.2, center.z, 22.0 - progress * 10.0, -(double)elapsed * 0.1);
               Fx.sevenStars(sw, ParticleTypes.END_ROD, gold, skyCenter.x, skyCenter.y - 6.0, skyCenter.z, 16.0 - progress * 8.0, (double)elapsed * 0.08);
               Fx.laserMark(sw, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 40.0, (double)elapsed * 0.2);
               Fx.helix(sw, ParticleTypes.END_ROD, ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 5.0 + (1.0 - progress) * 8.0, 22.0, (double)elapsed * 0.3);
               if (elapsed % 6 == 0) {
                  for (int c = 0; c < 6; c++) {
                     double ca = (double)elapsed * 0.08 + c * Math.PI / 3.0;
                     Fx.line(sw, c % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                        center.x + Math.cos(ca) * 16.0, center.y + 0.5, center.z + Math.sin(ca) * 16.0,
                        skyCenter.x, skyCenter.y, skyCenter.z, 6);
                  }
                  Fx.burst(sw, ParticleTypes.REVERSE_PORTAL, skyCenter.x, skyCenter.y, skyCenter.z, 9, 0.35, 0.65, 0.35, 0.14);
                  AABB liftZone = new AABB(center, center).inflate(22.0);

                  for (LivingEntity entity : sw.getEntitiesOfClass(LivingEntity.class, liftZone, e -> !friend(e, user) && e.isAlive() && e.distanceToSqr(center) <= 484.0)) {
                     Vec3 toFrame = skyCenter.subtract(entity.position().add(0.0, (double)entity.getBbHeight() * 0.5, 0.0));
                     double distance = toFrame.length();
                     if (distance > 0.01) {
                        Vec3 direction = toFrame.scale(1.0 / distance);
                        push(entity, direction.x * 0.7, Math.max(0.35, direction.y * 1.25), direction.z * 0.7);
                     }
                  }

                  for (Projectile projectile : sw.getEntitiesOfClass(Projectile.class, liftZone, p -> p.getOwner() != user && !p.getTags().contains("lbm_ally"))) {
                     Vec3 toFrame = skyCenter.subtract(projectile.position());
                     if (toFrame.lengthSqr() > 0.01) {
                        Vec3 direction = toFrame.normalize();
                        launch(projectile, direction.x * 1.8, direction.y * 1.8, direction.z * 1.8);
                     }
                  }
               }
            }
         );
      }

      Scheduler.later(46, () -> {
         hx2.update(26.0, 2.0, 4);
         Scheduler.later(5, hx2::remove);
         AABB finalZone = new AABB(center, center).inflate(22.0);

         for (Projectile projectile : sw.getEntitiesOfClass(Projectile.class, finalZone, p -> p.getOwner() != user && !p.getTags().contains("lbm_ally"))) {
            projectile.discard();
         }

         for (Entity entity : sw.getEntities((Entity)null, finalZone, e -> e != user && !friend(e, user) && e.position().distanceToSqr(center) <= 484.0)) {
            if (entity instanceof Player player) {
               player.hurt(sw.damageSources().magic(), 150.0F);
            } else {
               entity.discard();
            }
         }

         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.GENERIC_EXPLODE, 4.5F, 0.42F);
         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.END_PORTAL_SPAWN, 4.5F, 0.35F);
         Fx.epicImpact(sw, center.add(0.0, 2.0, 0.0), violet, ParticleTypes.REVERSE_PORTAL, 17.0, 11897599);
         for (int beamTick = 0; beamTick < 10; beamTick++) {
            int bt = beamTick;
            Scheduler.later(bt * 2, () -> Fx.beamColumn(sw, ParticleTypes.END_ROD, gold, center.x, center.y, center.z, 45.0, 3.0 + bt * 0.6, (double)bt * 0.5));
         }
         Fx.shatter(sw, ParticleTypes.END_ROD, violet, ParticleTypes.ELECTRIC_SPARK, gold, skyCenter.x, skyCenter.y, skyCenter.z, 6.0, 2.0);
         Fx.burst(sw, ParticleTypes.FIREWORK, skyCenter.x, skyCenter.y, skyCenter.z, 24, 6.0, 4.0, 6.0, 0.5);
         Fx.sound(sw, BlockPos.containing(center.x, center.y, center.z), SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.5F);
         Fx.burst(sw, ParticleTypes.POOF, skyCenter.x, skyCenter.y, skyCenter.z, 24, 2.4, 2.4, 2.4, 0.18);
         Fx.burst(sw, ParticleTypes.PORTAL, skyCenter.x, skyCenter.y, skyCenter.z, 24, 2.8, 2.8, 2.8, 0.26);

         for (int pulse = 0; pulse < 4; pulse++) {
            int index = pulse;
            Scheduler.later(pulse * 2 + 1, () -> {
               double radius = 5.0 + (double)index * 4.2;
               Fx.ring(sw, gold, center.x, center.y + 0.2, center.z, radius, 18);
               Fx.ring(sw, ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.5, center.z, radius * 0.72, 18);
            });
         }
      });
   }

   private void absoluteAegis(ServerLevel sw, Player user) {
      msg(user, "\u00a76\u00a7l60-SECOND DAMAGE SHIELD!");
      activateShield(user, 1200);
      user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 9));
      user.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 8));
      user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 4));
      user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
      Fx.sound(sw, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, 3.0F, 1.7F);
      Fx.screen(sw, user.position(), 80.0, 0.45F, 0.6F, 10, 16776960);

      for (int i = 0; i < 5; i++) {
         int layer = i;
         Scheduler.later(
            i * 3 + 1, () -> Fx.ring(sw, YELLOW, user.getX(), user.getY() + 0.25 + (double)layer * 0.3, user.getZ(), 2.0 + (double)layer * 0.9, 24)
         );
      }
   }

   private void thunderGod(ServerLevel sw, Player user) {
      msg(user, "\u00a7b\u00a7lCHAIN LIGHTNING \u2014 UP TO 7 TARGETS!");
      LuckyBlockItem.Target target = pickTarget(sw, user, 100.0);
      LivingEntity first = target.entity();
      if (first != null && first.isAlive() && !friend(first, user)) {
         List<LivingEntity> chain = new ArrayList<>();
         chain.add(first);

         while (chain.size() < 7) {
            LivingEntity last = chain.get(chain.size() - 1);
            LivingEntity next = null;
            double bestDistance = 196.0;

            for (LivingEntity candidate : sw.getEntitiesOfClass(
               LivingEntity.class, new AABB(last.blockPosition()).inflate(14.0), e -> !friend(e, user) && !chain.contains(e) && e.isAlive()
            )) {
               double distance = candidate.distanceToSqr(last);
               if (distance < bestDistance) {
                  bestDistance = distance;
                  next = candidate;
               }
            }

            if (next == null) {
               break;
            }

            chain.add(next);
         }

         for (int i = 0; i < chain.size(); i++) {
            int index = i;
            Scheduler.later(
               i * 3 + 1,
               () -> {
                  LivingEntity entity = chain.get(index);
                  if (entity.isAlive()) {
                     LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(sw);
                     if (bolt != null) {
                        bolt.moveTo(entity.getX(), entity.getY(), entity.getZ());
                        bolt.setVisualOnly(true);
                        sw.addFreshEntity(bolt);
                     }

                     entity.hurt(sw.damageSources().indirectMagic(user, user), 52.0F);
                     Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + 1.0, entity.getZ(), 18, 0.4, 0.8, 0.4, 0.15);
                     if (index > 0) {
                        LivingEntity previous = chain.get(index - 1);
                        Fx.line(
                           sw,
                           ParticleTypes.ELECTRIC_SPARK,
                           previous.getX(),
                           previous.getY() + 1.0,
                           previous.getZ(),
                           entity.getX(),
                           entity.getY() + 1.0,
                           entity.getZ(),
                           8
                        );
                     }

                     Fx.sound(sw, entity.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F + (float)index * 0.08F);
                  }
               }
            );
         }

         Fx.screen(sw, first.position(), 100.0, 0.35F, 1.0F, 10, 6737151);
      } else {
         Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, target.pos().x, target.pos().y + 1.0, target.pos().z, 28, 0.6, 1.0, 0.6, 0.2);
         Fx.sound(sw, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.8F);
      }
   }

   private static record Beam(Vec3 eye, Vec3 dir, double len, Vec3 impact) {
   }

   private static record PlayerSnapshot(Vec3 position, float health, float yaw, float pitch, ResourceKey<Level> dimension) {
   }

   private static record Target(Vec3 pos, LivingEntity entity) {
   }

   @Override
   public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
      return true;
   }
}
