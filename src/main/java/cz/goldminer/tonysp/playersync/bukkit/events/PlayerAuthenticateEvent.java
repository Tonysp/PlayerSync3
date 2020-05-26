package cz.goldminer.tonysp.playersync.bukkit.events;

import org.bukkit.entity.Player;
import org.bukkit.event.*;

public class PlayerAuthenticateEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private Player player;

    public PlayerAuthenticateEvent (Player player) {
        this.player = player;
    }

    public Player getPlayer () {
        return player;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

