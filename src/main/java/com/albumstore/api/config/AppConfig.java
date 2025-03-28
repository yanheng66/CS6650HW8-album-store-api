package com.albumstore.api.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {
    private static final Logger LOGGER = LogManager.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "/application.properties";
    private static AppConfig instance;

    private final Properties properties;

    private AppConfig() {
        properties = new Properties();
        loadProperties();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadProperties() {
        // 首先尝试从外部配置文件加载
        Path externalConfigPath = Paths.get("/etc/albumstore/api.properties");
        if (Files.exists(externalConfigPath)) {
            try (InputStream inputStream = new FileInputStream(externalConfigPath.toFile())) {
                properties.load(inputStream);
                LOGGER.info("Loaded configuration from external file: {}", externalConfigPath);
                return;
            } catch (IOException e) {
                LOGGER.warn("Failed to load external configuration file: {}", externalConfigPath, e);
                // 继续尝试从内部资源加载
            }
        }

        // 从内部资源加载
        try (InputStream inputStream = getClass().getResourceAsStream("/application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
                LOGGER.info("Loaded configuration from internal resource: /application.properties");
            } else {
                LOGGER.error("Cannot find internal configuration resource: /application.properties");
                throw new RuntimeException("Cannot find internal configuration resource: /application.properties");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load internal configuration resource", e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

//    private void loadProperties() {
//        try (InputStream inputStream = getClass().getResourceAsStream(CONFIG_FILE)) {
//            if (inputStream != null) {
//                properties.load(inputStream);
//                LOGGER.info("Application properties loaded successfully");
//            } else {
//                LOGGER.error("Cannot find {}", CONFIG_FILE);
//                throw new RuntimeException("Cannot find " + CONFIG_FILE);
//            }
//        } catch (IOException e) {
//            LOGGER.error("Failed to load application properties", e);
//            throw new RuntimeException("Failed to load application properties", e);
//        }
//    }

    // 数据库配置
    public String getDbUrl() {
        return properties.getProperty("db.url");
    }

    public String getDbUsername() {
        return properties.getProperty("db.username");
    }

    public String getDbPassword() {
        return properties.getProperty("db.password");
    }

    public int getDbInitialSize() {
        return Integer.parseInt(properties.getProperty("db.pool.initialSize", "10"));
    }

    public int getDbMaxTotal() {
        return Integer.parseInt(properties.getProperty("db.pool.maxTotal", "50"));
    }

    public int getDbMaxIdle() {
        return Integer.parseInt(properties.getProperty("db.pool.maxIdle", "20"));
    }

    public int getDbMinIdle() {
        return Integer.parseInt(properties.getProperty("db.pool.minIdle", "10"));
    }

    // Producer服务配置
    public String getProducerHost() {
        return properties.getProperty("producer.host");
    }

    public int getProducerPort() {
        return Integer.parseInt(properties.getProperty("producer.port", "9090"));
    }

    // 获取任意属性
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
