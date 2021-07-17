package de.blu.profilemanager.util.signgui;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SignGUI {

  private static final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
  private static PacketAdapter packetListener;
  private static final Map<UUID, SignGUIListener> listeners = new ConcurrentHashMap<>();
  private static final Map<UUID, Vector> signLocations = new ConcurrentHashMap<>();

  public static void init(JavaPlugin plugin) {
    ProtocolLibrary.getProtocolManager()
        .addPacketListener(
            packetListener =
                new PacketAdapter(plugin, PacketType.Play.Client.UPDATE_SIGN) {
                  @Override
                  public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    PacketContainer packet = event.getPacket();

                    Vector v = signLocations.remove(player.getUniqueId());
                    if (v == null) return;
                    final BlockPosition position = packet.getBlockPositionModifier().read(0);

                    if (position.getX() != v.getBlockX()) return;
                    if (position.getY() != v.getBlockY()) return;
                    if (position.getZ() != v.getBlockZ()) return;

                    player.sendBlockChange(position.toLocation(player.getLocation().getWorld()), Material.AIR.createBlockData());

                    final String[] lines = packet.getStringArrays().getValues().get(0);
                    final SignGUIListener response =
                        listeners.remove(event.getPlayer().getUniqueId());
                    if (response != null) {
                      event.setCancelled(true);
                      Bukkit.getScheduler()
                          .scheduleSyncDelayedTask(plugin, () -> response.onSignDone(lines));
                    }
                  }
                });
  }

  public static void destroy() {
    protocolManager.removePacketListener(packetListener);
    listeners.clear();
    signLocations.clear();
  }

  public interface SignGUIListener {
    void onSignDone(String[] lines);
  }

  public static void open(Player player, SignGUIListener response) {
    SignGUI.open(player, new String[4], response);
  }

  public static void open(Player player, String[] defaultLines, SignGUIListener response) {
    List<PacketContainer> packets = new ArrayList<>();
    int x = player.getLocation().getBlockX();
    int y = 255;
    int z = player.getLocation().getBlockZ();
    BlockPosition blockPosition = new BlockPosition(x, y, z);

    player.sendBlockChange(
        blockPosition.toLocation(player.getLocation().getWorld()),
        Material.OAK_SIGN.createBlockData());

    PacketContainer openSign =
        ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
    openSign.getBlockPositionModifier().write(0, blockPosition);

    PacketContainer signData =
        ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);

    NbtCompound signNBT = (NbtCompound) signData.getNbtModifier().read(0);

    for (int line = 0; line < 4; line++) {
      String lineText = defaultLines.length > line ? ChatColor.translateAlternateColorCodes('&', defaultLines[line]) : "";

      signNBT.put(
          "Text" + (line + 1),
          defaultLines.length > line
              ? String.format(
                  "{\\\"text\\\":\\\"%s\\\"}",
                  lineText)
              : "");
    }

    signNBT.put("x", blockPosition.getX());
    signNBT.put("y", blockPosition.getY());
    signNBT.put("z", blockPosition.getZ());
    signNBT.put("id", "minecraft:sign");

    signData.getBlockPositionModifier().write(0, blockPosition);
    signData.getIntegers().write(0, 9);
    signData.getNbtModifier().write(0, signNBT);

    player.sendSignChange(blockPosition.toLocation(player.getWorld()), defaultLines);

    //packets.add(signData);
    packets.add(openSign);

    try {
      signLocations.put(player.getUniqueId(), new Vector(x, y, z));
      listeners.put(player.getUniqueId(), response);

      for (PacketContainer packet : packets) {
        protocolManager.sendServerPacket(player, packet);
      }
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }
}
