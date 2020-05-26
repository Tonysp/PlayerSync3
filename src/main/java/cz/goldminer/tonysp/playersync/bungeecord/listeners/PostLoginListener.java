package cz.goldminer.tonysp.playersync.bungeecord.listeners;

import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PostLoginListener implements Listener {

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        String playerName = event.getPlayer().getName();
        String address = AuthenticationManager.getInstance().ipToString(event.getPlayer());
        AuthenticationManager.getInstance().loadPlayerAuthData(playerName, address);
    }
}
