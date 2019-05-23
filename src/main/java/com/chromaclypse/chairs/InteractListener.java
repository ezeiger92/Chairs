package com.chromaclypse.chairs;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Stairs.Shape;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.entity.EntityDismountEvent;

public class InteractListener implements Listener {
	private static final String MD_KEY = "sitting:chair";
	private FixedMetadataValue chairMeta;
	
	public SeatConfig config = new SeatConfig();
	
	public InteractListener(Plugin handle) {
		chairMeta = new FixedMetadataValue(handle, true);
		config.init(handle);
	}
	
	private static EnumSet<Material> blacklist = EnumSet.of(
			Material.BOW, Material.TRIDENT, Material.FLINT_AND_STEEL, Material.FLINT,
			Material.FIRE_CHARGE, Material.BUCKET, Material.COD_BUCKET, Material.LAVA_BUCKET,
			Material.PUFFERFISH_BUCKET, Material.SALMON_BUCKET, Material.TROPICAL_FISH_BUCKET,
			Material.WATER_BUCKET, Material.FISHING_ROD, Material.STRING, Material.GLASS_BOTTLE,
			Material.ACACIA_BOAT, Material.BIRCH_BOAT, Material.DARK_OAK_BOAT,
			Material.JUNGLE_BOAT, Material.OAK_BOAT, Material.SPRUCE_BOAT, Material.MINECART,
			Material.CHEST_MINECART, Material.COMMAND_BLOCK_MINECART, Material.FURNACE_MINECART,
			Material.HOPPER_MINECART, Material.TNT_MINECART, Material.ARMOR_STAND,
			Material.ITEM_FRAME, Material.PAINTING);
	
	private boolean allowInteractSeat(ItemStack hand) {
		Material material = hand.getType();
		
		if(material == Material.AIR) {
			return true;
		}
		
		if(material.isEdible() || material.isBlock()) {
			return false;
		}
		
		return !config.strictInteract || !blacklist.contains(material);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void onClick(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK &&
				allowInteractSeat(event.getPlayer().getInventory().getItemInMainHand()) &&
				!event.getPlayer().isSneaking() &&
				block.getRelative(BlockFace.DOWN).getType().isSolid()) {
			
			Stairs s = asChair(block);
			
			if(s != null && !isChairInUse(block)) {
				
				// Direction faced as a chair (descending stair)
				BlockFace facing = s.getFacing().getOppositeFace();
				Location location = block.getLocation();
				
				// Abort if part of a staircase
				if(block.getRelative(facing).getRelative(BlockFace.DOWN).getBlockData() instanceof Stairs ||
						block.getRelative(facing.getOppositeFace()).getRelative(BlockFace.UP).getBlockData() instanceof Stairs) {
					return;
				}
				
				location.add(0.5, 0.3, 0.5);
				
				location.setYaw(getYawFromStair(s));

				if(s.getShape() == Shape.INNER_RIGHT || s.getShape() == Shape.INNER_LEFT) {
					location.add(location.getDirection().multiply(0.2));
				}

				ArmorStand mount = asChairMount(event.getPlayer().getVehicle());
				
				if(mount != null) {
					//return;
					moveMount(mount, location);
					event.setCancelled(true);
				}
				else {
					mount = makeMount(location);
					
					if(mount != null) {
						event.setCancelled(true);
						mount.addPassenger(event.getPlayer());
					}
				}
			}
		}
	}
	
	private ArmorStand makeMount(Location location) {
		return location.getWorld().spawn(location, ArmorStand.class, as -> {
			as.setMarker(true);
			as.setVisible(false);
			as.setInvulnerable(true);
			as.setGravity(false);
			as.setMetadata(MD_KEY, chairMeta);
			as.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(0);
		});
	}
	
