package cz.goldminer.tonysp.playersync.bukkit.listeners;

import cz.goldminer.tonysp.playersync.bukkit.PlayerSyncBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class AsyncPlayerChatListener implements Listener {

    private final PlayerSyncBukkit plugin;

    public AsyncPlayerChatListener(PlayerSyncBukkit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChatEvent (AsyncPlayerChatEvent event) {
        if (plugin.isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}