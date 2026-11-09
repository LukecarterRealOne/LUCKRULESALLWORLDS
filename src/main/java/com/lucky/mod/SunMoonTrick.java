package com.lucky.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Side troll: look at the sun (day) or the moon (night), say a message containing a word that starts with "sun" or "moon"
 * (e.g. "cool looking sun right?") while holding the Lucky Block, and:
 *  1. the block leaves your hand as a spinning 3D cube that floats toward the sky and pops,
 *  2. it stays gone for a few seconds, then comes back to normal,
 *  3. a big "?" shows up over the sun / moon for about 30 seconds.
 * Pure visual gag. The block is always given back (also if you log out or die while it is away).
 */
@EventBusSubscriber(modid = "luckyblockmod")
public final class SunMoonTrick {
   private static final Pattern WORD = Pattern.compile("\\b(sun|moon)");
   private static final Map<UUID, Pending> PENDING = new HashMap<>();
   private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

   private record Pending(ItemStack stack, InteractionHand hand) {
   }

   private SunMoonTrick() {
   }

   @SubscribeEvent
   public static void onChat(ServerChatEvent event) {
      ServerPlayer player = event.getPlayer();
      if (player == null) {
         return;
      }
      java.util.regex.Matcher m = WORD.matcher(event.getRawText().toLowerCase(Locale.ROOT));
      if (!m.find()) {
         return;
      }
      boolean wantSun = m.group(1).equals("sun");
      // chat may arrive off the main thread: do the work on the server thread
      player.getServer().execute(() -> tryTrigger(player, wantSun));
   }

   /** Direction from the world to the sun (moon = negated). */
   private static Vec3 sunDir(ServerLevel sw) {
      double a = sw.getTimeOfDay(1.0F) * Math.PI * 2.0;
      return new Vec3(-Math.sin(a), Math.cos(a), 0.0);
   }

   private static void tryTrigger(ServerPlayer player, boolean wantSun) {
      if (!(player.level() instanceof ServerLevel sw) || sw.dimension() != Level.OVERWORLD || PENDING.containsKey(player.getUUID())) {
         return;
      }
      long now = sw.getGameTime();
      if (COOLDOWN.getOrDefault(player.getUUID(), -9999L) + 400L > now) {
         return;
      }
      InteractionHand hand = player.getMainHandItem().getItem() instanceof LuckyBlockItem ? InteractionHand.MAIN_HAND
         : player.getOffhandItem().getItem() instanceof LuckyBlockItem ? InteractionHand.OFF_HAND : null;
      if (hand == null) {
         return;
      }
      Vec3 dir = wantSun ? sunDir(sw) : sunDir(sw).scale(-1.0);
      if (dir.y < 0.0 || player.getLookAngle().dot(dir) < 0.94) {
         return; // not looking at it (or it is below the horizon)
      }
      COOLDOWN.put(player.getUUID(), now);
      run(sw, player, hand, dir, wantSun);
   }

