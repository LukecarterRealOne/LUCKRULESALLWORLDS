package com.lucky.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

/**
 * THE LUCK METER: 0..50,000 stacks, saved with the player (survives death). The higher it is, the stronger and luckier you get, everywhere:
 * vanilla Luck attribute (loot tables, fishing...), max health, ALL damage you deal (melee, abilities, your allies) up to x50,
 * and ability cooldowns shrink up to 90%.
 */
@EventBusSubscriber(modid = "luckyblockmod")
public final class LuckMeter {
   public static final int MAX = 50000;
   private static final UUID LUCK_ID = UUID.fromString("5b0c2a52-1d3e-4f6a-9a11-4c1d2b7e0001");
   private static final UUID HP_ID = UUID.fromString("5b0c2a52-1d3e-4f6a-9a11-4c1d2b7e0002");
   private static final Map<UUID, Long> LAST_CAST = new HashMap<>();
   private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

   private LuckMeter() {
   }

   public static int get(Player p) {
      return Math.max(0, Math.min(MAX, Adapt.data(p).getInt("luck")));
   }

   public static void set(Player p, int v) {
      Adapt.data(p).putInt("luck", Math.max(0, Math.min(MAX, v)));
   }

   public static void add(Player p, int v) {
      set(p, get(p) + v);
   }

   public static float frac(Player p) {
      return get(p) / (float)MAX;
   }

   /** Damage multiplier 1..50. */
   public static float mult(Player p) {
      return 1.0F + frac(p) * 49.0F;
   }

   public static int cooldown(Player p, int base) {
      return Math.max(2, Math.round(base * (1.0F - 0.9F * frac(p))));
   }

   public static boolean carrying(Player p) {
      return p.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof LuckyBlockItem) || p.getOffhandItem().getItem() instanceof LuckyBlockItem;
   }

   public static void onCast(Player p) {
      LAST_CAST.put(p.getUUID(), p.level().getGameTime());
      add(p, 100);
   }

   @SubscribeEvent
   public static void onTick(TickEvent.PlayerTickEvent e) {
      Player p = e.player;
      if (e.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % 20 != 0) {
         return;
      }
      boolean has = Adapt.has(p) || get(p) > 0;
      boolean carry = carrying(p);
      if (carry && p.tickCount % 40 == 0) {
         add(p, 2);
      }
      float f = carry ? frac(p) : 0.0F;
      apply(p, Attributes.LUCK, LUCK_ID, "Lucky Block luck", f * 100.0F);
      apply(p, Attributes.MAX_HEALTH, HP_ID, "Lucky Block health", f * 100.0F);
      if (has && p instanceof ServerPlayer sp) {
         int sig = get(p) * 31 + Adapt.names(p).size();
         Integer last = LAST_SENT.get(p.getUUID());
         if (last == null || last != sig) {
            LAST_SENT.put(p.getUUID(), sig);
            sync(sp);
         }
      }
   }

   private static void apply(Player p, Attribute attr, UUID id, String name, double amount) {
      AttributeInstance inst = p.getAttribute(attr);
      if (inst == null) {
         return;
      }
      AttributeModifier cur = inst.getModifier(id);
      if (amount <= 0.0) {
         if (cur != null) {
            inst.removeModifier(id);
         }
      } else if (cur == null || Math.abs(cur.getAmount() - amount) > 0.01) {
         if (cur != null) {
            inst.removeModifier(id);
         }
         inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
      }
   }

   public static void sync(ServerPlayer sp) {
      LuckyBlockMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new MeterPacket(get(sp), Adapt.names(sp)));
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
      LAST_SENT.remove(e.getEntity().getUUID());
      if (e.getEntity() instanceof ServerPlayer sp) {
         Failsafe.guard(sp);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
      LAST_SENT.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onDamage(LivingHurtEvent e) {
      LivingEntity v = e.getEntity();
      if (v.level().isClientSide || v instanceof Player || v.getTags().contains("lbm_ally")) {
         return;
      }
      Player src = null;
      Entity att = e.getSource().getEntity();
      if (att instanceof Player p && carrying(p)) {
         src = p;
      } else if (att != null && att.getTags().contains("lbm_ally")) {
         for (String t : att.getTags()) {
            if (t.startsWith("lbm_owner_")) {
               try {
                  Player o = v.level().getPlayerByUUID(UUID.fromString(t.substring(10)));
                  if (o != null && carrying(o)) {
                     src = o;
                  }
               } catch (IllegalArgumentException ignored) {
               }
            }
         }
      } else if (att == null || att instanceof Player) {
         // ability damage (magic, lightning, explosions...) carries no attacker: credit the caster who cast recently nearby
         for (Player p : v.level().players()) {
            long last = LAST_CAST.getOrDefault(p.getUUID(), -99999L);
            if (carrying(p) && v.level().getGameTime() - last < 900L && p.distanceToSqr(v) < 22500.0) {
               src = p;
               break;
            }
         }
      }
      if (src != null) {
         float m = mult(src);
         if (m > 1.0F) {
            e.setAmount(e.getAmount() * m);
         }
      }
   }

   @SubscribeEvent
   public static void onKill(LivingDeathEvent e) {
      if (!e.getEntity().level().isClientSide && !(e.getEntity() instanceof Player) && e.getSource().getEntity() instanceof Player p && carrying(p)) {
         add(p, 5);
      }
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent e) {
      e.getDispatcher().register(
         Commands.literal("luckmeter").requires(s -> s.hasPermission(2))
            .then(Commands.literal("set").then(Commands.argument("n", IntegerArgumentType.integer(0, MAX)).executes(c -> {
               ServerPlayer p = c.getSource().getPlayerOrException();
               set(p, IntegerArgumentType.getInteger(c, "n"));
               sync(p);
               c.getSource().sendSuccess(() -> Component.literal("Luck meter: " + get(p) + " / " + MAX), false);
               return 1;
            })))
            .then(Commands.literal("add").then(Commands.argument("n", IntegerArgumentType.integer(-MAX, MAX)).executes(c -> {
               ServerPlayer p = c.getSource().getPlayerOrException();
               add(p, IntegerArgumentType.getInteger(c, "n"));
               sync(p);
               c.getSource().sendSuccess(() -> Component.literal("Luck meter: " + get(p) + " / " + MAX), false);
               return 1;
            })))
      );
   }
}
