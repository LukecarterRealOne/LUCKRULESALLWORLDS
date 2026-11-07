package com.lucky.mod;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3f;

/** ADAPTATION: after the cutscene you become immune to every damage type and harmful effect once it has hurt you. */
@EventBusSubscriber(modid = "luckyblockmod")
public final class Adapt {
   private static final String ROOT = "lbm_adapt";
   private static final java.util.Set<java.util.UUID> PENDING = new java.util.HashSet<>();

   /** Commands that could strip the block / the adaptation or cripple the player. */
   private static final java.util.Set<String> GUARDED = java.util.Set.of("clear", "item", "loot", "attribute", "replaceitem");

   private Adapt() {
   }

   public static CompoundTag data(Player p) {
      CompoundTag persisted = p.getPersistentData().getCompound("lbm_persist");
      CompoundTag d = persisted.getCompound(ROOT);
      persisted.put(ROOT, d);
      p.getPersistentData().put("lbm_persist", persisted);
      return d;
   }

   public static void grant(Player p) {
      data(p).putBoolean("on", true);
   }

   public static java.util.List<String> names(Player p) {
      ListTag l = data(p).getList("names", 8);
      java.util.List<String> out = new java.util.ArrayList<>();
      for (int i = 0; i < l.size(); i++) {
         out.add(l.getString(i));
      }
      return out;
   }

   public static boolean has(Player p) {
      return data(p).getBoolean("on");
   }

   /** Adaptation only works while the Lucky Block is in a hand. */
   public static boolean active(Player p) {
      return has(p) && (p.getMainHandItem().getItem() instanceof LuckyBlockItem || p.getOffhandItem().getItem() instanceof LuckyBlockItem);
   }

   private static boolean adapted(Player p, String key) {
      ListTag l = data(p).getList("keys", 8);
      for (int i = 0; i < l.size(); i++) {
         if (l.getString(i).equals(key)) {
            return true;
         }
      }
      return false;
   }

   private static void learn(Player p, String key, String pretty) {
      CompoundTag d = data(p);
      ListTag l = d.getList("keys", 8);
      l.add(StringTag.valueOf(key));
      d.put("keys", l);
      ListTag nl = d.getList("names", 8);
      nl.add(StringTag.valueOf(pretty.length() > 60 ? pretty.substring(0, 60) : pretty));
      d.put("names", nl);
      LuckMeter.add(p, 250);
      if (p instanceof net.minecraft.server.level.ServerPlayer sp0) {
         LuckMeter.sync(sp0);
      }
      p.displayClientMessage(Component.literal("§e§lADAPTED §r§6to " + pretty), true);
      if (p.level() instanceof ServerLevel sw) {
         chime(sw, p);
      }
   }

