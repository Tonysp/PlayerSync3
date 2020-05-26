package cz.goldminer.tonysp.playersync.bungeecord.listeners;

import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerDisconnectListener implements Listener {

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        AuthenticationManager.getInstance().playerDisconnected(event.getPlayer().getName());
    }
}
