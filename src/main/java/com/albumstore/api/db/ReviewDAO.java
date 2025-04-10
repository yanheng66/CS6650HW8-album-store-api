package com.albumstore.api.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ReviewDAO {
    private static final Logger LOGGER = LogManager.getLogger(ReviewDAO.class);

    /**
     * 获取专辑喜欢数量
     */
    public int getLikesCount(String albumId) {
        // 使用合并查询方法
        return getReviewStats(albumId).get("like");
    }

    /**
     * 获取专辑不喜欢数量
     */
    public int getDislikesCount(String albumId) {
        // 使用合并查询方法
        return getReviewStats(albumId).get("dislike");
    }

    /**
     * 获取专辑的所有评论统计信息（一次查询获取所有类型数量）
     * 使用组合索引idx_album_review进行优化
     */
    public Map<String, Integer> getReviewStats(String albumId) {
        String sql = "SELECT review_type, COUNT(*) AS count FROM album_reviews " +
                "WHERE album_id = ? GROUP BY review_type";

        // 初始化结果Map，设置默认值
        Map<String, Integer> stats = new HashMap<>();
        stats.put("like", 0);
        stats.put("dislike", 0);

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, albumId);

            // 执行查询并处理结果
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("review_type");
                    int count = rs.getInt("count");
                    stats.put(type, count);
                }
            }

            LOGGER.debug("Retrieved review stats for album {}: likes={}, dislikes={}",
                    albumId, stats.get("like"), stats.get("dislike"));
            return stats;

        } catch (SQLException e) {
            LOGGER.error("Error getting review stats for album: {}", albumId, e);
            return stats;  // 出错时返回初始化的默认值
        }
    }

    /**
     * 获取评论计数 (此方法保留以兼容旧代码，但内部实现已优化)
     */
    private int getReviewCount(String albumId, String reviewType) {
        return getReviewStats(albumId).get(reviewType);
    }
}