package de.blu.profilemanager.menu;

import com.google.gson.JsonObject;
import com.google.inject.Injector;
import de.blu.itemstackbuilder.builder.ItemStackBuilder;
import de.blu.localize.LocalizeAPI;
import de.blu.localize.converter.PeriodNumber;
import de.blu.localize.data.Locale;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.util.InventoryHelper;
import de.blu.profilemanager.util.SchedulerHelper;
import de.blu.profilemanager.util.signgui.SignGUI;
import de.blu.profilesystem.data.PlayTime;
import de.blu.profilesystem.data.Profile;
import de.blu.profilesystem.exception.ServiceUnreachableException;
import de.blu.profilesystem.util.ProfileWebRequester;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ProfilesMainMenu extends Menu {

  @Inject private MainConfig mainConfig;
  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private ExecutorService executorService;
  @Inject private LocalizeAPI localizeAPI;
  @Inject private Injector injector;

  @Inject
  private ProfilesMainMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void open(Player player) {
    this.size = 9 * 5;
    this.title = "Profile Management";

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

    this.getPlayerProfiles(
        profiles -> {
          if (profiles == null) {
            return;
          }

          int startY = 1;
          int endY = 2;
          int startX = 2;
          int endX = 7;

          this.addClickableItem(
              this.getSize() - 5,
              ItemStackBuilder.normal(Material.ARROW)
                  .displayName(
                      this.localizeAPI.getMessage(locale, "profile-menu-main-go-back-title"))
                  .lore(
                      this.localizeAPI
                          .getMessage(locale, "profile-menu-main-go-back-lore")
                          .split("\n"))
                  .build(),
              e -> {
                this.getPlayer().closeInventory();
                this.getPlayer().chat("/menu");
              });

          int y = startY;
          int x = startX - 1;
          for (int i = 0; i < 10; i++) {
            // Increment Slot
            if (++x >= endX) {
              y++;
              x = startX;
            }

            if (y > endY) {
              continue;
            }

            int slot = y * 9 + x;

            // Show Button

            // Check for permissions
            if (!this.canUse(this.player, i + 1)) {
              this.addLockedItem(slot);
              continue;
            }

            if (i < profiles.size()) {
              Profile profile = profiles.get(i);

              // Existing Profile
              boolean loggedIn = false;
              try {
                Profile currentProfile =
                    this.profileWebRequester.getCurrentProfile(
                        this.mainConfig.getServiceUrl(), this.player.getUniqueId());

                if (currentProfile != null) {
                  loggedIn = currentProfile.getId().equals(profile.getId());
                }
              } catch (ServiceUnreachableException e) {
                e.printStackTrace();
              }
              this.addExistingItem(slot, locale, profile, loggedIn);
            } else {
              // Empty Profile Slot
              this.addEmptyItem(slot, locale);
            }
          }
        });

    if (!this.player.hasPermission("profilemanager.admin")) {
      return;
    }

    this.addClickableItem(
        this.getSize() - 9,
        ItemStackBuilder.normal(Material.DIAMOND_HELMET)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-main-admin-title"))
            .lore(this.localizeAPI.getMessage(locale, "profile-menu-main-admin-lore").split("\n"))
            .build(),
        e -> {
          // Open Admin Menu
          ProfilesAdminMenu profilesAdminMenu = this.injector.getInstance(ProfilesAdminMenu.class);
          this.player.closeInventory();
          profilesAdminMenu.open(this.player);
        });
  }

  private void addLockedItem(int slot) {
    ItemStack itemStack =
        ItemStackBuilder.normal(Material.BARRIER)
            .displayName(
                this.localizeAPI.getMessage(
                    this.player.getUniqueId(), "profile-menu-main-locked-slot-title"))
                .lore(this.localizeAPI.getMessage(this.player.getUniqueId(), "profile-menu-main-locked-slot-lore").split("\n"))
            .build();
    this.addClickableItem(slot, itemStack, e -> {});
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

  private void addEmptyItem(int slot, Locale locale) {
    ItemStack itemStack =
        ItemStackBuilder.normal(Material.OAK_BUTTON)
            .displayName(this.localizeAPI.getMessage(locale, "profile-menu-main-empty-slot-title"))
            .lore(
                this.localizeAPI
                    .getMessage(locale, "profile-menu-main-empty-slot-lore")
                    .split("\n"))
            .build();

    this.addClickableItem(
        slot,
        itemStack,
        e -> {
          this.askForProfileName(
              locale,
              profileName -> {
                if (profileName.isEmpty()) {
                  this.open(this.player);
                  return;
                }

                // Create Profile
                try {
                  Profile profile =
                      this.profileWebRequester.createProfile(
                          this.mainConfig.getServiceUrl(), this.player.getUniqueId(), profileName);

                  if (profile == null) {
                    this.player.sendMessage(
                        "#Something went wrong with creating the Profile. Please try again");
                    return;
                  }
                } catch (ServiceUnreachableException serviceUnreachableException) {
                  this.player.sendMessage(
                      this.localizeAPI.getMessage(
                          this.player.getUniqueId(), "profile-service-unreachable"));
                }

                this.open(this.player);
              });
        });
  }

  private void addExistingItem(
      int slot, Locale locale, Profile profile, boolean currentlyLoggedIn) {
    long playTimeMillis = 0;
    for (PlayTime playTime : profile.getPlayTimes()) {
      playTimeMillis += playTime.duration();
    }

    String balance = "#" + new PeriodNumber(50125000) + " $";
    Date createdAt = new Date(profile.getCreationTime());
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    long daysAge = Duration.between(createdAt.toInstant(), new Date().toInstant()).toDays();

    long seconds = TimeUnit.MILLISECONDS.toSeconds(playTimeMillis);
    long minutes = TimeUnit.SECONDS.toMinutes(seconds);
    long hours = TimeUnit.MINUTES.toHours(minutes);

    String playTime = hours + "h";

    Material material = Material.GRASS;
    if (profile.getData().containsKey("profile")) {
      JsonObject jsonObject = profile.getData().get("profile");
      if (jsonObject.has("iconMaterial")) {
        material = Material.valueOf(jsonObject.get("iconMaterial").getAsString());
      }
    }

    ItemStackBuilder itemBuilder =
        ItemStackBuilder.normal(material)
            .displayName(
                this.localizeAPI.getMessage(
                    locale, "profile-menu-main-existing-profile-title", profile.getName()))
            .lore(
                this.localizeAPI
                    .getMessage(
                        locale,
                        "profile-menu-main-existing-profile"
                            + (currentlyLoggedIn ? "-current" : "")
                            + "-lore",
                        balance,
                        simpleDateFormat.format(createdAt),
                        daysAge,
                        playTime)
                    .split("\n"));

    if (currentlyLoggedIn) {
      itemBuilder.glow();
    }

    ItemStack itemStack = itemBuilder.build();
    this.addClickableItem(
        slot,
        itemStack,
        e -> {
          if (currentlyLoggedIn) {
            return;
          }

          // Manage
          ProfileEditMenu profileEditMenu = this.injector.getInstance(ProfileEditMenu.class);
          profileEditMenu.setProfile(profile);
          profileEditMenu.open(this.player);
          this.player.closeInventory();
        });
  }

  private boolean canUse(Player player, int amount) {
    Map<String, Integer> permissionLimits = new HashMap<>();
    permissionLimits.put("profile.amount.premium", 4);
    permissionLimits.put("profile.amount.vip", 5);
    permissionLimits.put("profile.amount.team", 10);

    int limit = permissionLimits.values().stream().min(Integer::compareTo).orElse(2) - 1;

    for (Map.Entry<String, Integer> entry :
        permissionLimits.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList())) {

      boolean hasPermission = player.hasPermission(entry.getKey());
      if (!hasPermission) {
        continue;
      }

      limit = entry.getValue();
    }

    return amount <= limit;
  }

  private void getPlayerProfiles(Consumer<List<Profile>> profilesCallback) {
    this.executorService.submit(
        () -> {
          try {
            List<Profile> profiles =
                this.profileWebRequester.getProfilesByPlayer(
                    this.mainConfig.getServiceUrl(), this.player.getUniqueId());
            profilesCallback.accept(
                profiles.stream()
                    .filter(profile -> !profile.isDisabled())
                    .collect(Collectors.toList()));
          } catch (ServiceUnreachableException e) {
            this.player.sendMessage(
                this.localizeAPI.getMessage(
                    this.player.getUniqueId(), "profile-service-unreachable"));
            profilesCallback.accept(null);
            SchedulerHelper.runSync(
                () -> this.player.closeInventory(InventoryCloseEvent.Reason.PLUGIN));
          }
        });
  }

  @Override
  protected void onMenuClick(InventoryClickEvent e) {
    e.setCancelled(true);
  }
}