   /** The ascension sound: a rising run of chimes ending in a bright toast + a gold helix. */
   public static void chime(ServerLevel sw, Player p) {
      float[] notes = new float[]{0.7F, 0.9F, 1.1F, 1.35F, 1.65F, 2.0F};
      for (int i = 0; i < notes.length; i++) {
         float pitch = notes[i];
         Scheduler.later(i * 2, () -> {
            Fx.sound(sw, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, pitch);
            Fx.sound(sw, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, 0.7F, pitch);
         });
      }
      Scheduler.later(13, () -> Fx.sound(sw, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 1.3F));
      DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.2F);
      for (int t = 0; t < 12; t++) {
         int tick = t;
         Scheduler.later(t, () -> Fx.helix(sw, gold, net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + tick * 0.25, p.getZ(), 1.0, 2.4, tick * 0.5));
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttack(LivingIncomingDamageEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp && active(sp) && "genericKill".equals(event.getSource().getMsgId())) {
         // /kill: refuse it, make a scene, and adapt to it for good.
         event.setCanceled(true);
         if (!adapted(sp, "dmg:genericKill") && PENDING.add(sp.getUUID())) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4, 50, 12));
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("\u00a7c\u00a7lNot on my watch.")));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new CutscenePacket(1));
            if (sp.level() instanceof ServerLevel sw) {
               Fx.sound(sw, sp.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.4F);
               Fx.sound(sw, sp.blockPosition(), SoundEvents.ANVIL_LAND, 1.5F, 0.6F);
            }
            Scheduler.later(45, () -> {
               PENDING.remove(sp.getUUID());
               if (!sp.isRemoved() && !adapted(sp, "dmg:genericKill")) {
                  learn(sp, "dmg:genericKill", "/KILL");
               }
            });
         }
         return;
      }
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)) {
         // EVERY damage source (incl. /damage with any amount or type) is refused; the first of each type is learned on the spot.
         String key = "dmg:" + event.getSource().getMsgId();
         event.setCanceled(true);
         if (!adapted(p, key)) {
            learn(p, key, event.getSource().getMsgId().replace('_', ' ').toUpperCase());
         }
         if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && p instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sw) {
            int y = sw.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, sp.getBlockX(), sp.getBlockZ());
            sp.teleportTo(sw, sp.getX(), y + 1.0, sp.getZ(), sp.getYRot(), sp.getXRot());
            sp.fallDistance = 0.0F;
            sp.setDeltaMovement(0.0, 0.0, 0.0);
         }
      }
   }

   /** Safety net: whatever slips through (health set directly, odd damage paths) can never actually kill an adapted player. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)) {
         event.setCanceled(true);
         p.setHealth(p.getMaxHealth());
         p.deathTime = 0;
      }
   }

   @SubscribeEvent
   public static void onAdaptTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
      Player p = event.getEntity();
      if (!p.level().isClientSide && active(p) && p.isAlive() && p.getHealth() < 1.0F) {
         p.setHealth(p.getMaxHealth());
      }
   }

   @SubscribeEvent
   public static void onDamaged(LivingDamageEvent.Post event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p) && event.getNewDamage() > 0.0F) {
         String id = event.getSource().getMsgId();
         if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !adapted(p, "dmg:" + id)) {
            learn(p, "dmg:" + id, id.replace('_', ' ').toUpperCase());
         }
      }
   }

   @SubscribeEvent
   public static void onEffectApplicable(MobEffectEvent.Applicable event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)
         && event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
         String key = "fx:" + event.getEffectInstance().getEffect().value().getDescriptionId();
         if (!adapted(p, key)) {
            learn(p, key, event.getEffectInstance().getEffect().value().getDisplayName().getString().toUpperCase());
         }
         event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
      }
   }

   @SubscribeEvent
   public static void onEffectAdded(MobEffectEvent.Added event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)
         && event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
         String key = "fx:" + event.getEffectInstance().getEffect().value().getDescriptionId();
         if (!adapted(p, key)) {
            learn(p, key, event.getEffectInstance().getEffect().value().getDisplayName().getString().toUpperCase());
         }
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp && sp.getTags().contains(Cutscene.TAG)) {
         Cutscene.clear(sp);
         sp.setHealth(sp.getMaxHealth());
      }
   }

   /** /clear aimed at an adapted player (block in hand): refused with a taunt. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onCommand(net.neoforged.neoforge.event.CommandEvent event) {
      com.mojang.brigadier.context.CommandContextBuilder<net.minecraft.commands.CommandSourceStack> ctx = event.getParseResults().getContext();
      net.minecraft.commands.CommandSourceStack src = ctx.getSource();
      java.util.LinkedHashSet<ServerPlayer> targets = new java.util.LinkedHashSet<>();
      String hit = null;
      boolean hasSelector = false;
      com.mojang.brigadier.context.CommandContextBuilder<net.minecraft.commands.CommandSourceStack> b = ctx;
      while (b != null) { // walks /execute ... run clear chains too
         if (!b.getNodes().isEmpty() && GUARDED.contains(b.getNodes().get(0).getNode().getName())) {
            hit = b.getNodes().get(0).getNode().getName();
         }
         for (java.util.Map.Entry<String, com.mojang.brigadier.context.ParsedArgument<net.minecraft.commands.CommandSourceStack, ?>> en : b.getArguments().entrySet()) {
            if (en.getValue().getResult() instanceof net.minecraft.commands.arguments.selector.EntitySelector es) {
               hasSelector = true;
               try {
                  targets.addAll(es.findPlayers(src));
               } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
               }
            }
         }
         b = b.getChild();
      }
      if (hit == null) {
         return;
      }
      if (!hasSelector && src.getEntity() instanceof ServerPlayer self) {
         targets.add(self);
      }
      boolean blocked = false;
      for (ServerPlayer t : targets) {
         if (active(t)) {
            blocked = true;
            t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4, 40, 10));
            t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("\u00a76\u00a7lNot bad, but try again.")));
            if (!adapted(t, "cmd:" + hit)) {
               learn(t, "cmd:" + hit, "/" + hit.toUpperCase());
            }
         }
      }
      if (blocked) {
         event.setCanceled(true);
         src.sendFailure(Component.literal("Not bad, but try again."));
      }
   }
}
