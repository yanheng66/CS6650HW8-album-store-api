package com.albumstore;

import com.albumstore.api.db.DBConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@WebListener
public class AppInitializer implements ServletContextListener {
    private static final Logger LOGGER = LogManager.getLogger(AppInitializer.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("Initializing Album Store API application");

        try {
            // 确保数据库表存在
            createTablesIfNotExist();

            LOGGER.info("Album Store API application initialized successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to initialize Album Store API application", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("Shutting down Album Store API application");

        try {
            // 关闭数据库连接池
            DBConnectionPool.closePool();

            LOGGER.info("Album Store API application shutdown completed");

        } catch (Exception e) {
            LOGGER.error("Error during application shutdown", e);
        }
    }

    /**
     * 创建必要的数据库表
     */
    private void createTablesIfNotExist() {
        try (Connection conn = DBConnectionPool.getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建专辑表
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS albums (" +
                            "id VARCHAR(255) PRIMARY KEY, " +
                            "artist VARCHAR(255) NOT NULL, " +
                            "title VARCHAR(255) NOT NULL, " +
                            "year VARCHAR(50) NOT NULL, " +
                            "image_data MEDIUMBLOB NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // 创建评论表
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS album_reviews (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "album_id VARCHAR(255) NOT NULL, " +
                            "review_type ENUM('like', 'dislike') NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (album_id) REFERENCES albums(id))"
            );

            LOGGER.info("Database tables created or already exist");
        } catch (SQLException e) {
            LOGGER.error("Failed to create database tables", e);
            throw new RuntimeException("Failed to create database tables", e);
        }
    }
}