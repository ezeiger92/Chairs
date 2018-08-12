package com.chromaclypse.chairs;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class Chairs extends JavaPlugin {
	
	@Override
	public void onEnable() {
		this.getServer().getPluginManager().registerEvents(new InteractListener(this), this);
	}
	
	@Override
	public void onDisable() {
		HandlerList.unregisterAll(this);
		
		for(Player player : getServer().getOnlinePlayers()) {
			InteractListener.attemptCleanup(player.getVehicle());
		}
	}
}
