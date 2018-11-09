package com.chromaclypse.chairs;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Door.Hinge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.chromaclypse.api.world.RelativeFace;

public class DoubleDoor implements Listener {
	
	@EventHandler
	public void onRestone(BlockRedstoneEvent event) {
		// All doors can be triggered by redstone
		Block doorBlock = event.getBlock();
		
		if(Tag.DOORS.isTagged(doorBlock.getType())) {
			Door door = (Door)doorBlock.getBlockData();
			Block sibling = getSiblingBlock(doorBlock);
			
			if(sibling.getBlockData() instanceof Door) {
				Door siblingDoor = (Door)sibling.getBlockData();
				
				if(siblingDoor.getHinge() != door.getHinge()) {
					// original door has not been toggled at this point
					
					if(event.getOldCurrent() == 0 && event.getNewCurrent() > 0) {
						siblingDoor.setOpen(true);
						
						sibling.setBlockData(siblingDoor);
					}
					else if(event.getOldCurrent() > 0 && event.getNewCurrent() == 0) {
						if(siblingDoor.isPowered()) {
							event.setNewCurrent(1);
							
							Bukkit.getScheduler().runTaskLater(Chairs.getPlugin(Chairs.class), () -> {
								
								if(doorBlock.getBlockData() instanceof Door) {
									door.setPowered(false);
									
									boolean open;
									
									if(sibling.getBlockData() instanceof Door) {
										Door newSiblingDoor = (Door)sibling.getBlockData();
										
										open = newSiblingDoor.isOpen() && newSiblingDoor.isPowered();
										
										if(!open) {
											newSiblingDoor.setOpen(false);
											newSiblingDoor.setPowered(false);
											
											sibling.setBlockData(newSiblingDoor);
										}
									}
									else {
										open = false;
									}
									
									door.setOpen(open);
									
									doorBlock.setBlockData(door);
								}
							}, 1);
						}
						else {
							siblingDoor.setOpen(false);
							
							sibling.setBlockData(siblingDoor);
						}
					}
				}
			}
		}
	}
	
	private static final Block getSiblingBlock(Block doorBlock) {
		Door door = (Door)doorBlock.getBlockData();
		BlockFace direction;
		
		if(door.getHinge() == Hinge.RIGHT) {
			direction = RelativeFace.toLeft(door.getFacing());
		}
		else {
			direction = RelativeFace.toRight(door.getFacing());
		}
		
		return doorBlock.getRelative(direction);
	}
	
	@EventHandler(ignoreCancelled=true)
	public void onClick(PlayerInteractEvent event) {
		// Only wood doors can be manually opened
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK &&
				!event.getPlayer().isSneaking() &&
				Tag.WOODEN_DOORS.isTagged(event.getClickedBlock().getType())) {
			Door door = (Door)event.getClickedBlock().getBlockData();
			
			Block sibling = getSiblingBlock(event.getClickedBlock());
			
			if(sibling.getBlockData() instanceof Door) {
				Door siblingDoor = (Door)sibling.getBlockData();
				
				if(siblingDoor.getHinge() != door.getHinge()) {
					// original door has not been toggled at this point
					siblingDoor.setOpen(!door.isOpen());
					sibling.setBlockData(siblingDoor);
				}
			}
		}
	}
}
