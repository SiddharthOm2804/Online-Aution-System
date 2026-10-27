package com.auction.system.repository;

import com.auction.system.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, String> {
    List<Auction> findByActiveTrue();
    List<Auction> findBySellerId(String sellerId);
}
