package com.chromaclypse.chairs;

import java.util.EnumSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
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
	private Plugin handle;
	private static final String MD_KEY = "sitting:chair";
	private FixedMetadataValue chairMeta;
	
	public SeatConfig config = new SeatConfig();
	
	public InteractListener(Plugin handle) {
		this.handle = handle;
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
				
				switch(facing) {
					case NORTH:
						location.setYaw(180.f);
						break;
					case EAST:
						location.setYaw(270.f);
						break;
					case SOUTH:
						location.setYaw(0.f);
						break;
					case WEST:
						location.setYaw(90.f);
						break;
					default:
				}

				ArmorStand mount = asChairMount(event.getPlayer().getVehicle());
				
				if(mount != null) {
					moveMount(mount, location);
					event.setCancelled(true);
				}
				else {
					mount = event.getClickedBlock().getWorld().spawn(location, ArmorStand.class, as -> {
						as.setMarker(true);
						as.setVisible(false);
						as.setInvulnerable(true);
						as.setGravity(false);
						as.setMetadata(MD_KEY, chairMeta);
					});
					
					if(mount != null) {
						event.setCancelled(true);
						mount.addPassenger(event.getPlayer());
					}
				}
			}
		}
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
						Bukkit.getScheduler().runTask(handle, () -> {
							rider.teleport(nearby.getLocation().add(0.5, 0.0, 0.5).setDirection(rider.getLocation().getDirection()));
						});
						
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
				Bukkit.getScheduler().runTask(handle, () -> {
					// We only put 1 rider on a seat
					Entity rider = mount.getPassengers().get(0);
					rider.teleport(event.getBlock().getLocation().add(0.5, 0.0, 0.5).setDirection(rider.getLocation().getDirection()));
				});
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
		Entity rider = mount.getPassengers().get(0);
		
		mount.removeMetadata(MD_KEY, handle);
		mount.eject();
		mount.teleport(location);

		mount.addPassenger(rider);
		mount.setMetadata(MD_KEY, chairMeta);
	}
}
