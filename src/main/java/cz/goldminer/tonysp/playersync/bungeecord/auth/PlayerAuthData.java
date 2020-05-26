package cz.goldminer.tonysp.playersync.bungeecord.auth;

import cz.goldminer.tonysp.playersync.PlayerData;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;

public class PlayerAuthData {

    private String playerName, passwordHash, salt;
    private ArrayList<String> ipList = new ArrayList<>();
    private boolean rememberIp = false, loggedIn = false, registered = false, passwordSet = false, rememberIpSet = false, slaveReceivedDataOk = false, needsSave = false;
    private PlayerData playerData = null;

    public String getPeriodicalMessage () {
        if (loggedIn) {
            return null;
        } else if (!registered) {
            return ChatColor.RED + "Zaregistruj se prikazem /register [heslo] [email]";
        } else if (!passwordSet) {
            return ChatColor.RED + "Resetuj si heslo na https://goldminer.cz/login";
        } else {
            return ChatColor.RED + "Prihlas se prikazem /login heslo";
        }
    }

    public boolean isIpRemembered (String ip) {
        return hasRememberIp() && getIpList().contains(ip);
    }

    public String getPlayerName () {
        return playerName;
    }

    public void setPlayerName (String playerName) {
        this.playerName = playerName;
    }

    public PlayerData getPlayerData () {
        return playerData;
    }

    public void setPlayerData (PlayerData playerData) {
        this.playerData = playerData;
    }

    public boolean isSlaveReceivedDataOk () {
        return slaveReceivedDataOk;
    }

    public void setSlaveReceivedDataOk (boolean slaveReceivedDataOk) {
        this.slaveReceivedDataOk = slaveReceivedDataOk;
    }

    public boolean isNeedsSave () {
        return needsSave;
    }

    public void setNeedsSave (boolean needsSave) {
        this.needsSave = needsSave;
    }

    public ArrayList<String> getIpList () {
        return ipList;
    }

    public void setIpList (ArrayList<String> ipList) {
        this.ipList = ipList;
    }

    public boolean hasRememberIp () {
        return rememberIp;
    }

    public void setRememberIp (boolean rememberIp) {
        this.rememberIp = rememberIp;
    }

    public boolean isLoggedIn () {
        return loggedIn;
    }

    public void setLoggedIn (boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean isRegistered () {
        return registered;
    }

    public void setRegistered (boolean registered) {
        this.registered = registered;
    }

    public boolean isPasswordSet () {
        return passwordSet;
    }

    public void setPasswordSet (boolean passwordSet) {
        this.passwordSet = passwordSet;
    }

    public void setPasswordHash (String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt () {
        return salt;
    }

    public void setSalt (String salt) {
        this.salt = salt;
    }

    public String getPasswordHash () {
        return passwordHash;
    }
}
