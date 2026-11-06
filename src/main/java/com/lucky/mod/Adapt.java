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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

/** ADAPTATION: after the cutscene you become immune to every damage type and harmful effect once it has hurt you. */
@EventBusSubscriber(modid = "luckyblockmod")
public final class Adapt {
   private static final String ROOT = "lbm_adapt";
   private static final java.util.Set<java.util.UUID> PENDING = new java.util.HashSet<>();

   private Adapt() {
   }

   private static CompoundTag data(Player p) {
      CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      CompoundTag d = persisted.getCompound(ROOT);
      persisted.put(ROOT, d);
      p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
      return d;
   }

   public static void grant(Player p) {
      data(p).putBoolean("on", true);
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
   public static void onAttack(LivingAttackEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp && active(sp) && "genericKill".equals(event.getSource().getMsgId())) {
         // /kill: refuse it, make a scene, and adapt to it for good.
         event.setCanceled(true);
         if (!adapted(sp, "dmg:genericKill") && PENDING.add(sp.getUUID())) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4, 50, 12));
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("\u00a7c\u00a7lNot on my watch.")));
            LuckyBlockMod.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new CutscenePacket(1));
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
         String key = "dmg:" + event.getSource().getMsgId();
         boolean bypass = event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY);
         if (bypass && !adapted(p, key)) {
            // void, /damage with bypass, etc: adapt instantly (the first hit is already refused)
            learn(p, key, event.getSource().getMsgId().replace('_', ' ').toUpperCase());
         }
         if (adapted(p, key)) {
            event.setCanceled(true);
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && p instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sw) {
               int y = sw.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, sp.getBlockX(), sp.getBlockZ());
               sp.teleportTo(sw, sp.getX(), y + 1.0, sp.getZ(), sp.getYRot(), sp.getXRot());
               sp.fallDistance = 0.0F;
               sp.setDeltaMovement(0.0, 0.0, 0.0);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onDamaged(LivingDamageEvent event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p) && event.getAmount() > 0.0F) {
         String id = event.getSource().getMsgId();
         if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !adapted(p, "dmg:" + id)) {
            learn(p, "dmg:" + id, id.replace('_', ' ').toUpperCase());
         }
      }
   }

   @SubscribeEvent
   public static void onEffectApplicable(MobEffectEvent.Applicable event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)
         && event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.BENEFICIAL
         && adapted(p, "fx:" + event.getEffectInstance().getEffect().getDescriptionId())) {
         event.setResult(Event.Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onEffectAdded(MobEffectEvent.Added event) {
      if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)
         && event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.BENEFICIAL) {
         String key = "fx:" + event.getEffectInstance().getEffect().getDescriptionId();
         if (!adapted(p, key)) {
            learn(p, key, event.getEffectInstance().getEffect().getDisplayName().getString().toUpperCase());
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
}
