package com.lucky.mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class Fx {
   private static final int MAX_BURST_PARTICLES = 24;
   private static final int MAX_RING_POINTS = 18;
   private static final int MAX_WORLD_PARTICLE_CALLS_PER_TICK = 160;
   private static final int MAX_WORLD_PARTICLES_PER_TICK = 350;
   private static final Map<ServerLevel, Fx.ParticleBudget> PARTICLE_BUDGETS = new WeakHashMap<>();
   private static final Map<UUID, Long> LAST_SCREEN_TICK = new HashMap<>();
   private static long lastScreenCleanupTick = Long.MIN_VALUE;

   public Fx() {
   }

   public static void burst(ServerLevel sw, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
      int requested = Math.max(0, Math.min(count, 24));
      if (requested != 0) {
         boolean hasViewer = false;

         for (ServerPlayer player : sw.players()) {
            if (player.distanceToSqr(x, y, z) < 25600.0) {
               hasViewer = true;
               break;
            }
         }

         if (hasViewer) {
            int safeCount;
            synchronized (PARTICLE_BUDGETS) {
               Fx.ParticleBudget budget = PARTICLE_BUDGETS.computeIfAbsent(sw, ignored -> new Fx.ParticleBudget());
               budget.resetIfNeeded(sw.getGameTime());
               if (budget.calls >= 160 || budget.particles >= 350) {
                  return;
               }

               safeCount = Math.min(requested, 350 - budget.particles);
               budget.calls++;
               budget.particles += safeCount;
            }

            for (ServerPlayer playerx : sw.players()) {
               if (playerx.distanceToSqr(x, y, z) < 25600.0) {
                  sw.sendParticles(playerx, particle, true, x, y, z, safeCount, dx, dy, dz, speed);
               }
            }
         }
      }
   }

   public static void ring(ServerLevel sw, ParticleOptions particle, double cx, double cy, double cz, double radius, int points) {
      int safePoints = Math.max(8, Math.min(points, 18));

      for (int i = 0; i < safePoints; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)safePoints;
         burst(sw, particle, cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static void arc(
      ServerLevel sw, ParticleOptions particle, double cx, double cy, double cz, double radius, double startAngle, double angleSpan, int points
   ) {
      int safePoints = Math.max(6, Math.min(points, 18));

      for (int i = 0; i <= safePoints; i++) {
         double angle = startAngle + angleSpan * (double)i / (double)safePoints;
         burst(sw, particle, cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static void line(ServerLevel sw, ParticleOptions particle, double x1, double y1, double z1, double x2, double y2, double z2, int points) {
      int safePoints = Math.max(2, Math.min(points, 18));

      for (int i = 0; i <= safePoints; i++) {
         double t = (double)i / (double)safePoints;
         burst(sw, particle, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static void cubeOutline(ServerLevel sw, ParticleOptions particle, double cx, double cy, double cz, double halfSize, double yaw, int pointsPerEdge) {
      int steps = Math.max(2, Math.min(pointsPerEdge, 6));
      double sin = Math.sin(yaw);
      double cos = Math.cos(yaw);
      double[][] vertices = new double[8][3];

      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? -halfSize : halfSize;
         double y = (i & 2) == 0 ? -halfSize : halfSize;
         double z = (i & 4) == 0 ? -halfSize : halfSize;
         vertices[i][0] = x * cos - z * sin;
         vertices[i][1] = y;
         vertices[i][2] = x * sin + z * cos;
      }

      for (int i = 0; i < 8; i++) {
         for (int axis = 0; axis < 3; axis++) {
            if ((i & 1 << axis) == 0) {
               int j = i | 1 << axis;

               for (int step = 0; step < steps; step++) {
                  double t = (double)step / (double)(steps - 1);
                  double x = vertices[i][0] + (vertices[j][0] - vertices[i][0]) * t;
                  double y = vertices[i][1] + (vertices[j][1] - vertices[i][1]) * t;
                  double z = vertices[i][2] + (vertices[j][2] - vertices[i][2]) * t;
                  burst(sw, particle, cx + x, cy + y, cz + z, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   public static void tesseract(
      ServerLevel sw,
      ParticleOptions outerFrame,
      ParticleOptions innerFrame,
      ParticleOptions connectors,
      ParticleOptions corners,
      double cx,
      double cy,
      double cz,
      double halfSize,
      double phase,
      int pointsPerEdge
   ) {
      int samples = Math.max(2, Math.min(pointsPerEdge, 4));
      double[][] projected = new double[16][3];
      double ax = phase * 0.91;
      double ay = phase * 0.73 + 0.37;
      double az = phase * 0.59;
      double cosX = Math.cos(ax);
      double sinX = Math.sin(ax);
      double cosY = Math.cos(ay);
      double sinY = Math.sin(ay);
      double cosZ = Math.cos(az);
      double sinZ = Math.sin(az);
      double yaw = phase * 0.31;
      double pitch = phase * 0.22;
      double cosYaw = Math.cos(yaw);
      double sinYaw = Math.sin(yaw);
      double cosPitch = Math.cos(pitch);
      double sinPitch = Math.sin(pitch);

      for (int i = 0; i < 16; i++) {
         double x = (i & 1) == 0 ? -1.0 : 1.0;
         double y = (i & 2) == 0 ? -1.0 : 1.0;
         double z = (i & 4) == 0 ? -1.0 : 1.0;
         double w = (i & 8) == 0 ? -1.0 : 1.0;
         double nx = x * cosX - w * sinX;
         w = x * sinX + w * cosX;
         double ny = y * cosY - w * sinY;
         w = y * sinY + w * cosY;
         double nz = z * cosZ - w * sinZ;
         w = z * sinZ + w * cosZ;
         double perspective = 1.0 / Math.max(0.8, 1.7 - w * 0.33);
         x = nx * halfSize * perspective;
         y = ny * halfSize * perspective;
         z = nz * halfSize * perspective;
         double rx = x * cosYaw - z * sinYaw;
         double rz = x * sinYaw + z * cosYaw;
         double ry = y * cosPitch - rz * sinPitch;
         rz = y * sinPitch + rz * cosPitch;
         projected[i][0] = rx;
         projected[i][1] = ry;
         projected[i][2] = rz;
      }

      for (int i = 0; i < 16; i++) {
         for (int axis = 0; axis < 4; axis++) {
            int mask = 1 << axis;
            if ((i & mask) == 0) {
               int j = i | mask;
               ParticleOptions particle = axis == 3 ? connectors : ((i & 8) == 0 ? outerFrame : innerFrame);

               for (int sample = 0; sample < samples; sample++) {
                  double t = (double)sample / (double)(samples - 1);
                  double x = projected[i][0] + (projected[j][0] - projected[i][0]) * t;
                  double y = projected[i][1] + (projected[j][1] - projected[i][1]) * t;
                  double z = projected[i][2] + (projected[j][2] - projected[i][2]) * t;
                  burst(sw, particle, cx + x, cy + y, cz + z, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
      }

      for (int i = 0; i < 16; i++) {
         burst(sw, corners, cx + projected[i][0], cy + projected[i][1], cz + projected[i][2], 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static void epicImpact(ServerLevel sw, Vec3 center, ParticleOptions energy, ParticleOptions sparks, double radius, int color) {
      double x = center.x;
      double y = center.y;
      double z = center.z;
      double safeRadius = Math.max(2.0, Math.min(24.0, radius));
      burst(sw, ParticleTypes.EXPLOSION_EMITTER, x, y + 0.25, z, 1, 0.0, 0.0, 0.0, 0.0);
      burst(sw, ParticleTypes.FLASH, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
      burst(sw, ParticleTypes.SONIC_BOOM, x, y + 0.3, z, 1, 0.0, 0.0, 0.0, 0.0);
      burst(sw, energy, x, y + 0.6, z, 24, 0.7, 0.45, 0.7, 0.13);
      burst(sw, sparks, x, y + 0.7, z, 24, 0.9, 0.65, 0.9, 0.22);
      ring(sw, energy, x, y + 0.15, z, safeRadius * 0.52, 18);
      ring(sw, sparks, x, y + 0.45, z, safeRadius, 18);

      for (int i = 0; i < 8; i++) {
         double angle = (Math.PI * 2) * (double)i / 8.0;
         double dx = Math.cos(angle);
         double dz = Math.sin(angle);
         line(
            sw,
            (i & 1) == 0 ? energy : sparks,
            x + dx * 0.8,
            y + 0.15,
            z + dz * 0.8,
            x + dx * safeRadius * 1.18,
            y + 0.15 + (double)(i % 3 - 1) * 0.45,
            z + dz * safeRadius * 1.18,
            4
         );
      }

      screen(sw, center, safeRadius * 3.0, 0.38F, 1.55F, 12, color);
   }

   public static void sound(ServerLevel sw, BlockPos pos, SoundEvent sound, float volume, float pitch) {
      sw.playSound(null, pos, sound, SoundSource.PLAYERS, volume, pitch);
   }

   public static void sound(ServerLevel sw, BlockPos pos, Holder<SoundEvent> sound, float volume, float pitch) {
      sw.playSound(null, pos, (SoundEvent)sound.value(), SoundSource.PLAYERS, volume, pitch);
   }

   public static void screen(ServerLevel sw, Vec3 pos, double radius, float flash, float shake, int duration, int color) {
      long tick = sw.getGameTime();
      synchronized (LAST_SCREEN_TICK) {
         if (tick % 200L == 0L && tick != lastScreenCleanupTick) {
            LAST_SCREEN_TICK.entrySet().removeIf(entry -> tick - entry.getValue() > 1200L);
            lastScreenCleanupTick = tick;
         }
      }

      for (ServerPlayer player : sw.players()) {
         if (!(player.distanceToSqr(pos) >= radius * radius)) {
            UUID id = player.getUUID();
            synchronized (LAST_SCREEN_TICK) {
               Long last = LAST_SCREEN_TICK.get(id);
               if (last != null && tick - last < 4L) {
                  continue;
               }

               LAST_SCREEN_TICK.put(id, tick);
            }

            LuckyBlockMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FxPacket(flash, shake, duration, color));
         }
      }
   }

   public static void cmd(ServerLevel sw, String command) {
      try {
         MinecraftServer server = sw.getServer();
         server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withLevel(sw).withSuppressedOutput());
      } catch (Exception var3) {
      }
   }

   private static final class ParticleBudget {
      private long tick = Long.MIN_VALUE;
      private int calls;
      private int particles;

      private ParticleBudget() {
      }

      private void resetIfNeeded(long currentTick) {
         if (this.tick != currentTick) {
            this.tick = currentTick;
            this.calls = 0;
            this.particles = 0;
         }
      }
   }
}
