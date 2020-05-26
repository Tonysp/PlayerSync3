package cz.goldminer.tonysp.playersync.bungeecord;

import cz.goldminer.tonysp.playersync.messaging.MessagingManager;
import cz.goldminer.tonysp.playersync.PlayerData;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;


public class PlayerDataManager {

    private static PlayerDataManager instance;

    private JedisPool jedisPool;
    private String redisPassword;
    private String INVENTORY_KEY_SUFFIX = "Inventory";
    private String PLAYER_IP_REMEMBER_SUFFIX = "RememberIp";
    private String PLAYER_IP_LIST_SUFFIX = "IpList";

    public PlayerDataManager (String redisIp, int redisPort, String redisPassword) {
        instance = this;
        this.redisPassword = redisPassword;

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(MessagingManager.class.getClassLoader());
        jedisPool = new JedisPool(redisIp, redisPort);
        Thread.currentThread().setContextClassLoader(previous);
    }

    public static PlayerDataManager getInstance () {
        return instance;
    }

    public PlayerData loadPlayerData (String playerName){
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            String key = playerName.concat(INVENTORY_KEY_SUFFIX);
            if (!jedis.exists(key)) {
                PlayerData playerData = generateNewPlayerData(playerName);
                savePlayerData(playerName, playerData);
                return playerData;
            }

            byte[] data = jedis.get(key.getBytes());

            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ins = new ObjectInputStream(bais);
            PlayerData pd = (PlayerData)ins.readObject();

            ins.close();
            bais.close();
            return pd;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public PlayerData generateNewPlayerData (String playerName) {
        return new PlayerData(playerName, null, null, 1);
    }

    public boolean savePlayerData(String playerName, Object object){
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);

            oos.writeObject(object);
            oos.flush();
            oos.close();
            bos.close();

            byte[] data = bos.toByteArray();

            String key = playerName.concat(INVENTORY_KEY_SUFFIX);
            jedis.set(key.getBytes(), data);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean hasPlayerSetRemeberIp (String playerName) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            String key = playerName.concat(PLAYER_IP_REMEMBER_SUFFIX);
            if (!jedis.exists(key)) {
                return false;
            }

            return Boolean.valueOf(jedis.get(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setPlayerRememberIp (String playerName, boolean value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            String key = playerName.concat(PLAYER_IP_REMEMBER_SUFFIX);
            PlayerSyncBungee.getInstance().log("SAVING " + Boolean.valueOf(value).toString());
            String stringValue = Boolean.valueOf(value).toString();
            jedis.set(key, stringValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> getPlayerRememberIpList (String playerName) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            String key = playerName.concat(PLAYER_IP_LIST_SUFFIX);
            if (!jedis.exists(key)) {
                return new ArrayList<>();
            }

            String ipString = jedis.get(key);
            return ipStringToList(ipString);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void setPlayerRememberIpList (String playerName, ArrayList<String> ipList) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            String key = playerName.concat(PLAYER_IP_LIST_SUFFIX);
            jedis.set(key, ipListToString(ipList));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> ipStringToList (String ipString) {
        String[] ips = ipString.split(";");
        ArrayList<String> ipList = new ArrayList<>();
        for (String ip : ips) {
            if (!ip.isEmpty())
                ipList.add(ip);
        }
        return ipList;
    }

    private String ipListToString (ArrayList<String> ipList) {
        StringBuilder ipString = new StringBuilder();
        String delimiter = "";
        for (String ip : ipList) {
            ipString.append(delimiter);
            delimiter = ";";
            ipString.append(ip);
        }
        return ipString.toString();
    }
}
