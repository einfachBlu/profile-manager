package de.blu.profilemanager;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import de.blu.database.DatabaseAPI;
import de.blu.database.storage.redis.RedisConnection;
import de.blu.localize.LocalizeAPI;
import de.blu.profilemanager.command.ProfilesCommand;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.listener.ProfileLoginListener;
import de.blu.profilemanager.util.SchedulerHelper;
import de.blu.profilemanager.util.signgui.SignGUI;
import de.blu.profilesystem.util.ProfileWebRequester;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public final class ProfileManager extends JavaPlugin {

  @Getter private static ProfileManager instance;
  @Inject private RedisConnection redisConnection;
  @Inject private LocalizeAPI localizeAPI;

  @Override
  public void onEnable() {
    ProfileManager.instance = this;

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(JavaPlugin.class).toInstance(ProfileManager.this);
                bind(ProfileWebRequester.class).toInstance(new ProfileWebRequester());
                bind(LocalizeAPI.class).toInstance(LocalizeAPI.getInstance());
                bind(ExecutorService.class).toInstance(Executors.newSingleThreadExecutor());
                bind(RedisConnection.class)
                    .toInstance(DatabaseAPI.getInstance().getRedisConnection());
              }
            });

    injector.injectMembers(this);
    this.init(injector);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(onlinePlayer, ""));
    }

    SignGUI.init(this);
  }

  @Override
  public void onDisable() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(onlinePlayer, ""));
    }

    SignGUI.destroy();
  }

  private void init(Injector injector) {
    injector.getInstance(MainConfig.class).init();

    // Register Commands
    this.getCommand("profiles").setExecutor(injector.getInstance(ProfilesCommand.class));

    // Register Listener
    this.getServer()
        .getPluginManager()
        .registerEvents(injector.getInstance(ProfileLoginListener.class), this);

    this.redisConnection.subscribe(
        (channel, message) -> {
          UUID playerId = UUID.fromString(message);
          Player player = Bukkit.getPlayer(playerId);
          if (player == null) {
            return;
          }

          String kickMessage = "";
          switch (channel) {
            case "ProfileKickPlayerProfileDisabled":
              kickMessage = this.localizeAPI.getMessage(playerId, "profile-disabled-kick-message");
              break;
            case "ProfileKickPlayerProfileOtherLogin":
              kickMessage = this.localizeAPI.getMessage(playerId, "profile-other-kick-message");
              break;
          }

          String finalKickMessage = kickMessage;
          SchedulerHelper.runSync(
              () ->
                  player.kick(Component.text(finalKickMessage)));
        },
        "ProfileKickPlayerProfileDisabled", "ProfileKickPlayerProfileOtherLogin");
  }
}
