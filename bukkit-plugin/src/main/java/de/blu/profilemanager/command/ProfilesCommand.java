package de.blu.profilemanager.command;

import com.google.inject.Injector;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.menu.ProfilesMainMenu;
import de.blu.profilesystem.util.ProfileWebRequester;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;

@Singleton
public final class ProfilesCommand implements CommandExecutor {

  @Inject private MainConfig mainConfig;
  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private ExecutorService executorService;
  @Inject private Injector injector;

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player)) {
      return false;
    }

    Player player = (Player) sender;
    this.injector.getInstance(ProfilesMainMenu.class).open(player);
    return true;
  }
}
