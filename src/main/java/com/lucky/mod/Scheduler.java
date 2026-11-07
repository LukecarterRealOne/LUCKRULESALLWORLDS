package com.lucky.mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(
   modid = "luckyblockmod"
)
public class Scheduler {
   private static final List<Scheduler.Task> TASKS = new ArrayList<>();
   private static final List<Scheduler.Task> QUEUE = new ArrayList<>();

   public Scheduler() {
   }

   public static void later(int ticks, Runnable action) {
      QUEUE.add(new Scheduler.Task(Math.max(1, ticks), action));
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      {
         LuckyBlockItem.captureSnapshots(event.getServer());
         TASKS.addAll(QUEUE);
         QUEUE.clear();
         Iterator<Scheduler.Task> it = TASKS.iterator();

         while (it.hasNext()) {
            Scheduler.Task t = it.next();
            if (--t.delay <= 0) {
               it.remove();

               try {
                  t.action.run();
               } catch (Exception var4) {
                  var4.printStackTrace();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      try {
         MinecraftServer server = event.getServer();
         server.getCommands().getDispatcher().execute("kill @e[tag=lbm_beam]", server.createCommandSourceStack());
      } catch (Exception var2) {
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      TASKS.clear();
      QUEUE.clear();
   }

   private static class Task {
      int delay;
      final Runnable action;

      Task(int delay, Runnable action) {
         this.delay = delay;
         this.action = action;
      }
   }
}
