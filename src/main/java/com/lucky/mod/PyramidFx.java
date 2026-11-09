package com.lucky.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * A real, solid square pyramid built from block-display entities (4 base edges + 4 slanted edges
 * + an optional flat "fill" cap that grows as it absorbs), reusing the oriented-beam trick from
 * {@link Hypercube}. Vanilla entities only. Created on first update(), freed by remove().
 */
public final class PyramidFx {
   private final ServerLevel sw;
   private final Vec3 apex;
   private final String edgeBlock;
   private final String fillBlock;
   private final List<Entity> edges = new ArrayList<>();
   private final List<Entity> fill = new ArrayList<>();
   private boolean built;

   public PyramidFx(ServerLevel sw, Vec3 apex, String edgeBlock, String fillBlock) {
      this.sw = sw;
      this.apex = apex;
      this.edgeBlock = edgeBlock;
      this.fillBlock = fillBlock;
   }

   private static String f(double v) {
      return String.format(Locale.ROOT, "%.4ff", v);
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
      return "{id:\"minecraft:block_display\",Pos:[" + apex.x + "," + apex.y + "," + apex.z + "],block_state:{Name:\"" + block
         + "\"},brightness:{sky:15,block:15},view_range:4.0f,width:80f,height:80f,Tags:[\"lbm_fx\"]," + tf + "," + anim + "}";
   }

   private static double[] rotate(double[] q, double x, double y, double z) {
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

   private void apply(List<Entity> bucket, int idx, String snbt, boolean first) {
      try {
         CompoundTag tag = TagParser.parseTag(snbt);
         if (first) {
            Entity e = EntityType.BLOCK_DISPLAY.create(sw);
            if (e == null) {
               return;
            }
            e.load(tag);
            e.setPos(apex.x, apex.y, apex.z);
            sw.addFreshEntity(e);
            bucket.add(e);
         } else if (idx < bucket.size()) {
            Entity e = bucket.get(idx);
            CompoundTag cur = e.saveWithoutId(new CompoundTag());
            cur.merge(tag);
            e.load(cur);
         }
      } catch (CommandSyntaxException | RuntimeException ex) {
         // never let a visual break the ability
      }
   }

   /**
    * baseHalf = half-width of the square base; height = apex above base; phase = slow spin angle;
    * fillLevel 0..1 = how solid the base cap looks (0 = outline only, 1 = fully filled base plate).
    */
   public void update(double baseHalf, double height, double phase, double fillLevel) {
      double[][] corners = new double[4][3];
      for (int i = 0; i < 4; i++) {
         double sx = (i == 0 || i == 3) ? -1 : 1;
         double sz = (i < 2) ? -1 : 1;
         double rx = sx * baseHalf * Math.cos(phase) - sz * baseHalf * Math.sin(phase);
         double rz = sx * baseHalf * Math.sin(phase) + sz * baseHalf * Math.cos(phase);
         corners[i][0] = rx;
         corners[i][1] = -height;
         corners[i][2] = rz;
      }
      double thick = Math.max(0.15, Math.min(0.55, baseHalf * 0.07));
      boolean first = !built;
      built = true;
      double[] top = new double[]{0.0, 0.0, 0.0};
      for (int i = 0; i < 4; i++) {
         apply(edges, i, beam(edgeBlock, corners[i], corners[(i + 1) % 4], thick, first), first);
      }
      for (int i = 0; i < 4; i++) {
         apply(edges, 4 + i, beam(edgeBlock, corners[i], top, thick, first), first);
      }
      if (fillLevel > 0.01) {
         double fh = Math.max(0.1, 0.5 * fillLevel);
         double[] a = new double[]{0.0, -height, 0.0};
         double[] b = new double[]{0.0, -height + fh, 0.0};
         String fb = fillBlock;
         double fillHalf = baseHalf * fillLevel;
         String tf = "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:["
            + f(fillHalf * 2.0) + "," + f(fh) + "," + f(fillHalf * 2.0) + "],translation:[" + f(-fillHalf) + "," + f(-height) + "," + f(-fillHalf) + "]}";
         String anim = "start_interpolation:0,interpolation_duration:3";
         String snbt = first
            ? "{id:\"minecraft:block_display\",Pos:[" + apex.x + "," + apex.y + "," + apex.z + "],block_state:{Name:\"" + fb
               + "\"},brightness:{sky:15,block:15},view_range:4.0f,width:80f,height:80f,Tags:[\"lbm_fx\"]," + tf + "," + anim + "}"
            : "{" + tf + "," + anim + "}";
         apply(fill, 0, snbt, first && fill.isEmpty());
      }
   }

   public void remove() {
      for (Entity e : edges) {
         e.discard();
      }
      for (Entity e : fill) {
         e.discard();
      }
      edges.clear();
      fill.clear();
   }
}
