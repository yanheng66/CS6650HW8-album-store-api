package com.albumstore.api.db;

import com.albumstore.api.config.AppConfig;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

public class DBConnectionPool {
    private static final Logger LOGGER = LogManager.getLogger(DBConnectionPool.class);
    private static BasicDataSource dataSource;

    static {
        try {
            AppConfig config = AppConfig.getInstance();

            // 初始化连接池
            dataSource = new BasicDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(config.getDbUrl());
            dataSource.setUsername(config.getDbUsername());
            dataSource.setPassword(config.getDbPassword());

            // 连接池配置
            dataSource.setInitialSize(config.getDbInitialSize());
            dataSource.setMaxTotal(config.getDbMaxTotal());
            dataSource.setMaxIdle(config.getDbMaxIdle());
            dataSource.setMinIdle(config.getDbMinIdle());
            dataSource.setMaxWaitMillis(30000);

            LOGGER.info("Database connection pool initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = dataSource.getConnection();
            LOGGER.debug("Database connection obtained");
            return conn;
        } catch (SQLException e) {
            LOGGER.error("Failed to get database connection", e);
            throw e;
        }
    }

    /**
     * 关闭数据库连接池
     */
    public static void closePool() {
        try {
            if (dataSource != null) {
                dataSource.close();
                LOGGER.info("Database connection pool closed");
            }
        } catch (SQLException e) {
            LOGGER.error("Error closing database connection pool", e);
        }
    }
}