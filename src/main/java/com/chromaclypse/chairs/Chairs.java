package com.chromaclypse.chairs;

import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import com.chromaclypse.api.command.CommandBase;
import com.chromaclypse.api.command.Context;

public class Chairs extends JavaPlugin {
	private InteractListener inter;
	
	@Override
	public void onEnable() {
		inter = new InteractListener(this);
		
		TabExecutor ch = new CommandBase()
				.calls(this::helpCommand)
				.with().arg("reload").calls(this::reloadCommand)
				.with().arg("version").calls(CommandBase::pluginVersion)
				.getCommand();

		getCommand("chairs").setExecutor(ch);
		getCommand("chairs").setTabCompleter(ch);
		this.getServer().getPluginManager().registerEvents(inter, this);
		this.getServer().getPluginManager().registerEvents(new DoubleDoor(), this);
	}
	
	@Override
	public void onDisable() {
		HandlerList.unregisterAll(this);
		
		for(Player player : getServer().getOnlinePlayers()) {
			InteractListener.attemptCleanup(player.getVehicle());
		}
	}
	
	private boolean helpCommand(Context context) {
		context.Sender().sendMessage("Chairs: /"+context.Alias()+" reload");
		return true;
	}
	
	private boolean reloadCommand(Context context) {
		inter.config.init(this);
		context.Sender().sendMessage("Chairs: Reloaded config!");
		return true;
	}
}
