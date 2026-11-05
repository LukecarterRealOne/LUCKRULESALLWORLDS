package com.lucky.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A real, solid, rotating hypercube built from block display entities (32 edges + 16 corners),
 * projected from 4D every update. Entities are tagged lbm_fx and are never saved/reloaded.
 */
public final class HyperFx {
   private final ServerLevel sw;
   private final double cx;
   private final double cy;
   private final double cz;
   private final List<Display.BlockDisplay> edges = new ArrayList<>();
   private final List<Display.BlockDisplay> corners = new ArrayList<>();
   private final int[][] edgeIdx = new int[32][2];

   private HyperFx(ServerLevel sw, double cx, double cy, double cz) {
      this.sw = sw;
      this.cx = cx;
      this.cy = cy;
      this.cz = cz;
   }

   public static HyperFx spawn(ServerLevel sw, double cx, double cy, double cz, String outer, String inner, String link, String corner) {
      HyperFx h = new HyperFx(sw, cx, cy, cz);
      int n = 0;
      for (int i = 0; i < 16; i++) {
         for (int axis = 0; axis < 4; axis++) {
            int mask = 1 << axis;
            if ((i & mask) == 0) {
               h.edgeIdx[n][0] = i;
               h.edgeIdx[n][1] = i | mask;
               n++;
               String block = axis == 3 ? link : ((i & 8) == 0 ? outer : inner);
               h.edges.add(h.make(block));
            }
         }
      }
      for (int i = 0; i < 16; i++) {
         h.corners.add(h.make(corner));
      }
      return h;
   }

   private Display.BlockDisplay make(String block) {
      Display.BlockDisplay e = EntityType.BLOCK_DISPLAY.create(sw);
      if (e == null) {
         return null;
      }
      CompoundTag t = base(new Vec3(cx, cy, cz), block);
      t.put("transformation", transform(new Vector3f(), new Vector3f(0.01F, 0.01F, 0.01F), new Quaternionf()));
      t.putInt("interpolation_duration", 0);
      e.load(t);
      e.setPos(cx, cy, cz);
      sw.addFreshEntity(e);
      return e;
   }

   private static CompoundTag base(Vec3 pos, String block) {
      CompoundTag t = new CompoundTag();
      ListTag p = new ListTag();
      p.add(DoubleTag.valueOf(pos.x));
      p.add(DoubleTag.valueOf(pos.y));
      p.add(DoubleTag.valueOf(pos.z));
      t.put("Pos", p);
      ListTag m = new ListTag();
      for (int i = 0; i < 3; i++) {
         m.add(DoubleTag.valueOf(0.0));
      }
      t.put("Motion", m);
      ListTag r = new ListTag();
      r.add(FloatTag.valueOf(0.0F));
      r.add(FloatTag.valueOf(0.0F));
      t.put("Rotation", r);
      ListTag tags = new ListTag();
      tags.add(StringTag.valueOf("lbm_fx"));
      t.put("Tags", tags);
      CompoundTag bs = new CompoundTag();
      bs.putString("Name", block);
      t.put("block_state", bs);
      CompoundTag br = new CompoundTag();
      br.putInt("sky", 15);
      br.putInt("block", 15);
      t.put("brightness", br);
      t.putFloat("view_range", 8.0F);
      t.putFloat("shadow_radius", 0.0F);
      return t;
   }

   private static CompoundTag transform(Vector3f translation, Vector3f scale, Quaternionf q) {
      CompoundTag tr = new CompoundTag();
      tr.put("translation", floats(translation.x, translation.y, translation.z));
      tr.put("scale", floats(scale.x, scale.y, scale.z));
      tr.put("left_rotation", floats(q.x, q.y, q.z, q.w));
      tr.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
      return tr;
   }

   private static ListTag floats(float... v) {
      ListTag l = new ListTag();
      for (float f : v) {
         l.add(FloatTag.valueOf(f));
      }
      return l;
   }

