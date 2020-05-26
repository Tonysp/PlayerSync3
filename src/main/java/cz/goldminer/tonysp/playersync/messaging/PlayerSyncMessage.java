package cz.goldminer.tonysp.playersync.messaging;

import cz.goldminer.tonysp.playersync.PlayerData;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.util.*;

public class PlayerSyncMessage implements Serializable {

    public enum Action {
        PLAYER_DATA_TO_DB,
        PLAYER_DATA_FROM_DB,
        PLAYER_RECEIVED_DATA_OK,
        PLAYER_DATA_REQUEST,
        ;
    }

    private String toServer, fromServer;
    private Action action;
    private PlayerData playerData;

    public PlayerSyncMessage (Action action, String toServer, String fromServer, PlayerData playerData) {
        this.action = action;
        this.toServer = toServer;
        this.fromServer = fromServer;
        this.playerData = playerData;
    }

    public Action getAction () {
        return action;
    }

    public String getToServer () {
        return toServer;
    }

    public PlayerData getPlayerData () {
        return playerData;
    }

    public String getFromServer () {
        return fromServer;
    }

    @Override
    public String toString () {
        String message;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream( baos );
            oos.writeObject(this);
            oos.close();
            message = new String( Base64Coder.encode(baos.toByteArray()) );
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }

        return message;
    }

    @Override
    public int hashCode () {
        return Objects.hash(
                toServer,
                playerData
        );
    }

    public static PlayerSyncMessage fromString (String s ) throws IOException, ClassNotFoundException {
        byte [] data = Base64Coder.decode( s );
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(  data ) );
        PlayerSyncMessage o  = (PlayerSyncMessage) ois.readObject();
        ois.close();
        return o;
    }
}
