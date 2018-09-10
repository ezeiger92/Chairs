package com.chromaclypse.chairs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class Chairs extends JavaPlugin {
	private InteractListener inter;
	
	@Override
	public void onEnable() {
		inter = new InteractListener(this);
		getCommand("chairs").setExecutor(this);
		this.getServer().getPluginManager().registerEvents(inter, this);
	}
	
	@Override
	public void onDisable() {
		HandlerList.unregisterAll(this);
		
		for(Player player : getServer().getOnlinePlayers()) {
			InteractListener.attemptCleanup(player.getVehicle());
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] parameters) {
		if(parameters.length == 0) {
			sender.sendMessage("Chairs: /"+alias+" reload");
		}
		else {
			switch(parameters[0]) {
				case "reload":
					inter.config.init(this);
					sender.sendMessage("Chairs: Reloaded config!");
					break;

				default:
					sender.sendMessage("Chairs: /"+alias+" reload");
					break;
			}
		}
		return true;
	}
}