   private void apply(Display.BlockDisplay e, Vector3f tr, Vector3f sc, Quaternionf q, int interp) {
      if (e == null || e.isRemoved()) {
         return;
      }
      CompoundTag t = new CompoundTag();
      e.saveWithoutId(t);
      t.put("transformation", transform(tr, sc, q));
      t.putInt("start_interpolation", 0);
      t.putInt("interpolation_duration", interp);
      e.load(t);
   }

   /** size = same half-size units as Fx.tesseract; phase = rotation angle; interp = client smoothing ticks. */
   public void update(double size, double phase, int interp) {
      size *= 1.0 + 0.04 * Math.sin(phase * 7.0);
      double ax = phase * 0.91;
      double ay = phase * 0.73 + 0.37;
      double az = phase * 0.59;
      double yaw = phase * 0.31;
      double pitch = phase * 0.22;
      double[][] pr = new double[16][3];
      double[] pe = new double[16];
      for (int i = 0; i < 16; i++) {
         double x = (i & 1) == 0 ? -1.0 : 1.0;
         double y = (i & 2) == 0 ? -1.0 : 1.0;
         double z = (i & 4) == 0 ? -1.0 : 1.0;
         double w = (i & 8) == 0 ? -1.0 : 1.0;
         double nx = x * Math.cos(ax) - w * Math.sin(ax);
         w = x * Math.sin(ax) + w * Math.cos(ax);
         double ny = y * Math.cos(ay) - w * Math.sin(ay);
         w = y * Math.sin(ay) + w * Math.cos(ay);
         double nz = z * Math.cos(az) - w * Math.sin(az);
         w = z * Math.sin(az) + w * Math.cos(az);
         double persp = 1.0 / Math.max(0.8, 1.7 - w * 0.33);
         pe[i] = persp;
         x = nx * size * persp;
         y = ny * size * persp;
         z = nz * size * persp;
         double rx = x * Math.cos(yaw) - z * Math.sin(yaw);
         double rz = x * Math.sin(yaw) + z * Math.cos(yaw);
         double ry = y * Math.cos(pitch) - rz * Math.sin(pitch);
         rz = y * Math.sin(pitch) + rz * Math.cos(pitch);
         pr[i][0] = rx;
         pr[i][1] = ry;
         pr[i][2] = rz;
      }
      for (int n = 0; n < 32; n++) {
         int a = edgeIdx[n][0];
         int b = edgeIdx[n][1];
         Vector3f d = new Vector3f((float)(pr[b][0] - pr[a][0]), (float)(pr[b][1] - pr[a][1]), (float)(pr[b][2] - pr[a][2]));
         float len = d.length();
         if (len < 1.0E-3F) {
            continue;
         }
         float th = (float)Math.max(0.12, size * 0.03 * (pe[a] + pe[b]) * 0.5);
         d.div(len);
         Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), d);
         Vector3f off = new Vector3f(th / 2.0F, th / 2.0F, len / 2.0F);
         q.transform(off);
         Vector3f mid = new Vector3f((float)(pr[a][0] + pr[b][0]) / 2.0F, (float)(pr[a][1] + pr[b][1]) / 2.0F, (float)(pr[a][2] + pr[b][2]) / 2.0F);
         apply(edges.get(n), mid.sub(off), new Vector3f(th, th, len), q, interp);
      }
      for (int i = 0; i < 16; i++) {
         float s = (float)Math.max(0.3, size * 0.07 * pe[i]);
         Vector3f tr = new Vector3f((float)pr[i][0] - s / 2.0F, (float)pr[i][1] - s / 2.0F, (float)pr[i][2] - s / 2.0F);
         apply(corners.get(i), tr, new Vector3f(s, s, s), new Quaternionf(), interp);
      }
   }

   public void remove() {
      for (Display.BlockDisplay e : edges) {
         if (e != null) {
            e.discard();
         }
      }
      for (Display.BlockDisplay e : corners) {
         if (e != null) {
            e.discard();
         }
      }
      edges.clear();
      corners.clear();
   }
}
