// AlbumDAO.java
package com.albumstore.api.db;

import com.albumstore.api.model.AlbumInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.UUID;

public class AlbumDAO {
    private static final Logger LOGGER = LogManager.getLogger(AlbumDAO.class);

    /**
     * 保存专辑信息和图片
     */
    public String saveAlbum(AlbumInfo albumInfo, byte[] imageData) {
        String sql = "INSERT INTO albums (id, artist, title, year, image_data) VALUES (?, ?, ?, ?, ?)";
        String albumId = generateUniqueAlbumId();

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, albumId);
            pstmt.setString(2, albumInfo.getArtist());
            pstmt.setString(3, albumInfo.getTitle());
            pstmt.setString(4, albumInfo.getYear());
            pstmt.setBytes(5, imageData);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Album saved successfully with ID: {}", albumId);
                return albumId;
            } else {
                LOGGER.error("Failed to save album: {}", albumInfo);
                return null;
            }

        } catch (SQLException e) {
            LOGGER.error("Error saving album", e);

            // 尝试使用新ID重新保存，但仅限于主键冲突的情况
            if (e instanceof SQLIntegrityConstraintViolationException) {
                LOGGER.warn("Primary key conflict detected, retrying with new ID");
                return retryWithNewId(albumInfo, imageData);
            }

            return null;
        }
    }

    /**
     * 使用新ID重试保存操作
     */
    private String retryWithNewId(AlbumInfo albumInfo, byte[] imageData) {
        String sql = "INSERT INTO albums (id, artist, title, year, image_data) VALUES (?, ?, ?, ?, ?)";
        String newAlbumId = generateUniqueAlbumId();

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newAlbumId);
            pstmt.setString(2, albumInfo.getArtist());
            pstmt.setString(3, albumInfo.getTitle());
            pstmt.setString(4, albumInfo.getYear());
            pstmt.setBytes(5, imageData);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Album saved successfully with new ID: {}", newAlbumId);
                return newAlbumId;
            } else {
                LOGGER.error("Failed to save album after retry: {}", albumInfo);
                return null;
            }

        } catch (SQLException e) {
            LOGGER.error("Error saving album during retry", e);
            return null;
        }
    }

    /**
     * 获取专辑信息
     */
    public AlbumInfo getAlbumById(String albumId) {
        String sql = "SELECT artist, title, year FROM albums WHERE id = ?";

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, albumId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String artist = rs.getString("artist");
                    String title = rs.getString("title");
                    String year = rs.getString("year");

                    AlbumInfo albumInfo = new AlbumInfo(artist, title, year);
                    LOGGER.info("Retrieved album info: {}", albumInfo);
                    return albumInfo;
                }
            }

            LOGGER.warn("Album not found with ID: {}", albumId);
            return null;

        } catch (SQLException e) {
            LOGGER.error("Error getting album with ID: {}", albumId, e);
            return null;
        }
    }

    /**
     * 检查专辑是否存在
     */
    public boolean albumExists(String albumId) {
        String sql = "SELECT 1 FROM albums WHERE id = ?";

        try (Connection conn = DBConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, albumId);

            try (ResultSet rs = pstmt.executeQuery()) {
                boolean exists = rs.next();
                LOGGER.debug("Album exists check for ID {}: {}", albumId, exists);
                return exists;
            }

        } catch (SQLException e) {
            LOGGER.error("Error checking album existence for ID: {}", albumId, e);
            return false;
        }
    }

    /**
     * 生成唯一的专辑ID - 使用UUID确保唯一性
     */
    private String generateUniqueAlbumId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}