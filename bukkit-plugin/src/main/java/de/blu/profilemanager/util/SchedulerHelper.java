package de.blu.profilemanager.util;

import de.blu.profilemanager.ProfileManager;
import org.bukkit.scheduler.BukkitRunnable;

public final class SchedulerHelper {
  public static void runSync(Runnable runnable) {
    new BukkitRunnable() {
      @Override
      public void run() {
        runnable.run();
      }
    }.runTask(ProfileManager.getInstance());
  }
}
