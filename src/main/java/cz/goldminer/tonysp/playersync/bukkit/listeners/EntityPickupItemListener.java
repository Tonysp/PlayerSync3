package cz.goldminer.tonysp.playersync.bukkit.listeners;

import cz.goldminer.tonysp.playersync.bukkit.PlayerSyncBukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class EntityPickupItemListener implements Listener {

    private final PlayerSyncBukkit plugin;

    public EntityPickupItemListener (PlayerSyncBukkit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupItemEvent (EntityPickupItemEvent event) {
        if ((event.getEntity() instanceof Player) && plugin.isNotAuthenticated((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }
}