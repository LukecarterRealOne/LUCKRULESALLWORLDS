package com.lucky.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/** Math-generated 3D / 4D wireframes drawn with particles. All original code, no assets. */
public final class Shapes {
   public enum Poly {
      CELL5, CELL16, CELL24
   }

   private static final double[][][] VERTS = new double[Poly.values().length][][];
   private static final int[][][] EDGES = new int[Poly.values().length][][];

   static {
      double r5 = Math.sqrt(5.0);
      VERTS[Poly.CELL5.ordinal()] = new double[][]{
         {1, 1, 1, -1 / r5}, {1, -1, -1, -1 / r5}, {-1, 1, -1, -1 / r5}, {-1, -1, 1, -1 / r5}, {0, 0, 0, r5 - 1 / r5}
      };
      double[][] c16 = new double[8][4];
      for (int i = 0; i < 4; i++) {
         c16[i * 2][i] = 1;
         c16[i * 2 + 1][i] = -1;
      }
      VERTS[Poly.CELL16.ordinal()] = c16;
      List<double[]> c24 = new ArrayList<>();
      for (int a = 0; a < 4; a++) {
         for (int b = a + 1; b < 4; b++) {
            for (int sa = -1; sa <= 1; sa += 2) {
               for (int sb = -1; sb <= 1; sb += 2) {
                  double[] v = new double[4];
                  v[a] = sa;
                  v[b] = sb;
                  c24.add(v);
               }
            }
         }
      }
      VERTS[Poly.CELL24.ordinal()] = c24.toArray(new double[0][]);
      for (Poly p : Poly.values()) {
         double[][] v = VERTS[p.ordinal()];
         double min = Double.MAX_VALUE;
         for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
               min = Math.min(min, dist(v[i], v[j]));
            }
         }
         List<int[]> e = new ArrayList<>();
         for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
               if (dist(v[i], v[j]) < min + 1.0E-6) {
                  e.add(new int[]{i, j});
               }
            }
         }
         EDGES[p.ordinal()] = e.toArray(new int[0][]);
      }
   }

   private Shapes() {
   }

   private static double dist(double[] a, double[] b) {
      double s = 0;
      for (int i = 0; i < 4; i++) {
         s += (a[i] - b[i]) * (a[i] - b[i]);
      }
      return Math.sqrt(s);
   }

   private static void rot(double[] v, int i, int j, double ang) {
      double c = Math.cos(ang);
      double s = Math.sin(ang);
      double a = v[i] * c - v[j] * s;
      double b = v[i] * s + v[j] * c;
      v[i] = a;
      v[j] = b;
   }

   /** Rotating 4D polytope, perspective-projected. interior = extra points per edge (0..3). */
   public static void poly4(ServerLevel sw, Poly poly, ParticleOptions edge, ParticleOptions corner, double cx, double cy, double cz, double size, double phase, int interior) {
      double[][] src = VERTS[poly.ordinal()];
      double norm = 0;
      for (double[] v : src) {
         norm = Math.max(norm, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2] + v[3] * v[3]));
      }
      double[][] pr = new double[src.length][3];
      for (int i = 0; i < src.length; i++) {
         double[] v = src[i].clone();
         for (int k = 0; k < 4; k++) {
            v[k] /= norm;
         }
         rot(v, 0, 3, phase * 0.9);
         rot(v, 1, 3, phase * 0.7 + 0.4);
         rot(v, 2, 3, phase * 0.55);
         rot(v, 0, 2, phase * 0.3);
         double persp = 1.0 / Math.max(0.7, 1.9 - v[3] * 0.6);
         pr[i][0] = v[0] * size * persp * 1.5;
         pr[i][1] = v[1] * size * persp * 1.5;
         pr[i][2] = v[2] * size * persp * 1.5;
      }
      int n = Math.max(0, Math.min(interior, 3));
      for (int[] e : EDGES[poly.ordinal()]) {
         for (int k = 1; k <= n; k++) {
            double t = (double)k / (double)(n + 1);
            Fx.burst(sw, edge, cx + lerp(pr[e[0]][0], pr[e[1]][0], t), cy + lerp(pr[e[0]][1], pr[e[1]][1], t), cz + lerp(pr[e[0]][2], pr[e[1]][2], t), 1, 0, 0, 0, 0);
         }
      }
      for (double[] p : pr) {
         Fx.burst(sw, corner, cx + p[0], cy + p[1], cz + p[2], 1, 0.04, 0.04, 0.04, 0.0);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   /** (2,3) torus knot, tilted and spinning. */
   public static void torusKnot(ServerLevel sw, ParticleOptions p, double cx, double cy, double cz, double radius, double phase) {
      int steps = 36;
      for (int i = 0; i < steps; i++) {
         double t = (double)i / steps * Math.PI * 2.0;
         double r = 0.62 + 0.3 * Math.cos(3.0 * t);
         double x = r * Math.cos(2.0 * t);
         double z = r * Math.sin(2.0 * t);
         double y = 0.3 * Math.sin(3.0 * t);
         double c = Math.cos(phase);
         double s = Math.sin(phase);
         Fx.burst(sw, p, cx + (x * c - z * s) * radius * 1.4, cy + y * radius * 1.4, cz + (x * s + z * c) * radius * 1.4, 1, 0, 0, 0, 0);
      }
   }

   /** Armillary sphere: three tilted orbiting rings. */
   public static void armillary(ServerLevel sw, ParticleOptions p, ParticleOptions accent, double cx, double cy, double cz, double radius, double phase) {
      for (int ring = 0; ring < 3; ring++) {
         double tilt = ring * Math.PI / 3.0 + phase * 0.4;
         double yaw = ring * 2.1 + phase;
         int pts = 16;
         for (int i = 0; i < pts; i++) {
            double a = (double)i / pts * Math.PI * 2.0;
            double x = Math.cos(a) * radius;
            double y = Math.sin(a) * radius * Math.cos(tilt);
            double z = Math.sin(a) * radius * Math.sin(tilt);
            double rx = x * Math.cos(yaw) - z * Math.sin(yaw);
            double rz = x * Math.sin(yaw) + z * Math.cos(yaw);
            Fx.burst(sw, ring == 1 ? accent : p, cx + rx, cy + y, cz + rz, 1, 0, 0, 0, 0);
         }
      }
      Fx.burst(sw, ParticleTypes.END_ROD, cx, cy, cz, 2, 0.1, 0.1, 0.1, 0.0);
   }

   /** Short themed flourish around the caster for any ability: shape + colour picked by ability index. */
   public static void signature(ServerLevel sw, Player user, int ability) {
      DustParticleOptions[] pal = new DustParticleOptions[]{
         new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.15F), 1.3F),
         new DustParticleOptions(new Vector3f(0.55F, 0.2F, 1.0F), 1.3F),
         new DustParticleOptions(new Vector3f(0.2F, 0.85F, 1.0F), 1.3F)
      };
      DustParticleOptions c1 = pal[ability % 3];
      DustParticleOptions c2 = pal[(ability + 1) % 3];
      int shape = ability % 5;
      for (int t = 0; t <= 14; t += 2) {
         int tick = t;
         Scheduler.later(t + 1, () -> {
            if (!user.isAlive() || user.level() != sw) {
               return;
            }
            double x = user.getX();
            double y = user.getY() + 1.1;
            double z = user.getZ();
            double ph = tick * 0.22 + ability;
            double grow = 1.0 + tick * 0.05;
            switch (shape) {
               case 0 -> poly4(sw, Poly.CELL24, c1, ParticleTypes.END_ROD, x, y, z, 1.7 * grow, ph, 1);
               case 1 -> torusKnot(sw, c1, x, y, z, 1.4 * grow, ph);
               case 2 -> armillary(sw, c1, c2, x, y, z, 1.9 * grow, ph);
               case 3 -> poly4(sw, Poly.CELL16, c1, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1.9 * grow, ph, 2);
               default -> poly4(sw, Poly.CELL5, c2, ParticleTypes.END_ROD, x, y, z, 2.0 * grow, ph, 3);
            }
            if (tick == 0) {
               Fx.ring(sw, c2, x, user.getY() + 0.1, z, 2.4, 18);
               Fx.sound(sw, user.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.7F + (ability % 7) * 0.08F);
            }
         });
      }
   }
}
