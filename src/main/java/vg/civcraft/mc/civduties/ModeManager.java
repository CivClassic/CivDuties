package vg.civcraft.mc.civduties;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.minecraft.server.v1_16_R1.NBTTagCompound;
import vg.civcraft.mc.civduties.configuration.Command;
import vg.civcraft.mc.civduties.configuration.Command.Timing;
import vg.civcraft.mc.civduties.configuration.Tier;
import vg.civcraft.mc.civduties.database.DatabaseManager;
import vg.civcraft.mc.civduties.external.VaultManager;
import vg.civcraft.mc.civmodcore.serialization.NBTCompound;

public class ModeManager {
	private DatabaseManager db;
	private VaultManager vaultManager;
	private Logger logger;

	public ModeManager() {
		db = CivDuties.getInstance().getDatabaseManager();
		vaultManager = CivDuties.getInstance().getVaultManager();
		logger = CivDuties.getInstance().getLogger();
	}

	public boolean isInDuty(UUID uuid) {
		if (db.getPlayerData(uuid) != null) {
			return true;
		}
		return false;
	}

	public boolean isInDuty(Player player) {
		return isInDuty(player.getUniqueId());
	}

	public boolean enableDutyMode(Player player, Tier tier) {
		NBTTagCompound nmsCompound = new NBTTagCompound();
		CraftPlayer cPlayer = (CraftPlayer) player;
		cPlayer.getHandle().save(nmsCompound);
		NBTCompound compound = new NBTCompound(nmsCompound);
		compound.setString("worldUUID", player.getWorld().getUID().toString());
		String serverName = Bukkit.getServer().getName();
		db.savePlayerData(player.getUniqueId(), compound, serverName, tier.getName());

		vaultManager.addPermissionsToPlayer(player, tier.getTemporaryPermissions());
		vaultManager.addPlayerToGroups(player, tier.getTemporaryGroups());

		for (Command command : tier.getCommands()) {
			if (command.getTiming() == Timing.ENABLE) {
				command.execute(player);
			}
		}

		player.sendMessage(ChatColor.RED + "You have entered duty mode. Type /duty to leave it");
		logger.log(Level.INFO, "player " + player.getName() + " has entered duty mode");
		return true;
	}

	public boolean disableDutyMode(Player player, Tier tier) {
		if (!isInDuty(player)) {
			return false;
		}
		NBTCompound input = db.getPlayerData(player.getUniqueId()).getData();
		// Teleport the players using the bukkit api to avoid triggering nocheat
		// movement detection
		double [] location = input.getDoubleArray("Pos");
		UUID worldUUID = UUID.fromString(input.getString("worldUUID"));
		Bukkit.getScheduler().scheduleSyncDelayedTask(CivDuties.getInstance(), () -> {
			player.teleport(new Location(Bukkit.getWorld(worldUUID), location [0], location[1], location[2]));
			// Inform the client the gamemode was changed to fix graphical issues on the client side
			player.setGameMode(getGameModeByValue(input.getInteger("playerGameType")));
		}, 3L);
		

		CraftPlayer cPlayer = (CraftPlayer) player;
		cPlayer.getHandle().loadData(input.getRAW());

		db.removePlayerData(player.getUniqueId());

		for (Command command : tier.getCommands()) {
			if (command.getTiming() == Timing.DISABLE) {
				command.execute(player);
			}
		}

		vaultManager.removePermissionsFromPlayer(player, tier.getTemporaryPermissions());
		vaultManager.removePlayerFromGroups(player, tier.getTemporaryGroups());

		player.sendMessage(ChatColor.RED + "You have left duty mode");
		logger.log(Level.INFO, "player " + player.getName() + " has left duty mode");
		return true;
	}

	private GameMode getGameModeByValue(int num) {
		switch (num) {
		case 0:
			return GameMode.SURVIVAL;
		case 1:
			return GameMode.CREATIVE;
		case 2:
			return GameMode.ADVENTURE;
		case 3:
			return GameMode.SPECTATOR;
		default:
			return null;
		}
	}

}
