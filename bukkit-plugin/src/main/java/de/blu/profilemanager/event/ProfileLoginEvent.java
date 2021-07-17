package de.blu.profilemanager.event;

import de.blu.profilesystem.data.Profile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
@Setter
@AllArgsConstructor
public final class ProfileLoginEvent extends Event {
  private static final HandlerList handlers = new HandlerList();

  private Player player;
  private Profile profile;

  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }
}
