package cz.goldminer.tonysp.playersync.bukkit;

import cz.goldminer.tonysp.playersync.bukkit.events.PlayerAuthenticateEvent;
import cz.goldminer.tonysp.playersync.bukkit.listeners.*;
import cz.goldminer.tonysp.playersync.messaging.MessagingManager;
import cz.goldminer.tonysp.playersync.messaging.PlayerSyncMessage;
import cz.goldminer.tonysp.playersync.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerSyncBukkit extends JavaPlugin implements Listener {

    private static PlayerSyncBukkit plugin;
    public boolean DEBUG = false;
    public boolean SERVER_SHUTTING_DOWN = false;
    public String SERVER_ID;

    private HashSet<String> authenticatedPlayers = new HashSet<>();
    private ConcurrentHashMap<Player, Location> playerLoginLocations = new ConcurrentHashMap<>();
    private HashMap<Player, Integer> loginSecondsRemaining = new HashMap<>();

    private MessagingManager messagingManager;
    private MessagesProcessor messagesProcessor;
    private InventorySerialization inventorySerialization;

    @Override
    public void onEnable () {
        plugin = this;

        loadConfig();

        messagingManager.clearMessageList(SERVER_ID);

        new AsyncPlayerChatListener(this);
        new AsyncPlayerPreLoginListener(this);
        new EntityDamageListener(this);
        new EntityPickupItemListener(this);
        new InventoryClickListener(this);
        new PlayerDropItemListener(this);
        new PlayerInteractListener(this);
        new PlayerJoinListener(this);
        new PlayerQuitListener(this);
        new PlayerTeleportListener(this);

        this.inventorySerialization = new InventorySerialization();

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            teleportPlayersBackToLoginLocations();
            kickPlayersTakingTooLong();
        }, 100L, 100L);

        // TODO make async!
        getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            messagingManager.sendMessages();
            messagesProcessor.processMessages(messagingManager.retrieveMessages());
        }, 10L, 10L);
    }

    @Override
    public void onDisable () {
        for (Player player : getInstance().getServer().getOnlinePlayers()) {
            sendPlayerDataOnExit(player);
            player.kickPlayer(ChatColor.RED + "Server vypnut");
        }

        messagingManager.sendMessages();
        messagesProcessor.processMessages(messagingManager.retrieveMessages());
        messagingManager.clearMessageList(SERVER_ID);
    }

    private void loadConfig () {

        if (!(new File(getDataFolder() + File.separator + "config.yml").exists())) {
            saveDefaultConfig();
        }

        try {
            new YamlConfiguration().load(new File(getDataFolder() + File.separator + "config.yml"));
        } catch (Exception e) {
            System.out.println("There was a problem loading the config. More details bellow.");
            System.out.println("-----------------------------------------------");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        reloadConfig();

        FileConfiguration configuration = getConfig();

        SERVER_ID = configuration.getString("server-id");
        String redisIp = configuration.getString("redis.ip");
        int redisPort = configuration.getInt("redis.port");
        String redisPassword = configuration.getString("redis.password");

        messagingManager = new MessagingManager(redisIp, redisPort, redisPassword, SERVER_ID);
        messagesProcessor = new MessagesProcessor();
    }

    public static PlayerSyncBukkit getInstance () {
        return plugin;
    }

    public void log (String message) {
        getLogger().log(Level.INFO, "[PlayerSync3] " + message);
    }

    public void log (Level level, String message) {
        getLogger().log(level, "[PlayerSync3] " + message);
    }

    private void teleportPlayersBackToLoginLocations () {
        Iterator<Map.Entry<Player, Location>> iterator = playerLoginLocations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Location> entry = iterator.next();
            if (isNotAuthenticated(entry.getKey())) {
                entry.getKey().teleport(entry.getValue(), PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);
            } else {
                iterator.remove();
            }
        }
    }

    private void kickPlayersTakingTooLong () {
        Iterator<Map.Entry<Player, Integer>> iterator = loginSecondsRemaining.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Integer> entry = iterator.next();
            if (isNotAuthenticated(entry.getKey())
                    && entry.getValue() <= 0) {
                entry.getKey().kickPlayer(ChatColor.RED + "Nestihl ses prihlasit.");
                iterator.remove();
            } else {
                entry.setValue(entry.getValue() - 5);
            }
        }
    }

    public boolean isAuthenticated (Player player) {
        return authenticatedPlayers.contains(player.getName());
    }

    public boolean isNotAuthenticated (Player player) {
        return !authenticatedPlayers.contains(player.getName());
    }

    public void setPlayerLoginLocation (Player player, Location location) {
        playerLoginLocations.put(player, location);
    }

    public void playerJoined (Player player) {
        if (authenticatedPlayers.contains(player.getName())) return;

        loginSecondsRemaining.put(player, 180);
        setPlayerLoginLocation(player, player.getLocation());
        player.getInventory().clear();
        PlayerSyncBukkit.getInstance().log("SENDING: PLAYER_DATA_REQUEST to: " + MessagingManager.BUNGEE_SERVER_ID);
        MessagingManager.getInstance().queueMessageToSend(new PlayerSyncMessage(PlayerSyncMessage.Action.PLAYER_DATA_REQUEST, MessagingManager.BUNGEE_SERVER_ID, SERVER_ID, new PlayerData(player.getName(), null, null, 1)));
    }

    public void sendPlayerDataOnExit (Player player) {
        if (isNotAuthenticated(player)) return;
        PlayerData playerData = InventorySerialization.getInstance().generatePlayerData(player);
        PlayerSyncBukkit.getInstance().log("SENDING: PLAYER_DATA_TO_DB to: " + MessagingManager.BUNGEE_SERVER_ID);
        MessagingManager.getInstance().queueMessageToSend(new PlayerSyncMessage(PlayerSyncMessage.Action.PLAYER_DATA_TO_DB, MessagingManager.BUNGEE_SERVER_ID, SERVER_ID, playerData));
        authenticatedPlayers.remove(player.getName());
        player.getInventory().clear();
    }

    public void receivedPlayerData (String playerName, PlayerData playerData) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline() || !isNotAuthenticated(player)) return;

        InventorySerialization.getInstance().loadPlayerInventory(player, playerData);
        player.setLevel(playerData.getLevel());
        authenticatedPlayers.add(playerName);
        PlayerAuthenticateEvent event = new PlayerAuthenticateEvent(player);
        Bukkit.getServer().getPluginManager().callEvent(event);
        PlayerSyncBukkit.getInstance().log("SENDING: PLAYER_RECEIVED_DATA_OK to: " + MessagingManager.BUNGEE_SERVER_ID);
        MessagingManager.getInstance().queueMessageToSend(new PlayerSyncMessage(PlayerSyncMessage.Action.PLAYER_RECEIVED_DATA_OK, MessagingManager.BUNGEE_SERVER_ID, SERVER_ID, playerData));
    }
}
