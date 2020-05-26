package cz.goldminer.tonysp.playersync.bungeecord;

import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
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
            String playerName = message.getPlayerData().getName();
            PlayerSyncBungee.getInstance().log("PROCESSING: " + message.getAction() + " from: " + message.getFromServer());
            switch (message.getAction()) {
                case PLAYER_RECEIVED_DATA_OK:
                    AuthenticationManager.getInstance().setSlaveReceivedDataOk(playerName, true);
                    //PlayerSyncMessage playerSyncMessage = new PlayerSyncMessage(PlayerSyncMessage.Action.PLAYER_RECEIVED_DATA_OK_ACK, message.getToServer(), PlayerData.BUNGEE_SERVER_ID,null);
                    //MessagingManager.getInstance().queueMessageToSend(playerSyncMessage);
                    break;
                case PLAYER_DATA_TO_DB:
                    AuthenticationManager.getInstance().receiveDataFromSlave(playerName, message.getPlayerData());
                    break;
                case PLAYER_DATA_REQUEST:
                    AuthenticationManager.getInstance().slaveRequestedData(playerName);
                    break;
                default:
                    PlayerSyncBungee.getInstance().log(Level.SEVERE, "Wrong message action received!!! (" + message.getAction() + ")");
            }
        }
    }
}
