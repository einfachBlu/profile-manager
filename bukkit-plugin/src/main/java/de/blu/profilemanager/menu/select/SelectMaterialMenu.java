package de.blu.profilemanager.menu.select;

import de.blu.itemstackbuilder.builder.ItemStackBuilder;
import de.blu.localize.LocalizeAPI;
import de.blu.profilemanager.menu.Menu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class SelectMaterialMenu extends Menu {

  @Inject private LocalizeAPI localizeAPI;

  private Consumer<Material> materialCallback;

  private Material selectedMaterial = null;

  private int currentPage = 1;
  private int pages = 1;
  private int contentRows = 4;

  @Inject
  private SelectMaterialMenu(JavaPlugin plugin) {
    super(plugin);
  }

  public void init(Player player, String title, Consumer<Material> materialCallback) {
    this.player = player;
    this.size = (9 * this.contentRows) + 18;
    this.title = title;
    this.materialCallback = materialCallback;

    this.pages = (int) Math.round((Material.values().length / (this.contentRows * 9)) + 0.5);
  }

  public void open() {
    this.open(this.player);
  }

  @Override
  public void open(Player player) {
    super.open(player);

    // Load Menu
    this.updateContent();
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

    ItemStack placeHolder =
        ItemStackBuilder.defaults().placeHolderGlass(Material.BLACK_STAINED_GLASS_PANE).build();
    for (int slot = this.size - 18; slot < this.size - 9; slot++) {
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
          new ItemStackBuilder().type(Material.ARROW).displayName(this.localizeAPI.getMessage(this.player.getUniqueId(), "profile-menu-select-material-previous-page-title")).build();
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
          new ItemStackBuilder().type(Material.ARROW).displayName(this.localizeAPI.getMessage(this.player.getUniqueId(), "profile-menu-select-material-next-page-title")).build();
      this.addClickableItem(
          this.getSize() - 1,
          nextPageItemStack,
          e -> {
            this.currentPage++;
            this.updateContent();
            this.player.playSound(this.player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 10, 1);
          });
    }

    List<Material> materials =
        Arrays.stream(Material.values())
            .skip((this.currentPage - 1) * (this.contentRows * 9L))
            .limit(this.contentRows * 9L)
            .collect(Collectors.toList());

    // Materials on this page
    for (int i = 0; i < this.contentRows * 9; i++) {
      if (i >= materials.size()) {
        break;
      }

      Material material = materials.get(i);
      this.addClickableItem(
          i,
          new ItemStack(material),
          e -> {
            this.selectedMaterial = material;
            e.setCancelled(true);
            new BukkitRunnable() {
              @Override
              public void run() {
                player.closeInventory();
              }
            }.runTaskLater(this.getPlugin(), 1);
          });
    }
  }

  @Override
  protected void onMenuClick(InventoryClickEvent e) {
    e.setCancelled(true);
  }

  @Override
  protected void onMenuClose(InventoryCloseEvent e) {
    this.materialCallback.accept(this.selectedMaterial);
  }
}