	private float getYawFromStair(Stairs stair) {
		BlockFace facing = stair.getFacing().getOppositeFace();
		float yaw;
		
		//Handle base direction
		switch(facing) {
			case NORTH:
				yaw = 180.f;
				break;
			case EAST:
				yaw = 270.f;
				break;
			case SOUTH:
				yaw = 0.f;
				break;
			case WEST:
				yaw = 90.f;
				break;
			default:
				yaw = 66.7f;
				break;
		}
		
		//Modify yaw based on shape
		switch(stair.getShape()) {
			case INNER_LEFT:
			case OUTER_LEFT:
				yaw -= 45;
				
				if(yaw < 0) {
					yaw += 360;
				}
				break;
			case INNER_RIGHT:
			case OUTER_RIGHT:
				yaw += 45;
				
				if(yaw >= 360) {
					yaw -= 360;
				}
				break;
			case STRAIGHT:
				break;
		}
		
		return yaw;
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void exitSeat(EntityDismountEvent event) {
		
		ArmorStand as = asChairMount(event.getDismounted());
		
		if(as != null) {
			Entity rider = event.getEntity();
			Block chairBlock = as.getWorld().getBlockAt(as.getLocation());
			Stairs chair = asChair(chairBlock);
			
			if(chair != null) {
				BlockFace facing = chair.getFacing().getOppositeFace();
				BlockFace side;
				
				switch(facing) {
					case NORTH:
					case SOUTH:
						side = BlockFace.EAST;
						break;
						
					default:
						side = BlockFace.NORTH;
						break;
				}
				
				BlockFace[] faces = {
						facing,
						side,
						side.getOppositeFace(),
						facing.getOppositeFace(),
						BlockFace.UP,
				};
				
				for(BlockFace face : faces) {
					Block nearby = chairBlock.getRelative(face);
					if(canDismountAt(nearby)) {
						rider.teleport(nearby.getLocation().add(0.5, 0.0, 0.5).setDirection(rider.getLocation().getDirection()));
						break;
					}
				}
				
				as.remove();
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Stairs chair = asChair(event.getBlock(), false);
		
		if(chair != null) {
			ArmorStand mount = getChairMount(event.getBlock());
			
			if(mount != null) {
				Location target = event.getBlock().getLocation().add(0.5, 0.0, 0.5);
				
				for(Entity rider : mount.getPassengers()) {
					rider.teleport(target.setDirection(rider.getLocation().getDirection()));
				}
				
				mount.remove();
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonPush(BlockPistonExtendEvent event) {
		handlePiston(event.getDirection(), event.getBlocks());
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonPull(BlockPistonRetractEvent event) {
		handlePiston(event.getDirection(), event.getBlocks());
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onDisconnect(PlayerQuitEvent event) {
		attemptCleanup(event.getPlayer().getVehicle());
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onKicked(PlayerKickEvent event) {
		attemptCleanup(event.getPlayer().getVehicle());
	}
	
	// Utilities
	void handlePiston(BlockFace direction, List<Block> blocks) {
		for(Block block : blocks) {
			Stairs chair = asChair(block, false);
			
			
			if(chair != null) {
				ArmorStand mount = getChairMount(block);
				
				if(mount != null) {
					moveMount(mount, mount.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ()));
					break;
				}
			}
		}
	}
	
	static void attemptCleanup(Entity entity) {
		ArmorStand as = asChairMount(entity);
		
		if(as != null) {
			as.remove();
		}
	}
	
	static ArmorStand asChairMount(Entity entity) {
		if(entity instanceof ArmorStand) {
			ArmorStand as = (ArmorStand)entity;
			
			if(!as.getMetadata(MD_KEY).isEmpty())
				return as;
		}
		
		return null;
	}
	
	static Stairs asChair(Block block) {
		return asChair(block, true);
	}
	
	static Stairs asChair(Block block, boolean needHeadSpace) {
		if(!needHeadSpace || !isSolid(block.getRelative(BlockFace.UP).getType())) {
			
			BlockData data = block.getBlockData();
			
			if(data instanceof Stairs && ((Stairs)data).getHalf() != Half.TOP) {
				return (Stairs)data;
			}
		}
		
		return null;
	}
	
	static ArmorStand getChairMount(Block chair) {
		Location location = chair.getLocation().add(0.5, 0.0, 0.5);
		for(Entity entity : location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5)) {
			ArmorStand mount = asChairMount(entity);
			if(mount != null) {
				return mount;
			}
		}
		
		return null;
	}
	
	static boolean isChairInUse(Block chair) {
		return getChairMount(chair) !=  null;
	}
	
	static boolean canDismountAt(Block feetPos) {
		Block standOn = feetPos.getRelative(BlockFace.DOWN);
		
		return !isSolid(feetPos.getType()) &&
				!isSolid(feetPos.getRelative(BlockFace.UP).getType()) &&
				(isSolid(standOn.getType()) || isSolid(standOn.getRelative(BlockFace.DOWN).getType()));
	}
	
	private static final EnumSet<Material> nonSolid = EnumSet.of(Material.SIGN, Material.WALL_SIGN);
	
	static boolean isSolid(Material material) {
		
		if(nonSolid.contains(material)) {
			return false;
		}
		
		return material.isSolid();
	}
	
	void moveMount(ArmorStand mount, Location location) {
		ArmorStand newMount = makeMount(location);
		List<Entity> oldPassengers = new ArrayList<>(mount.getPassengers());
		
		mount.remove();
		
		for(Entity rider : oldPassengers) {
			newMount.addPassenger(rider);
		}
	}
}
