package cz.goldminer.tonysp.playersync.messaging;

import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MessagingManager {

    private static MessagingManager instance;

    public static final String BUNGEE_SERVER_ID = "bungee";

    private JedisPool jedisPool;
    private String redisPassword;
    private Long currentMessageListPos = 0L;

    private final String SERVER_ID;
    private final String MESSAGES_PREFIX = "player_sync_messages_";
    private final String MESSAGES_KEY;

    private ConcurrentLinkedQueue<PlayerSyncMessage> readyToSend = new ConcurrentLinkedQueue<>();

    public MessagingManager (String redisIp, int redisPort, String redisPassword, String SERVER_ID) {
        instance = this;
        this.redisPassword = redisPassword;

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(MessagingManager.class.getClassLoader());
        jedisPool = new JedisPool(redisIp, redisPort);
        Thread.currentThread().setContextClassLoader(previous);

        this.SERVER_ID = SERVER_ID;
        this.MESSAGES_KEY = this.MESSAGES_PREFIX + this.SERVER_ID;
    }

    public static MessagingManager getInstance () {
        return instance;
    }

    public void queueMessageToSend (PlayerSyncMessage message) {
        readyToSend.add(message);
    }

    public void clearMessageListForMultipleServer (List<String> servers) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);
            for (String server : servers) {
                jedis.del(MESSAGES_PREFIX + server);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearMessageList(String server) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);
            jedis.del(MESSAGES_PREFIX + server);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<PlayerSyncMessage> retrieveMessages() {
        List<String> stringList = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            Long llen = jedis.llen(MESSAGES_KEY);
            if (jedis.exists(MESSAGES_KEY) && llen > currentMessageListPos) {
                stringList = jedis.lrange(MESSAGES_KEY, currentMessageListPos, llen);
                currentMessageListPos = llen;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<PlayerSyncMessage> playerSyncMessages = new ArrayList<>();
        for (String messageString : stringList) {
            try {
                playerSyncMessages.add(PlayerSyncMessage.fromString(messageString));
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return playerSyncMessages;
    }

    public void sendMessages () {
        if (readyToSend.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(redisPassword);

            while (!readyToSend.isEmpty()) {
                PlayerSyncMessage message = readyToSend.remove();
                String messageString = message.toString();

                if (messageString != null)
                    jedis.rpush(MESSAGES_PREFIX + message.getToServer(), messageString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
