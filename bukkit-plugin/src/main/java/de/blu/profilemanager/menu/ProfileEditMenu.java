package de.blu.profilemanager.menu;

import com.google.gson.JsonObject;
import com.google.inject.Injector;
import de.blu.database.storage.redis.RedisConnection;
import de.blu.itemstackbuilder.builder.ItemStackBuilder;
import de.blu.localize.LocalizeAPI;
import de.blu.localize.data.Locale;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.event.ProfileLoginEvent;
import de.blu.profilemanager.event.ProfileLogoutEvent;
import de.blu.profilemanager.menu.select.SelectMaterialMenu;
import de.blu.profilemanager.util.InventoryHelper;
import de.blu.profilemanager.util.SchedulerHelper;
import de.blu.profilemanager.util.signgui.SignGUI;
import de.blu.profilesystem.data.Profile;
import de.blu.profilesystem.exception.ServiceUnreachableException;
import de.blu.profilesystem.util.ProfileWebRequester;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class ProfileEditMenu extends Menu {

  @Inject private MainConfig mainConfig;
  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private ExecutorService executorService;
  @Inject private LocalizeAPI localizeAPI;
  @Inject private Injector injector;
  @Inject private RedisConnection redisConnection;

  @Setter private Profile profile;

  @Inject
  private ProfileEditMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void open(Player player) {
    this.size = 9 * 4;
    this.title = "Profile: " + this.profile.getName();

    super.open(player);

    // Load Menu
    this.updateContent();
  }

  private void updateContent() {
    this.getInventory().clear();
    this.getSlotClickEvents().clear();

    ItemStack placeHolder =
        ItemStackBuilder.defaults().placeHolderGlass(Material.BLACK_STAINED_GLASS_PANE).build();
    InventoryHelper.fill(this.getInventory(), placeHolder);

    Locale locale = this.localizeAPI.getLocale(this.getPlayer().getUniqueId());

    this.addClickableItem(
        this.getSize() - 5,
        ItemStackBuilder.normal(Material.ARROW)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-edit-go-back-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-edit-go-back-lore").split("\n"))
            .build(),
        e -> {
          this.getPlayer().closeInventory();
          this.getPlayer().chat("/profiles");
        });

    this.addClickableItem(
        this.getSize() - 18 - 8,
        ItemStackBuilder.normal(Material.ENDER_EYE)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-edit-login-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-edit-login-lore").split("\n"))
            .build(),
        e -> {
          try {
            Profile currentProfile =
                this.profileWebRequester.getCurrentProfile(
                    this.mainConfig.getServiceUrl(), this.player.getUniqueId());

            // Ignore if already logged in to this profile
            if (currentProfile != null && currentProfile.getId().equals(this.profile.getId())) {
              return;
            }

            // Prevent if someone else is logged in
            if (this.profile.getLoggedInPlayerId() != null
                && !this.player.hasPermission("profilemanager.admin")) {
              return;
            }

            if (currentProfile != null) {
              this.profileWebRequester.logout(this.mainConfig.getServiceUrl(), currentProfile);
              Bukkit.getServer()
                  .getPluginManager()
                  .callEvent(new ProfileLogoutEvent(this.player, currentProfile));
            }

            // Kick out player if admin is logging in
            if (this.profile.getLoggedInPlayerId() != null
                && this.player.hasPermission("profilemanager.admin")) {
              this.redisConnection.publish(
                  "ProfileKickPlayerProfileOtherLogin",
                  this.profile.getLoggedInPlayerId().toString());
            }

            this.profileWebRequester.login(
                this.mainConfig.getServiceUrl(), this.player.getUniqueId(), this.profile);
            Bukkit.getServer()
                .getPluginManager()
                .callEvent(new ProfileLoginEvent(this.player, this.profile));
            this.getPlayer().closeInventory();
            this.getPlayer().chat("/profiles");
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }
        });

    this.addClickableItem(
        this.getSize() - 18 - 6,
        ItemStackBuilder.normal(Material.NAME_TAG)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-edit-rename-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-edit-rename-lore").split("\n"))
            .build(),
        e -> {
          try {
            Profile profile =
                this.profileWebRequester.getProfileById(
                    this.mainConfig.getServiceUrl(), this.profile.getId());
            this.askForProfileName(
                locale,
                profileName -> {
                  profile.setName(profileName);
                  try {
                    this.profileWebRequester.updateProfile(
                        this.mainConfig.getServiceUrl(), profile);
                  } catch (ServiceUnreachableException serviceUnreachableException) {
                    serviceUnreachableException.printStackTrace();
                  }

                  this.getPlayer().closeInventory();
                  this.getPlayer().chat("/profiles");
                });
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }
        });

    this.addClickableItem(
        this.getSize() - 18 - 4,
        ItemStackBuilder.normal(Material.MAP)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-edit-icon-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-edit-icon-lore").split("\n"))
            .build(),
        e -> {
          try {
            Profile profile =
                this.profileWebRequester.getProfileById(
                    this.mainConfig.getServiceUrl(), this.profile.getId());
            this.askForMaterial(
                this.player,
                material -> {
                  if (material == null) {
                    this.getPlayer().chat("/profiles");
                    return;
                  }

                  this.executorService.submit(
                      () -> {
                        if (!profile.getData().containsKey("profile")) {
                          profile.getData().put("profile", new JsonObject());
                        }

                        JsonObject jsonObject = profile.getData().get("profile");

                        // Set new Icon
                        jsonObject.addProperty("iconMaterial", material.name());
                        try {
                          this.profileWebRequester.updateProfile(
                              this.mainConfig.getServiceUrl(), profile);
                        } catch (ServiceUnreachableException serviceUnreachableException) {
                          serviceUnreachableException.printStackTrace();
                        }

                        SchedulerHelper.runSync(() -> this.getPlayer().chat("/profiles"));
                      });
                });
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }
        });

    this.addClickableItem(
        this.getSize() - 18 - 2,
        ItemStackBuilder.normal(Material.RED_STAINED_GLASS)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-edit-delete-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-edit-delete-lore").split("\n"))
            .build(),
        e -> {
          ProfileDeleteConfirmMenu profileDeleteConfirmMenu =
              this.injector.getInstance(ProfileDeleteConfirmMenu.class);
          profileDeleteConfirmMenu.setProfile(profile);
          profileDeleteConfirmMenu.open(this.player);
        });

    if (!this.player.hasPermission("profilemanager.admin")) {
      return;
    }

    this.addClickableItem(
        this.getSize() - 9,
        ItemStackBuilder.normal(Material.RED_WOOL)
            .displayName(
                this.localizeAPI.getMessage(
                    locale,
                    !this.profile.isDisabled()
                        ? "profile-menu-edit-disable-title"
                        : "profile-menu-edit-enable-title"))
            .lore(
                this.localizeAPI
                    .getMessage(
                        locale,
                        !this.profile.isDisabled()
                            ? "profile-menu-edit-disable-lore"
                            : "profile-menu-edit-enable-lore")
                    .split("\n"))
            .build(),
        e -> {
          try {
            Profile profile =
                this.profileWebRequester.getProfileById(
                    this.mainConfig.getServiceUrl(), this.profile.getId());
            profile.setDisabled(!profile.isDisabled());
            this.profileWebRequester.updateProfile(this.mainConfig.getServiceUrl(), profile);

            this.profile = profile;
            if (profile.getLoggedInPlayerId() != null) {
              this.redisConnection.publish(
                  "ProfileKickPlayerProfileDisabled", profile.getLoggedInPlayerId().toString());
            }
          } catch (ServiceUnreachableException serviceUnreachableException) {
            serviceUnreachableException.printStackTrace();
          }

          this.updateContent();
        });
  }

  private void askForProfileName(Locale locale, Consumer<String> profileNameCallback) {
    SignGUI.open(
        this.player,
        new String[] {
          "",
          "",
          "^^^^^^^^^^^^^^^",
          this.localizeAPI.getMessage(locale, "profile-menu-main-create-name-input-header")
        },
        lines -> {
          String profileName =
              (lines[0] + lines[1]).replaceAll(" ", "").replaceAll("[^a-zA-Z0-9]+", "");
          if (profileName.length() > 16) {
            profileName = profileName.substring(0, 16);
          }

          profileNameCallback.accept(profileName);
        });
  }

  private void askForMaterial(Player player, Consumer<Material> materialCallback) {
    String title =
        this.localizeAPI.getMessage(player.getUniqueId(), "profile-menu-edit-select-icon-title");

    SelectMaterialMenu selectMaterialMenu = this.injector.getInstance(SelectMaterialMenu.class);
    selectMaterialMenu.init(this.player, title, materialCallback);
    selectMaterialMenu.open();
  }

  @Override
  protected void onMenuClick(InventoryClickEvent e) {
    e.setCancelled(true);
  }
}