   private static void run(ServerLevel sw, ServerPlayer player, InteractionHand hand, Vec3 dir, boolean sun) {
      ItemStack held = player.getItemInHand(hand);
      PENDING.put(player.getUUID(), new Pending(held.copy(), hand));
      player.setItemInHand(hand, ItemStack.EMPTY);

      Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.7)).add(0.0, -0.5, 0.0);
      Entity cube = spawn(sw, "{id:\"minecraft:block_display\",Pos:[" + start.x + "," + start.y + "," + start.z
         + "],block_state:{Name:\"minecraft:gold_block\"},brightness:{sky:15,block:15},view_range:2.0f,width:8f,height:8f,Tags:[\"lbm_fx\"],"
         + transform(0.0, 0.5, new Vec3(0, 0, 0)) + ",start_interpolation:0,interpolation_duration:4}");
      Fx.sound(sw, player.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, 1.2F, 1.8F);
      Fx.burst(sw, ParticleTypes.ENCHANT, start.x, start.y, start.z, 20, 0.3, 0.3, 0.3, 0.3);

      // t=0..40: the cube spins faster and floats out toward the sky
      for (int t = 4; t <= 40; t += 4) {
         int tick = t;
         Scheduler.later(t, () -> {
            if (cube != null && !cube.isRemoved()) {
               double angle = tick * tick * 0.012;
               Vec3 drift = dir.scale(tick * 0.08).add(0.0, tick * 0.03, 0.0);
               merge(cube, transform(angle, 0.5 + tick * 0.005, drift) + ",start_interpolation:0,interpolation_duration:4");
               Fx.burst(sw, ParticleTypes.END_ROD, cube.getX() + drift.x, cube.getY() + drift.y + 0.3, cube.getZ() + drift.z, 3, 0.15, 0.15, 0.15, 0.02);
            }
         });
      }
      Scheduler.later(44, () -> {
         if (cube != null) {
            Fx.burst(sw, ParticleTypes.FIREWORK, cube.getX() + dir.x * 3.5, cube.getY() + dir.y * 3.5 + 1.0, cube.getZ() + dir.z * 3.5, 30, 0.4, 0.4, 0.4, 0.2);
            cube.discard();
         }
         Fx.sound(sw, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.2F, 1.2F);
         placeQuestionMark(sw, player, dir);
      });

      // t=100: it comes back to normal
      Scheduler.later(100, () -> giveBack(player, true, sun));
   }

   private static void placeQuestionMark(ServerLevel sw, ServerPlayer player, Vec3 dir) {
      Vec3 p = player.getEyePosition().add(dir.scale(70.0));
      Entity mark = spawn(sw, "{id:\"minecraft:text_display\",Pos:[" + p.x + "," + p.y + "," + p.z + "],text:'{\\"text\\":\\"?\\",\\"color\\":\\"light_purple\\",\\"bold\\":true}',billboard:\"center\",background:0,"
         + "brightness:{sky:15,block:15},view_range:3.0f,width:80f,height:80f,line_width:200,Tags:[\"lbm_fx\"],"
         + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[45f,45f,45f],translation:[0f,0f,0f]}}");
      if (mark != null) {
         Scheduler.later(600, mark::discard);
      }
   }

   /** Returns the block (also used on logout / death so it can never be lost). */
   private static void giveBack(ServerPlayer player, boolean fx, boolean sun) {
      Pending pending = PENDING.remove(player.getUUID());
      if (pending == null) {
         return;
      }
      if (player.getItemInHand(pending.hand()).isEmpty()) {
         player.setItemInHand(pending.hand(), pending.stack());
      } else {
         player.getInventory().placeItemBackInInventory(pending.stack());
      }
      if (fx && player.level() instanceof ServerLevel sw) {
         Fx.sound(sw, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
         Fx.burst(sw, ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
         player.displayClientMessage(Component.literal(sun ? "\u00a76The sun blinks back at you." : "\u00a7bThe moon blinks back at you."), true);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         giveBack(sp, false, true);
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         giveBack(sp, false, true);
      }
   }

   // ---------------------------------------------------------------- display helpers

   private static String f(double v) {
      return String.format(Locale.ROOT, "%.4ff", v);
   }

   /** Block display transform: spin around a tilted axis by angle, uniform size s, centered, plus a drift offset. */
   private static String transform(double angle, double s, Vec3 drift) {
      double ax = 0.45, ay = 0.8, az = 0.35;
      double n = Math.sqrt(ax * ax + ay * ay + az * az);
      ax /= n;
      ay /= n;
      az /= n;
      double sin = Math.sin(angle / 2.0);
      double qx = ax * sin, qy = ay * sin, qz = az * sin, qw = Math.cos(angle / 2.0);
      // rotate the half-size offset so the cube spins around its own center
      double hx = s / 2.0, hy = s / 2.0, hz = s / 2.0;
      double tx = 2 * (qy * hz - qz * hy);
      double ty = 2 * (qz * hx - qx * hz);
      double tz = 2 * (qx * hy - qy * hx);
      double rx = hx + qw * tx + (qy * tz - qz * ty);
      double ry = hy + qw * ty + (qz * tx - qx * tz);
      double rz = hz + qw * tz + (qx * ty - qy * tx);
      return "transformation:{left_rotation:[" + f(qx) + "," + f(qy) + "," + f(qz) + "," + f(qw) + "],right_rotation:[0f,0f,0f,1f],scale:["
         + f(s) + "," + f(s) + "," + f(s) + "],translation:[" + f(drift.x - rx) + "," + f(drift.y - ry) + "," + f(drift.z - rz) + "]}";
   }

   private static Entity spawn(ServerLevel sw, String snbt) {
      try {
         CompoundTag tag = TagParser.parseTag(snbt);
         String id = tag.getString("id");
         EntityType<?> type = id.endsWith("text_display") ? EntityType.TEXT_DISPLAY : EntityType.BLOCK_DISPLAY;
         Entity e = type.create(sw);
         if (e == null) {
            return null;
         }
         e.load(tag);
         net.minecraft.nbt.ListTag pos = tag.getList("Pos", 6);
         e.setPos(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
         sw.addFreshEntity(e);
         return e;
      } catch (CommandSyntaxException | RuntimeException ex) {
         return null;
      }
   }

   private static void merge(Entity e, String body) {
      try {
         CompoundTag cur = e.saveWithoutId(new CompoundTag());
         cur.merge(TagParser.parseTag("{" + body + "}"));
         e.load(cur);
      } catch (CommandSyntaxException | RuntimeException ex) {
         // visual only
      }
   }
}
