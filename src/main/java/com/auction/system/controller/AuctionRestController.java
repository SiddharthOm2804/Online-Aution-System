package com.auction.system.controller;

import com.auction.system.Auction;
import com.auction.system.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/auction")
@CrossOrigin(origins = "*")
public class AuctionRestController {

    @Autowired
    private AuctionService auctionService;

    @PostMapping("/add")
    public ResponseEntity<?> addAuction(@RequestBody AuctionRequest request) {
        try {
            Auction auction = new Auction();
            auction.setTitle(request.getTitle());
            auction.setDescription(request.getDescription());
            auction.setStartingPrice(request.getStartingPrice());
            auction.setSellerId(request.getSellerId());

            if (request.getEndDate() != null && !request.getEndDate().isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
                auction.setEndDate(sdf.parse(request.getEndDate()));
            }

            Auction created = auctionService.createAuction(auction);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating auction: " + e.getMessage());
        }
    }

    @GetMapping("/getAll")
    public ResponseEntity<Map<String, Auction>> getAllAuctions() {
        return ResponseEntity.ok(auctionService.getAllAuctions());
    }

    @GetMapping("/status/{auctionId}")
    public ResponseEntity<?> getAuctionStatus(@PathVariable("auctionId") String auctionId) {
        Auction auction = auctionService.getAuctionById(auctionId);
        if (auction == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Auction not found");
        }
        return ResponseEntity.ok(auction);
    }

    @PostMapping("/bid")
    public ResponseEntity<?> submitBid(@RequestBody BidRequest request) {
        try {
            auctionService.placeBid(request.getAuctionId(), request.getUserId(), request.getAmount());
            return ResponseEntity.ok("Bid submitted successfully");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error submitting bid: " + e.getMessage());
        }
    }

    @PostMapping("/autoBid")
    public ResponseEntity<?> setAutoBid(@RequestBody AutoBidRequest request) {
        try {
            auctionService.setAutoBid(
                    request.getAuctionId(),
                    request.getUserId(),
                    request.getMaxAmount(),
                    request.getIncrement()
            );
            return ResponseEntity.ok("Auto-bid settings saved successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error setting auto-bid: " + e.getMessage());
        }
    }

    public static class AuctionRequest {
        private String title;
        private String description;
        private double startingPrice;
        private String endDate;
        private String sellerId;

        public AuctionRequest() {}

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public double getStartingPrice() { return startingPrice; }
        public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }

        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    }

    public static class BidRequest {
        private String auctionId;
        private String userId;
        private double amount;

        public BidRequest() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
    }

    public static class AutoBidRequest {
        private String auctionId;
        private String userId;
        private double maxAmount;
        private double increment;

        public AutoBidRequest() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public double getMaxAmount() { return maxAmount; }
        public void setMaxAmount(double maxAmount) { this.maxAmount = maxAmount; }

        public double getIncrement() { return increment; }
        public void setIncrement(double increment) { this.increment = increment; }
    }
}
