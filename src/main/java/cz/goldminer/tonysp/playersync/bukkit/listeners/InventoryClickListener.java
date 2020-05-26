package cz.goldminer.tonysp.playersync.bukkit.listeners;

import cz.goldminer.tonysp.playersync.bukkit.PlayerSyncBukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;


public class InventoryClickListener implements Listener {

    private final PlayerSyncBukkit plugin;

    public InventoryClickListener(PlayerSyncBukkit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickEvent (InventoryClickEvent event) {
        if (plugin.isNotAuthenticated((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }
}

