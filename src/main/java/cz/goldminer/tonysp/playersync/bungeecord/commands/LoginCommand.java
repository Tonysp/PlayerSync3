package cz.goldminer.tonysp.playersync.bungeecord.commands;

import cz.goldminer.tonysp.playersync.bungeecord.auth.AuthenticationManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class LoginCommand extends Command {

    public LoginCommand(String command) {
        super(command);
    }

    @Override
    public void execute(CommandSender commandSender, String[] args) {
        if (commandSender instanceof ProxiedPlayer) {
            AuthenticationManager.getInstance().tryLogin((ProxiedPlayer) commandSender, args);
        }
    }
}
