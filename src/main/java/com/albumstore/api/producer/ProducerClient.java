package com.albumstore.api.producer;

import com.albumstore.api.config.AppConfig;
import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProducerClient {
    private static final Logger LOGGER = LogManager.getLogger(ProducerClient.class);
    private static final int CONNECTION_TIMEOUT = 5000; // 5秒
    private static final int SOCKET_TIMEOUT = 10000; // 10秒
    private static final int MAX_RETRIES = 3;

    private final String producerUrl;
    private final CloseableHttpClient httpClient;
    private final Gson gson;

    public ProducerClient() {
        AppConfig config = AppConfig.getInstance();
        this.producerUrl = String.format("http://%s:%d/publish",
                config.getProducerHost(), config.getProducerPort());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        this.gson = new Gson();

        LOGGER.info("ProducerClient initialized with URL: {}", producerUrl);
    }

    /**
     * 发送评论消息到Producer服务
     */
    public boolean sendReviewMessage(String reviewType, String albumId) {
        Map<String, String> message = new HashMap<>();
        message.put("reviewType", reviewType);
        message.put("albumId", albumId);

        String jsonMessage = gson.toJson(message);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpPost httpPost = new HttpPost(producerUrl);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(jsonMessage));

                LOGGER.info("Sending review message to producer: {} for album: {}, attempt: {}",
                        reviewType, albumId, attempt);

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
                        LOGGER.info("Review message sent successfully: {} for album: {}", reviewType, albumId);
                        return true;
                    } else {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        LOGGER.warn("Failed to send review message. Status: {}, Response: {}",
                                statusCode, responseBody);

                        if (attempt < MAX_RETRIES) {
                            long backoffTime = calculateBackoffTime(attempt);
                            LOGGER.info("Retrying in {} ms...", backoffTime);
                            TimeUnit.MILLISECONDS.sleep(backoffTime);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error sending review message: {} for album: {}, attempt: {}",
                        reviewType, albumId, attempt, e);

                if (attempt < MAX_RETRIES) {
                    try {
                        long backoffTime = calculateBackoffTime(attempt);
                        LOGGER.info("Retrying in {} ms...", backoffTime);
                        TimeUnit.MILLISECONDS.sleep(backoffTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Thread interrupted while waiting for retry", ie);
                    }
                }
            }
        }

        LOGGER.error("Failed to send review message after {} attempts: {} for album: {}",
                MAX_RETRIES, reviewType, albumId);
        return false;
    }

    /**
     * 计算退避时间 (指数退避策略)
     */
    private long calculateBackoffTime(int attempt) {
        return (long) (Math.pow(2, attempt) * 100);
    }

    /**
     * 关闭客户端资源
     */
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
                LOGGER.info("ProducerClient closed");
            }
        } catch (IOException e) {
            LOGGER.error("Error closing ProducerClient", e);
        }
    }
}
