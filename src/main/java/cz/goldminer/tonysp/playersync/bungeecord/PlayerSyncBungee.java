package cz.goldminer.tonysp.playersync.bungeecord;

import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
import cz.goldminer.tonysp.playersync.bungeecord.commands.*;
import cz.goldminer.tonysp.playersync.bungeecord.listeners.PlayerDisconnectListener;
import cz.goldminer.tonysp.playersync.bungeecord.listeners.PostLoginListener;
import cz.goldminer.tonysp.playersync.bungeecord.listeners.ServerSwitchListener;
import cz.goldminer.tonysp.playersync.messaging.MessagingManager;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerSyncBungee extends Plugin {

    private static PlayerSyncBungee plugin;

    private MessagingManager messagingManager;
    private MessagesProcessor messagesProcessor;
    private AuthenticationManager authenticationManager;
    private PlayerDataManager playerDataManager;

    private boolean shuttingDown = false;

    @Override
    public void onEnable() {
        plugin = this;

        loadConfig();

        messagingManager.clearMessageList(MessagingManager.BUNGEE_SERVER_ID);

        // Register event listeners
        getProxy().getPluginManager().registerListener(this, new PlayerDisconnectListener());
        getProxy().getPluginManager().registerListener(this, new PostLoginListener());
        getProxy().getPluginManager().registerListener(this, new ServerSwitchListener());

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new ForgetCommand("forget"));
        getProxy().getPluginManager().registerCommand(this, new RegisterCommand("register"));
        getProxy().getPluginManager().registerCommand(this, new RegisterCommand("reg"));
        getProxy().getPluginManager().registerCommand(this, new LoginCommand("login"));
        getProxy().getPluginManager().registerCommand(this, new LoginCommand("l"));
        getProxy().getPluginManager().registerCommand(this, new LogoutCommand("logout"));
        getProxy().getPluginManager().registerCommand(this, new RememberCommand("remember"));

        plugin.getProxy().getScheduler().schedule(plugin, () -> plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            messagingManager.sendMessages();
            messagesProcessor.processMessages(messagingManager.retrieveMessages());
        }), 500L, 500L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        messagingManager.sendMessages();
        messagesProcessor.processMessages(messagingManager.retrieveMessages());
        messagingManager.clearMessageList(MessagingManager.BUNGEE_SERVER_ID);

        AuthenticationManager.getInstance().savePlayers();
    }

    public static PlayerSyncBungee getInstance () {
        return plugin;
    }

    public void log (String message) {
        getProxy().getLogger().log(Level.INFO, "[PlayerSync3] " + message);
    }

    public void log (Level level, String message) {
        getProxy().getLogger().log(level, "[PlayerSync3] " + message);
    }

    private boolean loadConfig () {
        log("Loading configuration.");
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdir()) {
                log(Level.SEVERE, "Could not create configuration folder.");
                return false;
            }
        }


        File file = new File(getDataFolder() + File.separator + "bungee_config.yml");

        if (!file.exists()) {
            try {
                Files.copy(getResourceAsStream("bungee_config.yml"), file.toPath());
            } catch (IOException ioe) {
                log(Level.SEVERE, "Could not create configuration file.");
                ioe.printStackTrace();
                return false;
            }
        }


        Configuration configuration;
        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "bungee_config.yml"));
        } catch (IOException ioe) {
            log(Level.SEVERE, "Could not load the configuration.");
            ioe.printStackTrace();
            return false;
        }


        String serverId = MessagingManager.BUNGEE_SERVER_ID;
        String redisIp = configuration.getString("redis.ip");
        int redisPort = configuration.getInt("redis.port");
        String redisPassword = configuration.getString("redis.password");

        messagingManager = new MessagingManager(redisIp, redisPort, redisPassword, serverId);
        playerDataManager = new PlayerDataManager(redisIp, redisPort, redisPassword);

        String discourseDbUrl = configuration.getString("postgresql.url");
        String discourseDbUser = configuration.getString("postgresql.username");
        String discourseDbPassword = configuration.getString("postgresql.password");

        String discourseApiUrl = configuration.getString("discourse.users-api-url");
        String discourseApiKey = configuration.getString("discourse.api-key");
        String discourseApiUser = configuration.getString("discourse.api-username");

        authenticationManager = new AuthenticationManager(discourseDbUrl, discourseDbUser, discourseDbPassword, discourseApiUrl, discourseApiUser, discourseApiKey);
        messagesProcessor = new MessagesProcessor();

        return true;
    }

    public boolean isShuttingDown () {
        return shuttingDown;
    }
}
