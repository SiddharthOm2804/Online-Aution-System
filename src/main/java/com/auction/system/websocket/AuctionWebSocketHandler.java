package com.auction.system.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Component
public class AuctionWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = Logger.getLogger(AuctionWebSocketHandler.class.getName());
    private static final Map<WebSocketSession, String> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket Connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            if (payload.startsWith("LOGIN:")) {
                String userId = payload.substring(6);
                sessionUserMap.put(session, userId);
                broadcast("👤 " + userId + " joined the auction.");
                logger.info("User " + userId + " logged in via WebSocket");
            } else if (payload.equals("LOGOUT")) {
                String userId = sessionUserMap.remove(session);
                if (userId != null) {
                    broadcast(userId + " left the auction.");
                    logger.info("User " + userId + " logged out via WebSocket");
                }
            } else if (payload.startsWith("BID:")) {
                broadcast(payload.substring(4));
            }
        } catch (Exception e) {
            logger.severe("Error processing WebSocket message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionUserMap.remove(session);
        if (userId != null) {
            broadcast(userId + " disconnected.");
            logger.info("User " + userId + " disconnected");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.severe("WebSocket error for session " + session.getId() + ": " + exception.getMessage());
        sessionUserMap.remove(session);
    }

    public static void broadcast(String message) {
        sessionUserMap.keySet().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.severe("Error broadcasting message: " + e.getMessage());
            }
        });
    }

    public static void notifyBid(String auctionId, String userId, double amount) {
        broadcast("BID: New bid on auction " + auctionId + " by " + userId + " for $" + amount);
    }
}
