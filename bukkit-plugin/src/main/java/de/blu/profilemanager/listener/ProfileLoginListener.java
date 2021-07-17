package de.blu.profilemanager.listener;

import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.event.ProfileLoginEvent;
import de.blu.profilemanager.event.ProfileLogoutEvent;
import de.blu.profilesystem.data.Profile;
import de.blu.profilesystem.exception.ServiceUnreachableException;
import de.blu.profilesystem.util.ProfileWebRequester;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Singleton
public final class ProfileLoginListener implements Listener {

  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private MainConfig mainConfig;
  @Inject private ExecutorService executorService;
  @Inject private JavaPlugin javaPlugin;

  @EventHandler
  public void onLogin(PlayerLoginEvent e) {
    final UUID playerId = e.getPlayer().getUniqueId();

    this.executorService.submit(
        () -> {
          // Login to last Profile or create if not exist
          try {
            List<Profile> profiles =
                this.profileWebRequester.getProfilesByPlayer(
                    this.mainConfig.getServiceUrl(), playerId);
            Profile profile = null;
            if (profiles.stream().filter(profile1 -> !profile1.isDisabled()).count() == 0) {
              // Create Profile
              profile = this.createProfile(playerId, e.getPlayer().getName());
            } else {
              for (Profile targetProfile : profiles) {
                if (targetProfile.getLoggedInPlayerId() != null || targetProfile.isDisabled()) {
                  continue;
                }

                if (profile == null) {
                  profile = targetProfile;
                  continue;
                }

                long lastLogin = 0;
                long targetLastLogin = 0;

                if (profile.getPlayTimes().size() > 0) {
                  lastLogin = profile.getPlayTimes().get(profile.getPlayTimes().size() - 1).getTo();
                }

                if (targetProfile.getPlayTimes().size() > 0) {
                  targetLastLogin =
                      targetProfile
                          .getPlayTimes()
                          .get(targetProfile.getPlayTimes().size() - 1)
                          .getTo();
                }

                if (targetLastLogin <= lastLogin) {
                  continue;
                }

                profile = targetProfile;
              }
            }

            if (profile == null) {
              return;
            }

            this.profileWebRequester.login(this.mainConfig.getServiceUrl(), playerId, profile);
            Profile finalProfile = profile;
            new BukkitRunnable() {
              @Override
              public void run() {
                Bukkit.getServer()
                        .getPluginManager()
                        .callEvent(new ProfileLoginEvent(e.getPlayer(), finalProfile));
              }
            }.runTask(this.javaPlugin);
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }
        });
  }

  private Profile createProfile(UUID playerId, String profileName) {
    try {
      return this.profileWebRequester.createProfile(
          this.mainConfig.getServiceUrl(), playerId, profileName);
    } catch (ServiceUnreachableException e) {
      e.printStackTrace();
    }

    return null;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();

    this.executorService.submit(
        () -> {
          try {
            Profile profile =
                this.profileWebRequester.getCurrentProfile(
                    this.mainConfig.getServiceUrl(), playerId);
            this.profileWebRequester.logout(this.mainConfig.getServiceUrl(), profile);
            Bukkit.getServer()
                .getPluginManager()
                .callEvent(new ProfileLogoutEvent(e.getPlayer(), profile));
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }
        });
  }
}
