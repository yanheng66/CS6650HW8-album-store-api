package com.albumstore.api.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReviewDAO {
    private static final Logger LOGGER = LogManager.getLogger(ReviewDAO.class);

    /**
     * 获取专辑喜欢数量
     */
    public int getLikesCount(String albumId) {
        return getReviewCount(albumId, "like");
    }

    /**
     * 获取专辑不喜欢数量
     */
    public int getDislikesCount(String albumId) {
        return getReviewCount(albumId, "dislike");
    }

    /**
     * 获取评论计数
     */
    private int getReviewCount(String albumId, String reviewType) {
        String sql = "SELECT COUNT(*) FROM album_reviews WHERE album_id = ? AND review_type = ?";

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, albumId);
            pstmt.setString(2, reviewType);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    LOGGER.debug("Count of {} for album {}: {}", reviewType, albumId, count);
                    return count;
                }
            }

            return 0;

        } catch (SQLException e) {
            LOGGER.error("Error getting {} count for album: {}", reviewType, albumId, e);
            return 0;
        }
    }
}