package me.antonschouten.economy.Commands.Auction;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Manager.AuctionManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public class AuctionCMD implements CommandExecutor {

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args) {

        // Only players can use auction commands.
        Player player = CommandUtils.requirePlayer(sender);

        if (player == null) {
            return true;
        }

        // Check permission.
        if (!CommandUtils.requirePermission(player, Perms.AUCTION)) {
            return true;
        }

        // Show help.
        if (args.length == 0) {
            AuctionManager.AuctionHelpMessage(player);
            return true;
        }

        // /auction buynow
        if (args.length == 1 && args[0].equalsIgnoreCase("buynow")) {
            AuctionManager.BuyNow(player);
            return true;
        }

        // /auction info
        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            AuctionManager.AuctionInfo(player);
            return true;
        }

        // /auction bid <amount>
        if (args.length == 2 && args[0].equalsIgnoreCase("bid")) {

            Integer bidAmount = parsePositiveInteger(args[1]);

            if (bidAmount == null) {
                player.sendMessage(
                    "§6§lAuction >> §cPlease enter a valid positive bid amount."
                );
                return true;
            }
            
            AuctionManager.BidOnAuction(player, bidAmount);
            return true;
        }


        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {

            Integer buyNowPrice = parsePositiveInteger(args[1]);
            Integer minBidPrice = parsePositiveInteger(args[2]);

            if (buyNowPrice == null || minBidPrice == null) {
                player.sendMessage(
                    "§6§lAuction >> §cPrices must be valid positive whole numbers."
                );
                player.sendMessage(
                    "§6§lAuction >> §7Usage: §b/auction sell <buy now price> <minimum bid>"
                );
                return true;
            }

            AuctionManager.AddNewItemToAuction(
                player,
                buyNowPrice,
                minBidPrice
            );

            return true;
        }

        AuctionManager.AuctionHelpMessage(player);
        return true;
    }

    
    private Integer parsePositiveInteger(String input) {
        try {
            int value = Integer.parseInt(input);

            if (value <= 0) {
                return null;
            }

            return value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}