package me.antonschouten.economy.Manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;

public final class AuctionManager {

	private static Player seller;
	private static ItemStack auctionItem;
	private static int buyNowPrice;
	private static int minBidPrice;

	private static final Map<UUID, Integer> currentBid = new HashMap<>();
	private static BukkitTask auctionTask;
	private static boolean auctionActive = false;

	private static final long AUCTION_DURATION = 20L * 60L * 3L;

	private AuctionManager() {
	}

	public static void AuctionHelpMessage(Player player) {
		player.sendMessage("");
		player.sendMessage("§8-------------- §6[Economy++ Auction] §8--------------");
		player.sendMessage("§a/auction sell <buy now price> <min bid> §8- §fSell your held item.");
		player.sendMessage("§a/auction bid <amount> §8- §fBid on the current auction.");
		player.sendMessage("§a/auction buynow §8- §fBuy the item immediately.");
		player.sendMessage("§a/auction info §8- §fView the current auction.");
		player.sendMessage("§8-------------- §6[Economy++ Auction] §8--------------");
		player.sendMessage("");
	}

	/**
	 * Creates a new auction using the item in the player's main hand.
	 */
	public static void AddNewItemToAuction(Player player, int BuyNowPrice, int MinBidPrice) {

		if (auctionActive) {
			player.sendMessage("§6§lAuction >> §cThere is already an active auction.");
			return;
		}

		if (BuyNowPrice <= 0 || MinBidPrice <= 0) {
			player.sendMessage("§6§lAuction >> §cPrices must be greater than zero.");
			return;
		}

		if (MinBidPrice > BuyNowPrice) {
			player.sendMessage("§6§lAuction >> §cThe minimum bid cannot exceed the buy now price.");
			return;
		}

		ItemStack itemInHand = player.getInventory().getItemInMainHand();

		if (itemInHand.getType().isAir()) {
			player.sendMessage("§6§lAuction >> §cYou must hold an item to sell it.");
			return;
		}

		// Store a copy and remove the original to prevent item duplication.
		auctionItem = itemInHand.clone();
		seller = player;
		buyNowPrice = BuyNowPrice;
		minBidPrice = MinBidPrice;

		player.getInventory().setItemInMainHand(null);

		currentBid.clear();
		auctionActive = true;

		player.sendMessage("§6§lAuction >> §aYour auction has started!");
		player.sendMessage("§6§lAuction >> §fItem: §b" + auctionItem.getAmount() + "x "
				+ auctionItem.getType().name().toLowerCase());
		player.sendMessage("§6§lAuction >> §fBuy now price: §b$" + buyNowPrice);
		player.sendMessage("§6§lAuction >> §fMinimum bid: §b$" + minBidPrice);
		player.sendMessage("§6§lAuction >> §fDuration: §e3 minutes");

		broadcast("§b" + player.getName() + " §chas started an auction for §b" + auctionItem.getAmount() + "x "
				+ auctionItem.getType().name().toLowerCase() + "§c. Buy now: §b$" + buyNowPrice
				+ " §c| Minimum bid: §b$" + minBidPrice);

		AuctionTimer();
	}

	/**
	 * Places a bid on the current auction.
	 */
	public static void BidOnAuction(Player player, int bidAmount) {

		if (!auctionActive || auctionItem == null) {
			player.sendMessage("§6§lAuction >> §cThere is no active auction.");
			return;
		}

		if (player.equals(seller)) {
			player.sendMessage("§6§lAuction >> §cYou cannot bid on your own auction.");
			return;
		}

		if (bidAmount < minBidPrice) {
			player.sendMessage("§6§lAuction >> §cYour bid is below the minimum bid of $" + minBidPrice + ".");
			return;
		}

		int highestBid = getHighestBid();

		if (bidAmount <= highestBid) {
			player.sendMessage("§6§lAuction >> §cYour bid must be higher than $" + highestBid + ".");
			return;
		}

		if (bidAmount >= buyNowPrice) {
			player.sendMessage("§6§lAuction >> §cUse /auction buynow to purchase this item immediately.");
			return;
		}

		if (EconomyManager.getBal(player) < bidAmount) {
			player.sendMessage("§6§lAuction >> §cYou don't have enough money to bid.");
			return;
		}

		currentBid.put(player.getUniqueId(), bidAmount);

		player.sendMessage("§6§lAuction >> §cYour bid of §b$" + bidAmount + " §chas been placed.");

		broadcast("§b" + player.getName() + " §chas bid §b$" + bidAmount + " §con §b"
				+ auctionItem.getType().name().toLowerCase() + "§c. Current highest bid: §b$" + bidAmount);
	}

	/**
	 * Purchases the item immediately at the buy now price.
	 */
	public static void BuyNow(Player player) {

		if (!auctionActive || auctionItem == null) {
			player.sendMessage("§6§lAuction >> §cThere is no active auction.");
			return;
		}

		if (player.equals(seller)) {
			player.sendMessage("§6§lAuction >> §cYou cannot buy your own auction.");
			return;
		}

		if (EconomyManager.getBal(player) < buyNowPrice) {
			player.sendMessage("§6§lAuction >> §cYou don't have enough money.");
			return;
		}

		finishAuction(player, buyNowPrice);
	}

