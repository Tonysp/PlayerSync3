package cz.goldminer.tonysp.playersync.bukkit.listeners;

import cz.goldminer.tonysp.playersync.bukkit.PlayerSyncBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class AsyncPlayerPreLoginListener implements Listener {

    private final PlayerSyncBukkit plugin;

    public AsyncPlayerPreLoginListener (PlayerSyncBukkit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLoginEvent (AsyncPlayerPreLoginEvent event) {
        if (plugin.SERVER_SHUTTING_DOWN) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.setKickMessage("Server se vypina...");
        }
    }
}
