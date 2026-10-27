package com.auction.system.repository;

import com.auction.system.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByAuctionIdOrderByAmountDesc(String auctionId);
    List<Bid> findByBidderId(String bidderId);
}
