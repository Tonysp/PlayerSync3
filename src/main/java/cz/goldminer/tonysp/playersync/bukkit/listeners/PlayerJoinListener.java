package cz.goldminer.tonysp.playersync.bukkit.listeners;

import cz.goldminer.tonysp.playersync.bukkit.PlayerSyncBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final PlayerSyncBukkit plugin;

    public PlayerJoinListener (PlayerSyncBukkit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoinEvent (PlayerJoinEvent event) {
        plugin.playerJoined(event.getPlayer());
    }
}