	/**
	 * Starts a three-minute countdown.
	 */
	public static void AuctionTimer() {

		if (!auctionActive) {
			return;
		}

		if (auctionTask != null) {
			auctionTask.cancel();
		}

		auctionTask = Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Main.class), () -> {
			auctionTask = null;
			EndAuction();
		}, AUCTION_DURATION);
	}

	/**
	 * Ends the auction and awards the item to the highest bidder.
	 */
	public static void EndAuction() {

		if (!auctionActive || auctionItem == null) {
			return;
		}

		Player highestBidder = null;
		int highestBid = 0;

		for (Map.Entry<UUID, Integer> entry : currentBid.entrySet()) {
			Player bidder = Bukkit.getPlayer(entry.getKey());

			if (bidder != null && bidder.isOnline() && entry.getValue() > highestBid) {
				highestBid = entry.getValue();
				highestBidder = bidder;
			}
		}

		if (highestBidder == null) {
			broadcast("§cThe auction has ended without a valid online winner.");

			// Do not destroy the item if the seller is offline.
			if (seller != null && seller.isOnline()) {
				returnItem(seller, auctionItem);
				seller.sendMessage("§6§lAuction >> §cNo valid bids. Your item has been returned.");
				clearAuction();
			} else {
				broadcast("§cThe item is awaiting recovery because the seller is offline.");
			}

			return;
		}

		if (EconomyManager.getBal(highestBidder) < highestBid) {
			currentBid.remove(highestBidder.getUniqueId());

			broadcast("§cThe highest bidder no longer has enough money.");

			// Retry settlement using the remaining bids.
			EndAuction();
			return;
		}

		finishAuction(highestBidder, highestBid);
	}

	/**
	 * Processes the sale.
	 *
	 * IMPORTANT: Connect withdrawMoney() and depositMoney() to your actual
	 * EconomyManager methods before using this in production.
	 */
	private static void finishAuction(Player winner, int price) {

		if (!auctionActive || auctionItem == null || winner == null) {
			return;
		}

		if (!winner.isOnline() || !winner.getUniqueId().equals(winner.getUniqueId())) {
			return;
		}

		if (EconomyManager.getBal(winner) < price) {
			winner.sendMessage("§6§lAuction >> §cYou no longer have enough money.");
			return;
		}

		// Ensure the item can be delivered without losing it.
		if (winner.getInventory().firstEmpty() == -1) {
			winner.sendMessage("§6§lAuction >> §cYour inventory is full. Free a slot first.");
			return;
		}

		ItemStack soldItem = auctionItem.clone();
		Player auctionSeller = seller;

		if (!withdrawMoney(winner, price)) {
			winner.sendMessage("§6§lAuction >> §cPayment failed. The auction remains active.");
			return;
		}

		if (auctionSeller != null && auctionSeller.isOnline()) {
			if (!depositMoney(auctionSeller, price)) {
				// Refund the winner if seller payment fails.
				depositMoney(winner, price);
				winner.sendMessage("§6§lAuction >> §cPayment could not be completed.");
				return;
			}
		} else {
			// Seller payment cannot be safely completed while offline.
			// Refund the winner and leave the auction available for recovery.
			depositMoney(winner, price);
			winner.sendMessage("§6§lAuction >> §cThe seller is offline. Please try again later.");
			return;
		}

		winner.getInventory().addItem(soldItem);

		winner.sendMessage("§6§lAuction >> §aYou purchased the item for $" + price + "!");

		auctionSeller.sendMessage("§6§lAuction >> §aYour item sold for $" + price + "!");

		broadcast("§b" + winner.getName() + " §ahas purchased §b" + soldItem.getType().name().toLowerCase()
				+ " §afor §a$" + price + "§a.");

		clearAuction();
	}

	/**
	 * Displays the current auction.
	 */
	public static void AuctionInfo(Player player) {

		if (!auctionActive || auctionItem == null) {
			player.sendMessage("§6§lAuction >> §cThere is no active auction.");
			return;
		}

		player.sendMessage("§8-------------- §6[Economy++ Auction] §8--------------");
		player.sendMessage("§fSeller: §b" + seller.getName());
		player.sendMessage("§fItem: §b" + auctionItem.getAmount() + "x " + auctionItem.getType().name().toLowerCase());
		player.sendMessage("§fBuy now: §a$" + buyNowPrice);
		player.sendMessage("§fMinimum bid: §a$" + minBidPrice);
		player.sendMessage("§fCurrent highest bid: §a$" + getHighestBid());
		player.sendMessage("§8-----------------------------------------------");
	}

	private static int getHighestBid() {
		return currentBid.values().stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	private static void returnItem(Player player, ItemStack item) {
		Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
	}

	private static void broadcast(String message) {
		for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
			onlinePlayer.sendMessage("§6§lAuction >> " + message);
		}
	}

	/**
	 * Replace these two methods with your actual EconomyManager API. They must
	 * return true only when the transaction succeeds.
	 */
	private static boolean withdrawMoney(Player player, int amount) {
		EconomyManager.removeBal(player, amount);
		return false;
	}

	private static boolean depositMoney(Player player, int amount) {
		EconomyManager.addBal(player, amount);
		return false;
	}

	/**
	 * Cancels the timer and resets the active auction.
	 */
	private static void clearAuction() {

		if (auctionTask != null) {
			auctionTask.cancel();
			auctionTask = null;
		}

		seller = null;
		auctionItem = null;
		buyNowPrice = 0;
		minBidPrice = 0;
		currentBid.clear();
		auctionActive = false;
	}
}