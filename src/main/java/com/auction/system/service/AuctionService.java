package com.auction.system.service;

import com.auction.system.Auction;
import com.auction.system.Bid;
import com.auction.system.repository.AuctionRepository;
import com.auction.system.repository.BidRepository;
import com.auction.system.websocket.AuctionWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class AuctionService {
    private static final Logger logger = Logger.getLogger(AuctionService.class.getName());

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    // In-memory auto-bid registry: Map<AuctionId, Map<UserId, AutoBidSetting>>
    public static class AutoBidSetting {
        public double maxAmount;
        public double increment;

        public AutoBidSetting(double maxAmount, double increment) {
            this.maxAmount = maxAmount;
            this.increment = increment;
        }
    }

    private final Map<String, Map<String, AutoBidSetting>> autoBids = new ConcurrentHashMap<>();

    @Transactional
    public Auction createAuction(Auction auction) {
        if (auction.getSellerId() == null || auction.getSellerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Seller ID is required for creating an auction");
        }
        if (auction.getId() == null || auction.getId().trim().isEmpty()) {
            auction.setId(UUID.randomUUID().toString());
        }
        if (auction.getStartDate() == null) {
            auction.setStartDate(new Date());
        }
        auction.setHighestBid(auction.getStartingPrice());
        auction.setActive(true);

        Auction saved = auctionRepository.save(auction);
        logger.info("Saved auction to SQL DB: " + saved.getId() + " by " + saved.getSellerId());
        AuctionWebSocketHandler.broadcast("UPDATE_AUCTIONS");
        return saved;
    }

    public Map<String, Auction> getAllAuctions() {
        List<Auction> list = auctionRepository.findAll();
        Map<String, Auction> map = new LinkedHashMap<>();
        for (Auction a : list) {
            map.put(a.getId(), a);
        }
        return map;
    }

    public Auction getAuctionById(String auctionId) {
        return auctionRepository.findById(auctionId).orElse(null);
    }

    @Transactional
    public synchronized Bid placeBid(String auctionId, String bidderId, double amount) {
        if (bidderId == null || bidderId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for bidding");
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found"));

        if (!auction.isActive()) {
            throw new IllegalStateException("Auction is ended or inactive");
        }
        if (bidderId.equals(auction.getSellerId())) {
            throw new IllegalArgumentException("Seller cannot bid on their own auction");
        }
        if (amount <= auction.getHighestBid()) {
            throw new IllegalArgumentException("Bid amount must be greater than current highest bid ($" + auction.getHighestBid() + ")");
        }

        // Update auction
        auction.setHighestBid(amount);
        auction.setBidder(bidderId);
        auctionRepository.save(auction);

        // Record bid in SQL database
        Bid bid = new Bid(auctionId, bidderId, amount);
        Bid savedBid = bidRepository.save(bid);

        logger.info("Bid placed successfully in SQL: " + amount + " by " + bidderId);

        // Broadcast notifications
        AuctionWebSocketHandler.notifyBid(auctionId, bidderId, amount);
        AuctionWebSocketHandler.broadcast("UPDATE_AUCTIONS");

        // Trigger Auto-Bids asynchronously/in-turn
        evaluateAutoBids(auctionId, bidderId);

        return savedBid;
    }

    public void setAutoBid(String auctionId, String userId, double maxAmount, double increment) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for auto-bidding");
        }
        autoBids.computeIfAbsent(auctionId, k -> new ConcurrentHashMap<>())
                .put(userId, new AutoBidSetting(maxAmount, increment));
        logger.info("Auto-bid set for user " + userId + " on auction " + auctionId);
    }

    public void evaluateAutoBids(String auctionId, String currentBidderId) {
        Map<String, AutoBidSetting> bidders = autoBids.getOrDefault(auctionId, Collections.emptyMap());

        for (Map.Entry<String, AutoBidSetting> entry : bidders.entrySet()) {
            String userId = entry.getKey();
            AutoBidSetting setting = entry.getValue();

            if (!userId.equals(currentBidderId)) {
                Auction auction = getAuctionById(auctionId);
                if (auction != null && auction.isActive()) {
                    double nextBid = auction.getHighestBid() + setting.increment;
                    if (nextBid <= setting.maxAmount) {
                        try {
                            placeBid(auctionId, userId, nextBid);
                            break; // Process one auto-bid step to let others participate
                        } catch (Exception e) {
                            logger.warning("Auto-bid failed for " + userId + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
