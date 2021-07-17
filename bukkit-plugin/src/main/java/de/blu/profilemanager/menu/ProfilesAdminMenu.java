package de.blu.profilemanager.menu;

import com.google.gson.JsonObject;
import com.google.inject.Injector;
import de.blu.itemstackbuilder.builder.ItemStackBuilder;
import de.blu.localize.LocalizeAPI;
import de.blu.localize.converter.PeriodNumber;
import de.blu.profilemanager.config.MainConfig;
import de.blu.profilemanager.util.SchedulerHelper;
import de.blu.profilemanager.util.signgui.SignGUI;
import de.blu.profilesystem.data.PlayTime;
import de.blu.profilesystem.data.Profile;
import de.blu.profilesystem.exception.ServiceUnreachableException;
import de.blu.profilesystem.util.ProfileWebRequester;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ProfilesAdminMenu extends Menu {

  @Inject private LocalizeAPI localizeAPI;
  @Inject private Injector injector;

  private int currentPage = 1;
  private int pages = 1;
  private int startX = 2;
  private int endX = 8;
  private int startY = 0;
  private int endY = 3;
  private int contentRows = endY - startY + 1;
  private int elementsPerRow = endX - startX + 1;

  // Filter
  private String nameFilter = "";

  @Inject private MainConfig mainConfig;
  @Inject private ProfileWebRequester profileWebRequester;
  @Inject private ExecutorService executorService;

  private List<Profile> profiles = new ArrayList<>();

  @Inject
  private ProfilesAdminMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void open(Player player) {
    this.size = (9 * this.contentRows) + 18;
    this.title = "Profiles (Admin)";

    super.open(player);

    // Load all Profiles
    this.loadProfiles();
  }

  private void loadProfiles() {
    this.profiles.clear();

    this.executorService.submit(
        () -> {
          try {
            List<Profile> profiles =
                this.profileWebRequester.getProfiles(this.mainConfig.getServiceUrl());

            if (!this.nameFilter.isEmpty()) {
              profiles =
                  profiles.stream()
                      .filter(
                          profile ->
                              profile
                                  .getName()
                                  .toLowerCase()
                                  .contains(this.nameFilter.toLowerCase()))
                      .collect(Collectors.toList());
            }

            this.profiles.addAll(profiles);
            this.pages = (int) Math.round((this.profiles.size() / (this.contentRows * 9)) + 0.5);
          } catch (ServiceUnreachableException e) {
            this.player.sendMessage(
                "§cThe Profile-Service is currently unavailable. Please try again later.");
            SchedulerHelper.runSync(() -> this.player.closeInventory());
          }

          this.updateContent();
        });
  }

  private void updateContent() {
    if (this.currentPage < 1) {
      this.currentPage = 1;
    }

    if (this.currentPage > this.pages) {
      this.currentPage = this.pages;
    }

    this.getInventory().clear();
    this.getSlotClickEvents().clear();

    // Placeholder
    ItemStack placeHolder =
        ItemStackBuilder.defaults().placeHolderGlass(Material.BLACK_STAINED_GLASS_PANE).build();

    // Horizontal Placeholder
    for (int x = this.startX - 1; x <= this.endX; x++) {
      int y = this.endY + 1;
      int slot = (y * 9) + x;
      this.getInventory().setItem(slot, placeHolder);
    }

    // Vertical Placeholder
    for (int y = this.startY; y <= this.endY + 1; y++) {
      int x = this.startX - 1;
      int slot = (y * 9) + x;
      this.getInventory().setItem(slot, placeHolder);
    }

    // Current Page
    ItemStack currentPageItemStack =
        new ItemStackBuilder()
            .type(Material.PAPER)
            .displayName("§e" + this.currentPage + " / " + this.pages)
            .build();
    this.getInventory().setItem(this.getSize() - 5, currentPageItemStack);

    // Previous Page
    if (this.currentPage > 1) {
      ItemStack previousPageItemStack =
          new ItemStackBuilder()
              .type(Material.ARROW)
              .displayName(
                  this.localizeAPI.getMessage(
                      this.player.getUniqueId(), "profile-menu-admin-previous-page-title"))
              .build();
      this.addClickableItem(
          this.getSize() - 9,
          previousPageItemStack,
          e -> {
            this.currentPage--;
            this.updateContent();
            this.player.playSound(this.player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 10, 1);
          });
    }

    // Next Page
    if (this.currentPage < this.pages) {
      ItemStack nextPageItemStack =
          new ItemStackBuilder()
              .type(Material.ARROW)
              .displayName(
                  this.localizeAPI.getMessage(
                      this.player.getUniqueId(), "profile-menu-admin-next-page-title"))
              .build();
      this.addClickableItem(
          this.getSize() - 1,
          nextPageItemStack,
          e -> {
            this.currentPage++;
            this.updateContent();
            this.player.playSound(this.player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 10, 1);
          });
    }

    // Filter by Name
    ItemStack nameFilterItemStack =
        new ItemStackBuilder()
            .type(Material.OAK_SIGN)
            .displayName(
                this.localizeAPI.getMessage(
                    this.player.getUniqueId(), "profile-menu-admin-search-name-title"))
            .lore(
                this.localizeAPI
                    .getMessage(
                        this.player.getUniqueId(),
                        "profile-menu-admin-search-name-lore",
                        this.nameFilter.isEmpty() ? "-" : this.nameFilter)
                    .split("\n"))
            .build();
    this.addClickableItem(
        this.startX - 2,
        nameFilterItemStack,
        e -> {
          if (e.getClick().equals(ClickType.RIGHT) || e.getClick().equals(ClickType.SHIFT_RIGHT)) {
            // Reset Filter
            this.nameFilter = "";
            this.player.playSound(this.player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 10, 1);
            this.loadProfiles();
            return;
          }

          // Change Filter
          this.askForNameFilter(
              nameFilter -> {
                this.nameFilter = nameFilter;
                this.loadProfiles();

                // Reopen this
                this.player.openInventory(this.getInventory());
                this.player.playSound(this.player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 10, 1);
              });
        });

    // Profiles on this page
    List<Profile> displayProfiles =
        this.profiles.stream()
            .skip((this.currentPage - 1) * ((long) this.contentRows * this.elementsPerRow))
            .limit((long) this.contentRows * this.elementsPerRow)
            .collect(Collectors.toList());

    int x = this.startX;
    int y = this.startY;
    for (Profile profile : displayProfiles) {
      int slot = (y * 9) + x;

      this.addProfileItem(slot, profile);
      x++;

      if (x > this.endX) {
        y++;
        x = this.startX;
      }

      if (y > this.endY) {
        break;
      }
    }
  }

  private void askForNameFilter(Consumer<String> nameFilter) {
    SignGUI.open(
        this.player,
        new String[] {
          "",
          "",
          "^^^^^^^^^^^^^^^",
          this.localizeAPI.getMessage(
              this.player.getUniqueId(), "profile-menu-main-create-name-input-header")
        },
        lines -> {
          String profileName =
              (lines[0] + lines[1]).replaceAll(" ", "").replaceAll("[^a-zA-Z0-9]+", "");
          nameFilter.accept(profileName);
        });
  }

  private void addProfileItem(int slot, Profile profile) {
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

    boolean currentlyLoggedIn = profile.getLoggedInPlayerId() != null;

    ItemStackBuilder itemBuilder =
        ItemStackBuilder.normal(material)
            .displayName(
                this.localizeAPI.getMessage(
                    this.player.getUniqueId(),
                    "profile-menu-main-existing-profile-title",
                    profile.getName()))
            .lore(
                this.localizeAPI
                    .getMessage(
                        this.player.getUniqueId(),
                        "profile-menu-main-existing-profile-lore",
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
          // Manage
          ProfileEditMenu profileEditMenu = this.injector.getInstance(ProfileEditMenu.class);
          profileEditMenu.setProfile(profile);
          profileEditMenu.open(this.player);
          this.player.closeInventory();
        });
  }

  @Override
  protected void onMenuClick(InventoryClickEvent e) {
    e.setCancelled(true);
  }
}
