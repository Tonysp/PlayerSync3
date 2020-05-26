package cz.goldminer.tonysp.playersync.bukkit;

import cz.goldminer.tonysp.playersync.messaging.PlayerSyncMessage;

import java.util.List;
import java.util.logging.Level;

public class MessagesProcessor {

    private static MessagesProcessor instance;

    public MessagesProcessor () {
        instance = this;
    }

    public static MessagesProcessor getInstance () {
        return instance;
    }

    public void processMessages (List<PlayerSyncMessage> messages) {
        for (PlayerSyncMessage message : messages) {
            PlayerSyncBukkit.getInstance().log("PROCESSING: " + message.getAction() + " from: " + message.getFromServer());
            if (message.getPlayerData() == null) {
                PlayerSyncBukkit.getInstance().log("pdatanull");
            } else if (message.getPlayerData().getName() == null) {
                PlayerSyncBukkit.getInstance().log("pnamenull");
            }
            String playerName = message.getPlayerData().getName();
            switch (message.getAction()) {
                case PLAYER_DATA_FROM_DB:
                    PlayerSyncBukkit.getInstance().receivedPlayerData(playerName, message.getPlayerData());
                    break;
                default:
                    PlayerSyncBukkit.getInstance().log(Level.SEVERE, "Wrong message action received!!! (" + message.getAction() + ")");
            }
        }
    }
}
