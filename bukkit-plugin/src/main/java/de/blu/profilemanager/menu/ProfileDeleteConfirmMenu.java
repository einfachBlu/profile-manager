package de.blu.profilemanager.menu;

import com.google.inject.Injector;
import de.blu.itemstackbuilder.builder.ItemStackBuilder;
import de.blu.localize.LocalizeAPI;
import de.blu.localize.data.Locale;
import de.blu.profilemanager.ProfileManager;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.util.InventoryHelper;
import de.blu.profilemanager.util.SchedulerHelper;
import de.blu.profilesystem.data.Profile;
import de.blu.profilesystem.exception.ServiceUnreachableException;
import de.blu.profilesystem.util.ProfileWebRequester;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;

public final class ProfileDeleteConfirmMenu extends Menu {

  @Inject private MainConfig mainConfig;
  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private ExecutorService executorService;
  @Inject private LocalizeAPI localizeAPI;
  @Inject private Injector injector;

  @Setter private Profile profile;
  private int secondsLeft = 3;
  private BukkitTask task;

  @Inject
  private ProfileDeleteConfirmMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void open(Player player) {
    this.size = 9 * 3;
    this.title =
        this.localizeAPI.getMessage(
            player.getUniqueId(), "profile-menu-delete-confirm-name", this.secondsLeft);

    super.open(player);

    // Load Menu
    this.updateContent();

    // Start Update Task
    this.task =
        new BukkitRunnable() {
          @Override
          public void run() {
            if (--secondsLeft <= 0) {
              this.cancel();
            }

            updateTitle();
          }
        }.runTaskTimer(ProfileManager.getInstance(), 20, 20);
  }

  private void updateContent() {
    this.getInventory().clear();
    this.getSlotClickEvents().clear();

    ItemStack placeHolder =
        ItemStackBuilder.defaults().placeHolderGlass(Material.BLACK_STAINED_GLASS_PANE).build();
    InventoryHelper.fill(this.getInventory(), placeHolder);

    Locale locale = this.localizeAPI.getLocale(this.getPlayer().getUniqueId());

    this.addClickableItem(
        this.getSize() - 9 - 7,
        ItemStackBuilder.normal(Material.RED_STAINED_GLASS)
            .displayName(
                this.localizeAPI.getMessage(locale, "profile-menu-delete-confirm-confirm-title"))
            .lore(
                this.localizeAPI
                    .getMessage(locale, "profile-menu-delete-confirm-confirm-lore")
                    .split("\n"))
            .build(),
        e -> {
          if (this.secondsLeft > 0) {
            return;
          }

          this.confirm();
        });

    this.addClickableItem(
        this.getSize() - 9 - 3,
        ItemStackBuilder.normal(Material.RED_WOOL)
            .displayName(
                this.localizeAPI.getMessage(locale, "profile-menu-delete-confirm-cancel-title"))
            .lore(
                this.localizeAPI
                    .getMessage(locale, "profile-menu-delete-confirm-cancel-lore")
                    .split("\n"))
            .build(),
        e -> {
          this.cancel();
        });
  }

  private void updateTitle() {
    if (this.secondsLeft > 0) {
      this.title =
          this.localizeAPI.getMessage(
              player.getUniqueId(), "profile-menu-delete-confirm-name", this.secondsLeft);
    } else {
      this.title =
          this.localizeAPI.getMessage(
              player.getUniqueId(), "profile-menu-delete-confirm-final-name");
    }

    this.recreate();
    this.player.openInventory(this.getInventory());
  }

  private void confirm() {
    this.deleteProfile(this::backToMainMenu);
  }

  private void cancel() {
    this.backToMainMenu();
  }

  private void backToMainMenu() {
    SchedulerHelper.runSync(
        () -> {
          this.getPlayer().closeInventory();
          this.getPlayer().chat("/profiles");
        });
  }

  private void deleteProfile(Runnable doneCallback) {
    this.executorService.submit(
        () -> {
          try {
            this.profileWebRequester.deleteProfile(
                this.mainConfig.getServiceUrl(), this.profile.getId());
          } catch (ServiceUnreachableException serviceUnreachableException) {
            this.player.sendMessage(
                this.localizeAPI.getMessage(
                    this.player.getUniqueId(), "profile-service-unreachable"));
          }

          doneCallback.run();
        });
  }

  @Override
  protected void onMenuClick(InventoryClickEvent e) {
    e.setCancelled(true);
  }

  @Override
  protected void onMenuClose(InventoryCloseEvent e) {
    if (this.task == null) {
      return;
    }

    this.task.cancel();
    this.task = null;
  }
}
