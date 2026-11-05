package com.lucky.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A real, solid, glowing tesseract built from block-display entities (32 edges + 16 corners),
 * rotated through the 4th dimension and re-projected every update. Vanilla entities only:
 * no client code, no assets. Created on first update(), freed by remove().
 */
public final class Hypercube {
   private final ServerLevel sw;
   private final Vec3 c;
   private final String outer;
   private final String inner;
   private final String link;
   private final String corner;
   private final List<Entity> parts = new ArrayList<>();
   private final int[][] edges = new int[32][2];
   private boolean built;

   public Hypercube(ServerLevel sw, Vec3 center, String outer, String inner, String link, String corner) {
      this.sw = sw;
      this.c = center;
      this.outer = outer;
      this.inner = inner;
      this.link = link;
      this.corner = corner;
      int n = 0;
      for (int i = 0; i < 16; i++) {
         for (int axis = 0; axis < 4; axis++) {
            if ((i & 1 << axis) == 0) {
               edges[n][0] = i;
               edges[n][1] = i | 1 << axis;
               n++;
            }
         }
      }
   }

   private static String f(double v) {
      return String.format(Locale.ROOT, "%.4ff", v);
   }

   private static double[] rotate(double[] q, double x, double y, double z) {
      // v' = v + 2w(q x v) + 2 q x (q x v)
      double qx = q[0], qy = q[1], qz = q[2], w = q[3];
      double tx = 2 * (qy * z - qz * y);
      double ty = 2 * (qz * x - qx * z);
      double tz = 2 * (qx * y - qy * x);
      return new double[]{
         x + w * tx + (qy * tz - qz * ty),
         y + w * ty + (qz * tx - qx * tz),
         z + w * tz + (qx * ty - qy * tx)
      };
   }

   private String beam(String block, double[] a, double[] b, double thick, boolean first) {
      double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      double[] q = new double[]{0, 0, 0, 1};
      if (len > 1.0E-5) {
         double ux = dx / len, uy = dy / len, uz = dz / len;
         double w = 1.0 + uy;
         if (w < 1.0E-6) {
            q = new double[]{1, 0, 0, 0};
         } else {
            double qx = uz, qy = 0, qz = -ux;
            double n = Math.sqrt(qx * qx + qy * qy + qz * qz + w * w);
            q = new double[]{qx / n, qy / n, qz / n, w / n};
         }
      }
      len = Math.max(len, 0.01);
      double[] off = rotate(q, thick / 2, len / 2, thick / 2);
      double mx = (a[0] + b[0]) / 2 - off[0];
      double my = (a[1] + b[1]) / 2 - off[1];
      double mz = (a[2] + b[2]) / 2 - off[2];
      String tf = "transformation:{left_rotation:[" + f(q[0]) + "," + f(q[1]) + "," + f(q[2]) + "," + f(q[3]) + "],right_rotation:[0f,0f,0f,1f],scale:["
         + f(thick) + "," + f(len) + "," + f(thick) + "],translation:[" + f(mx) + "," + f(my) + "," + f(mz) + "]}";
      String anim = "start_interpolation:0,interpolation_duration:3";
      if (!first) {
         return "{" + tf + "," + anim + "}";
      }
      return "{id:\"minecraft:block_display\",Pos:[" + c.x + "," + c.y + "," + c.z + "],block_state:{Name:\"" + block
         + "\"},brightness:{sky:15,block:15},view_range:4.0f,width:80f,height:80f,Tags:[\"lbm_fx\"]," + tf + "," + anim + "}";
   }

   private void apply(int idx, String snbt, boolean first) {
      try {
         CompoundTag tag = TagParser.parseTag(snbt);
         if (first) {
            Entity e = EntityType.BLOCK_DISPLAY.create(sw);
            if (e == null) {
               return;
            }
            e.load(tag);
            e.setPos(c.x, c.y, c.z);
            sw.addFreshEntity(e);
            parts.add(e);
         } else if (idx < parts.size()) {
            Entity e = parts.get(idx);
            CompoundTag cur = e.saveWithoutId(new CompoundTag());
            cur.merge(tag);
            e.load(cur);
         }
      } catch (CommandSyntaxException | RuntimeException ex) {
         // never let a visual break the ability
      }
   }

   /** halfSize in blocks; phase is the 4D rotation angle. */
   public void update(double halfSize, double phase) {
      double[][] p = new double[16][3];
      double yaw = phase * 0.35;
      for (int i = 0; i < 16; i++) {
         double x = (i & 1) == 0 ? -1 : 1;
         double y = (i & 2) == 0 ? -1 : 1;
         double z = (i & 4) == 0 ? -1 : 1;
         double w = (i & 8) == 0 ? -1 : 1;
         double nx = x * Math.cos(phase) - w * Math.sin(phase);
         w = x * Math.sin(phase) + w * Math.cos(phase);
         x = nx;
         double nz = z * Math.cos(phase * 0.5) - w * Math.sin(phase * 0.5);
         w = z * Math.sin(phase * 0.5) + w * Math.cos(phase * 0.5);
         z = nz;
         double persp = 1.0 / (2.4 - w * 0.55);
         x *= persp;
         y *= persp;
         z *= persp;
         p[i][0] = (x * Math.cos(yaw) - z * Math.sin(yaw)) * halfSize * 2.0;
         p[i][1] = y * halfSize * 2.0;
         p[i][2] = (x * Math.sin(yaw) + z * Math.cos(yaw)) * halfSize * 2.0;
      }
      double thick = Math.max(0.12, Math.min(0.6, halfSize * 0.05));
      boolean first = !built;
      built = true;
      for (int e = 0; e < 32; e++) {
         int i = edges[e][0];
         int j = edges[e][1];
         String block = (i ^ j) == 8 ? link : ((i & 8) == 0 ? outer : inner);
         apply(e, beam(block, p[i], p[j], thick, first), first);
      }
      double cs = thick * 2.4;
      for (int i = 0; i < 16; i++) {
         double[] a = new double[]{p[i][0], p[i][1] - cs / 2, p[i][2]};
         double[] b = new double[]{p[i][0], p[i][1] + cs / 2, p[i][2]};
         apply(32 + i, beam(corner, a, b, cs, first), first);
      }
   }

   public void remove() {
      for (Entity e : parts) {
         e.discard();
      }
      parts.clear();
   }
}
