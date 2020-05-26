package cz.goldminer.tonysp.playersync.bungeecord.auth;

import cz.goldminer.tonysp.playersync.bungeecord.PlayerSyncBungee;
import cz.goldminer.tonysp.playersync.bungeecord.PlayerDataManager;
import cz.goldminer.tonysp.playersync.messaging.MessagingManager;
import cz.goldminer.tonysp.playersync.messaging.PlayerSyncMessage;
import cz.goldminer.tonysp.playersync.PlayerData;
import io.netty.util.internal.ConcurrentSet;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.postgresql.Driver;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class AuthenticationManager {

    private static AuthenticationManager instance;

    private ConcurrentSet<String> waitingForDataFromSlave = new ConcurrentSet<>();
    private ConcurrentSet<String> slaveRequestedData = new ConcurrentSet<>();
    private ConcurrentHashMap<String, PlayerAuthData> playerAuthDataMap = new ConcurrentHashMap<>();
    private HashSet<String> playersOnline = new HashSet<>();

    private String discourseDbUrl, discourseDbUser, discourseDbPassword;
    private String discourseApiUrl, discourseApiUser, discourseApiKey;

    public AuthenticationManager (String discourseDbUrl,
                                  String discourseDbUser,
                                  String discourseDbPassword,
                                  String discourseApiUrl,
                                  String discourseApiUser,
                                  String discourseApiKey) {
        instance = this;
        this.discourseDbUrl = discourseDbUrl;
        this.discourseDbUser = discourseDbUser;
        this.discourseDbPassword = discourseDbPassword;
        this.discourseApiUrl = discourseApiUrl;
        this.discourseApiUser = discourseApiUser;
        this.discourseApiKey = discourseApiKey;

        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        PlayerSyncBungee.getInstance().getProxy().getScheduler().schedule(PlayerSyncBungee.getInstance(), () -> {
            for (PlayerAuthData playerAuthData : AuthenticationManager.getInstance().playerAuthDataMap.values()) {
                if (playerAuthData.isLoggedIn()) continue;
                ProxiedPlayer proxiedPlayer = PlayerSyncBungee.getInstance().getProxy().getPlayer(playerAuthData.getPlayerName());
                if (proxiedPlayer == null) continue;

                String message = playerAuthData.getPeriodicalMessage();
                if (message != null)
                    proxiedPlayer.sendMessage(ChatMessageType.CHAT, TextComponent.fromLegacyText(message));
            }
        }, 3L, 3L, TimeUnit.SECONDS);

        PlayerSyncBungee.getInstance().getProxy().getScheduler().schedule(PlayerSyncBungee.getInstance(), () -> {
            Iterator<String> iterator = slaveRequestedData.iterator();
            while (iterator.hasNext()) {
                String playerName = iterator.next();
                if (!playerAuthDataMap.containsKey(playerName)
                        || !playerAuthDataMap.get(playerName).isLoggedIn()) {
                    if (!playerAuthDataMap.get(playerName).isLoggedIn()) {
                        PlayerSyncBungee.getInstance().log("removing data request 1");
                    }
                    if (!playerAuthDataMap.containsKey(playerName)) {
                        PlayerSyncBungee.getInstance().log("removing data request 2");
                    }
                    iterator.remove();
                    continue;
                }

                PlayerSyncBungee.getInstance().log("requesteddata: " + playerName);
                if (!isWaitingForDataFromSlave(playerName)) {
                    PlayerSyncBungee.getInstance().log("requesteddata: " + playerName + ", SENDING");
                    iterator.remove();
                    ProxiedPlayer proxiedPlayer = PlayerSyncBungee.getInstance().getProxy().getPlayer(playerName);
                    if (proxiedPlayer == null) continue;
                    String serverName = proxiedPlayer.getServer().getInfo().getName();
                    sendPlayerData(serverName, playerAuthDataMap.get(playerName));
                }
            }
        }, 200L, 200L, TimeUnit.MILLISECONDS);

        PlayerSyncBungee.getInstance().getProxy().getScheduler().schedule(PlayerSyncBungee.getInstance(), () -> {
            if (!PlayerSyncBungee.getInstance().isShuttingDown())
                savePlayers();
        }, 60L, 60L, TimeUnit.SECONDS);
    }

    public static AuthenticationManager getInstance () {
        return instance;
    }

    public void tryLogin (ProxiedPlayer proxiedPlayer, String[] args) {
        if (!playerAuthDataMap.containsKey(proxiedPlayer.getName())) {
            PlayerSyncBungee.getInstance().log("tryLogin but not in authdatamap");
            return;
        }

        PlayerAuthData playerAuthData = playerAuthDataMap.get(proxiedPlayer.getName());
        if (playerAuthData.isLoggedIn()) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Uz jsi prihlasen! Pouzij /logout pro odhlaseni."));
            return;
        }

        if (args.length != 1) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Chybne argumenty!"));
            return;
        }

        String passwordFromUser = args[0];
        if (passwordMatches(passwordFromUser, playerAuthData.getSalt(), playerAuthData.getPasswordHash())) {
            sendPlayerData(proxiedPlayer.getServer().getInfo().getName(), playerAuthData);
            playerAuthData.setLoggedIn(true);
            TextComponent textComponent = new TextComponent("Uspesne prihlasen.");
            textComponent.setColor(ChatColor.GREEN);
            proxiedPlayer.sendMessage(textComponent);
        } else {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Spatne heslo! Pokud jsi ho zapomnel, resetujes ho na https://goldminer.cz/login"));
        }
    }

    public void tryRegister (ProxiedPlayer proxiedPlayer, String[] args) {
        if (!playerAuthDataMap.containsKey(proxiedPlayer.getName())) {
            PlayerSyncBungee.getInstance().log("tryRegister but not in authdatamap");
            return;
        }

        PlayerAuthData playerAuthData = playerAuthDataMap.get(proxiedPlayer.getName());
        if (playerAuthData.isRegistered()) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Uz jsi zaregistrovan! Pouzij /login [heslo] pro prihlaseni."));
            return;
        }

        if (args.length != 2) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Chybne argumenty!"));
            return;
        }

        String username = proxiedPlayer.getName();
        String password = args[0];
        String email = args[1];

        String usernameFixed = fixUsername(username);


        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(discourseApiUrl);
        httppost.addHeader("Content-Type", "multipart/form-data");
        httppost.addHeader("Api-Key", discourseApiKey);
        httppost.addHeader("Api-Username", discourseApiUser);


        List<NameValuePair> params = new ArrayList<>(6);
        params.add(new BasicNameValuePair("username", usernameFixed));
        params.add(new BasicNameValuePair("name", username));
        params.add(new BasicNameValuePair("email", email));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("approved", "true"));
        params.add(new BasicNameValuePair("active", "true"));

        try {
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Chyba pri registraci. Kontaktuj prosim administratora."));
            e.printStackTrace();
            return;
        }

        HttpResponse response = null;
        try {
            response = httpclient.execute(httppost);
        } catch (IOException e) {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Chyba pri registraci. Kontaktuj prosim administratora."));
            e.printStackTrace();
            return;
        }
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            try (InputStream instream = entity.getContent()) {
                String message = convertStreamToString(instream);
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(message);

                if ((Boolean) json.get("success")) {
                    sendPlayerData(proxiedPlayer.getServer().getInfo().getName(), playerAuthData);
                    playerAuthData.setLoggedIn(true);
                    proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "Uspesne zaregistrovan! Uz muzes hrat, ale nezapomen potvrdit svoji registraci v emailu."));
                } else {
                    boolean unknownError = true;
                    if (message.contains("New registrations are not")) {
                        proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Z tvoji IP bylo zaregistrovano prilis mnoho uctu, prosim kontaktuj vedeni serveru."));
                        return;
                    }
                    if (message.contains("Password je to")) {
                        unknownError = false;
                        proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Heslo je prilis kratke. (minumum je 8 znaku)"));
                    }
                    if (message.contains("Primary email je nep")) {
                        unknownError = false;
                        proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Email je neplatny."));
                    }
                    if (message.contains("Primary email u")) {
                        unknownError = false;
                        proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Tento email patri jinemu uctu. Pokud jsi zapomnel heslo, muzes si ho resetovat na https://goldminer.cz"));
                    }

                    if (unknownError) {
                        PlayerSyncBungee.getInstance().log(Level.SEVERE, "Unknown error while registering player " + proxiedPlayer.getName());
                        PlayerSyncBungee.getInstance().log(Level.SEVERE, message);
                        throw new Exception("Unknown Error");
                    }
                }
            } catch (Exception e) {
                proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Chyba pri registraci. Kontaktuj prosim administratora."));
                e.printStackTrace();
            }
        }

        /*if (passwordMatches(passwordFromUser, playerAuthData.getSalt(), playerAuthData.getPasswordHash())) {
            sendPlayerData(proxiedPlayer.getServer().getInfo().getName(), playerAuthData);
            playerAuthData.setLoggedIn(true);
            TextComponent textComponent = new TextComponent("Uspesne prihlasen.");
            textComponent.setColor(ChatColor.GREEN);
            proxiedPlayer.sendMessage(textComponent);
        } else {
            proxiedPlayer.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Spatne heslo! Pokud jsi ho zapomnel, resetujes ho na https://goldminer.cz/login"));
        }*/
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public void remember (ProxiedPlayer proxiedPlayer) {
        if (!playerAuthDataMap.containsKey(proxiedPlayer.getName())) return;
        PlayerAuthData playerAuthData = playerAuthDataMap.get(proxiedPlayer.getName());
        if (!playerAuthData.isLoggedIn()) return;

        String currentIp = ipToString(proxiedPlayer);
        if (!playerAuthData.isIpRemembered(currentIp)) {
            if (playerAuthData.getIpList().size() >= 2) {
                playerAuthData.getIpList().set(0, playerAuthData.getIpList().get(1));
                playerAuthData.getIpList().set(1, currentIp);
            } else {
                playerAuthData.getIpList().add(currentIp);
            }
        }
        playerAuthData.setRememberIp(true);
        playerAuthData.setNeedsSave(true);

        TextComponent textComponent = new TextComponent();
        textComponent.setText("Automaticke prihlasovani zapnuto.\nPro vypnuti automatickeho prihlasovani\nnapis /forget");
        textComponent.setColor(ChatColor.GREEN);
        proxiedPlayer.sendMessage(ChatMessageType.CHAT, textComponent);
    }

    public void forget (ProxiedPlayer proxiedPlayer) {
        if (!playerAuthDataMap.containsKey(proxiedPlayer.getName())) return;
        PlayerAuthData playerAuthData = playerAuthDataMap.get(proxiedPlayer.getName());
        if (!playerAuthData.isLoggedIn()) return;

        playerAuthData.setRememberIp(false);
        playerAuthData.setNeedsSave(true);

        TextComponent textComponent = new TextComponent();
        textComponent.setText("Automaticke prihlasovani vypnuto.\nPro zapnuti automatickeho prihlasovani\nnapis /remember");
        textComponent.setColor(ChatColor.GREEN);
        proxiedPlayer.sendMessage(ChatMessageType.CHAT, textComponent);
    }

    public void logout (ProxiedPlayer proxiedPlayer) {
        if (!playerAuthDataMap.containsKey(proxiedPlayer.getName())) return;

        PlayerAuthData playerAuthData = playerAuthDataMap.get(proxiedPlayer.getName());
        playerAuthData.setRememberIp(false);
        playerAuthData.setLoggedIn(false);
        slaveRequestedData.remove(playerAuthData.getPlayerName());
        setAsOffline(playerAuthData.getPlayerName());
        TextComponent textComponent = new TextComponent("Uspesne odhlasen!");
        textComponent.setColor(ChatColor.RED);
        proxiedPlayer.disconnect(textComponent);
    }

    public String ipToString (ProxiedPlayer proxiedPlayer) {
        return proxiedPlayer.getAddress().getAddress().toString().replace("/", "");
    }

    public void serverSwitched (String playerName) {
        PlayerSyncBungee.getInstance().log("Switch! --------------------------------------------------------------");
        waitingForDataFromSlave.add(playerName);
    }

    public boolean isWaitingForDataFromSlave (String playerName) {
        return waitingForDataFromSlave.contains(playerName);
    }

    public void setAsOnline (String playerName) {
        playersOnline.add(playerName);
    }

    public void setAsOffline (String playerName) {
        playersOnline.remove(playerName);
    }

    public boolean isSetAsOnline (String playerName) {
        return playersOnline.contains(playerName);
    }

    public void sendPlayerData (String destinationServer, PlayerAuthData playerAuthData) {
        slaveRequestedData.remove(playerAuthData.getPlayerName());
        PlayerData playerData = playerAuthData.getPlayerData();
        PlayerSyncBungee.getInstance().log("SENDING: PLAYER_DATA_FROM_DB (" + playerData.getName() + ") to: " + destinationServer);
        PlayerSyncMessage playerSyncMessage = new PlayerSyncMessage(PlayerSyncMessage.Action.PLAYER_DATA_FROM_DB, destinationServer, MessagingManager.BUNGEE_SERVER_ID, playerData);
        MessagingManager.getInstance().queueMessageToSend(playerSyncMessage);
    }

    public void loadPlayerAuthData (final String playerName, final String playerIp) {
        ProxyServer.getInstance().getScheduler().runAsync(PlayerSyncBungee.getInstance(), () -> {
            PlayerAuthData playerAuthData;
            if (playerAuthDataMap.containsKey(playerName)) {
                playerAuthData = playerAuthDataMap.get(playerName);
            } else {
                playerAuthData = new PlayerAuthData();
            }
            playerAuthData.setPlayerName(playerName);
            playerAuthData.setLoggedIn(false);
            playerAuthData.setRegistered(false);
            playerAuthData.setPasswordSet(false);
            playerAuthData.setSlaveReceivedDataOk(false);

            String playerNameFixed = playerName;
            if (playerNameFixed.endsWith("_")) {
                playerNameFixed += "0";
            }

            try (Connection connection = DriverManager.getConnection(discourseDbUrl, discourseDbUser, discourseDbPassword)) {
                PreparedStatement sql = connection.prepareStatement("SELECT salt,password_hash FROM users WHERE username='" + playerNameFixed + "';");
                ResultSet resultSet = sql.executeQuery();
                if (resultSet.next()) {
                    playerAuthData.setRegistered(true);
                    String salt = resultSet.getString("salt");
                    if (salt != null) {
                        String passwordHash = resultSet.getString("password_hash");
                        playerAuthData.setPasswordSet(true);
                        playerAuthData.setPasswordHash(passwordHash);
                        playerAuthData.setSalt(salt);
                    }
                }

                resultSet.close();
                sql.close();
            } catch (SQLException e) {
                System.out.println("POSTGRESQL CONNECTION FAILED ON LOGIN (" + playerName + ")");
                e.printStackTrace();
            }
            PlayerDataManager playerDataManager = PlayerDataManager.getInstance();
            playerAuthData.setRememberIp(playerDataManager.hasPlayerSetRemeberIp(playerName));
            playerAuthData.setIpList(playerDataManager.getPlayerRememberIpList(playerName));
            if (playerAuthData.isIpRemembered(playerIp)) {
                PlayerSyncBungee.getInstance().log("remeber IP, logged in: true");
                playerAuthData.setLoggedIn(true);
            }

            PlayerData playerData;
            if (playerAuthDataMap.containsKey(playerName)) {
                playerData = playerAuthDataMap.get(playerName).getPlayerData();
            } else if (waitingForDataFromSlave.contains(playerName)) {
                playerData = null;
            } else {
                playerData = PlayerDataManager.getInstance().loadPlayerData(playerName);
            }
            playerAuthData.setPlayerData(playerData);
            playerAuthDataMap.put(playerName, playerAuthData);
            PlayerSyncBungee.getInstance().log("loaded player auth data from db (" + playerName + ")");
        });
    }

    public void savePlayerAuthData (PlayerAuthData playerAuthData) {
        ProxyServer.getInstance().getScheduler().runAsync(PlayerSyncBungee.getInstance(), () -> {
            String playerName = playerAuthData.getPlayerName();
            PlayerDataManager playerDataManager = PlayerDataManager.getInstance();
            playerDataManager.setPlayerRememberIp(playerName, playerAuthData.hasRememberIp());
            playerDataManager.setPlayerRememberIpList(playerName, playerAuthData.getIpList());
            playerDataManager.savePlayerData(playerName, playerAuthData.getPlayerData());
            playerAuthData.setNeedsSave(false);
            PlayerSyncBungee.getInstance().log("saved player auth data to db (" + playerName + ")");
        });
    }

    private boolean passwordMatches (String inputPassword, String salt, String passwordHash) {
        int iterations = 64000;
        int keyLength = 256;
        PlayerSyncBungee.getInstance().log("pw: " + inputPassword + ", salt: " + salt + ", pwh: " + passwordHash);
        char[] passwordChars = inputPassword.toCharArray();
        byte[] saltBytes = salt.getBytes();

        byte[] hashedBytes = hashPassword(passwordChars, saltBytes, iterations, keyLength);
        String hashedString = Hex.encodeHexString(hashedBytes);

        return hashedString.equals(passwordHash);
    }

    private byte[] hashPassword (final char[] password, final byte[] salt, final int iterations, final int keyLength) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance( "PBKDF2WithHmacSHA256" );
            PBEKeySpec spec = new PBEKeySpec( password, salt, iterations, keyLength );
            SecretKey key = skf.generateSecret( spec );
            return key.getEncoded();
        } catch ( NoSuchAlgorithmException | InvalidKeySpecException e ) {
            throw new RuntimeException( e );
        }
    }

    public void setSlaveReceivedDataOk (String playerName, boolean value) {
        if (playerAuthDataMap.containsKey(playerName))
            playerAuthDataMap.get(playerName).setSlaveReceivedDataOk(value);
    }

    public void receiveDataFromSlave (String playerName, PlayerData playerData) {
        if (!playerAuthDataMap.containsKey(playerName)) return;
        PlayerAuthData playerAuthData = playerAuthDataMap.get(playerName);
        if (playerAuthData.isSlaveReceivedDataOk()) {
            playerAuthData.setSlaveReceivedDataOk(false);
            playerAuthData.setPlayerData(playerData);
            playerAuthData.setNeedsSave(true);

            if (!isWaitingForDataFromSlave(playerName)) {
                savePlayerAuthData(playerAuthDataMap.get(playerName));
            }
        }

        PlayerSyncBungee.getInstance().log("waitingForDataFromSlave.remove " + playerName);
        waitingForDataFromSlave.remove(playerName);
    }

    public void playerDisconnected (String playerName) {
        slaveRequestedData.remove(playerName);
        setAsOffline(playerName);
        if (playerAuthDataMap.containsKey(playerName)) {
            if (playerAuthDataMap.get(playerName).isLoggedIn() && playerAuthDataMap.get(playerName).isSlaveReceivedDataOk()) {
                PlayerSyncBungee.getInstance().log("waitingForDataFromSlave.add " + playerName);
                waitingForDataFromSlave.add(playerName);
            }
            playerAuthDataMap.get(playerName).setLoggedIn(false);
        }
    }

    public void savePlayers () {
        playerAuthDataMap.values().stream()
                .filter(PlayerAuthData::isNeedsSave)
                .forEach(this::savePlayerAuthData);
    }

    public void slaveRequestedData (String playerName) {
        slaveRequestedData.add(playerName);
    }

    public String fixUsername (String username) {
        if (username.endsWith("_")) {
            return username + "0";
        }
        return username;
    }
}
