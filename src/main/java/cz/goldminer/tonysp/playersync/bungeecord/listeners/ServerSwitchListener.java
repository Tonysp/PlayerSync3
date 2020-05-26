package cz.goldminer.tonysp.playersync.bungeecord.listeners;

import cz.goldminer.tonysp.playersync.bungeecord.PlayerSyncBungee;
import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ServerSwitchListener implements Listener {

    @EventHandler
    public void onServerSwitchEvent (ServerSwitchEvent event) {
        String playerName = event.getPlayer().getName();
        if (AuthenticationManager.getInstance().isSetAsOnline(playerName)) {
            AuthenticationManager.getInstance().serverSwitched(playerName);
        }
        AuthenticationManager.getInstance().setAsOnline(playerName);
    }
}