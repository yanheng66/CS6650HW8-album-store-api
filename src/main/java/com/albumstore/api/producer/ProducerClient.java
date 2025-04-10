package com.albumstore.api.producer;

import com.albumstore.api.config.AppConfig;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class ProducerClient {
    private static final Logger LOGGER = LogManager.getLogger(ProducerClient.class);
    private static final int CONNECTION_TIMEOUT = 3000; // 3秒
    private static final int SOCKET_TIMEOUT = 5000; // 5秒
    private static final int MAX_RETRIES = 3;

    private final String producerUrl;
    private final CloseableHttpAsyncClient httpClient;
    private final Gson gson;

    // 添加计数器以减少日志量
    private final AtomicLong sentCounter = new AtomicLong(0);
    private final AtomicLong successCounter = new AtomicLong(0);
    private final AtomicLong failureCounter = new AtomicLong(0);
    private static final int LOG_INTERVAL = 100; // 每100条消息记录一次日志

    private volatile boolean running = true;
    private Thread statsLoggerThread;

    public ProducerClient() {
        AppConfig config = AppConfig.getInstance();
        this.producerUrl = String.format("http://%s:%d/publish",
                config.getProducerHost(), config.getProducerPort());

        // 创建I/O反应器
        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor();
        } catch (IOReactorException e) {
            LOGGER.error("Failed to create I/O reactor", e);
            throw new RuntimeException("Failed to initialize ProducerClient", e);
        }

        // 创建连接池管理器
        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor);
        connManager.setMaxTotal(200); // 总连接数
        connManager.setDefaultMaxPerRoute(50); // 每个路由的最大连接数

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();

        // 创建异步HTTP客户端
        this.httpClient = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // 启动客户端
        this.httpClient.start();
        this.gson = new Gson();

        LOGGER.info("Async ProducerClient initialized with URL: {}", producerUrl);

        // 启动统计日志线程
        startStatsLogger();
    }

    private void startStatsLogger() {
        Thread statsThread = new Thread(() -> {
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000); // 每5秒记录一次统计
                    long sent = sentCounter.get();
                    long success = successCounter.get();
                    long failure = failureCounter.get();

                    if (sent > 0) {
                        LOGGER.info("Producer stats - Sent: {}, Success: {}, Failed: {}, Success Rate: {}%",
                                sent, success, failure,
                                sent > 0 ? (success * 100) / sent : 0);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer-stats-logger");

        statsThread.setDaemon(true);
        statsThread.start();
    }

    /**
     * 异步发送评论消息到Producer服务
     * 返回CompletableFuture，允许调用者决定是否等待结果
     */
    public CompletableFuture<Boolean> sendReviewMessageAsync(String reviewType, String albumId) {
        Map<String, String> message = new HashMap<>();
        message.put("reviewType", reviewType);
        message.put("albumId", albumId);

        String jsonMessage = gson.toJson(message);
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        // 记录发送计数
        long currentCount = sentCounter.incrementAndGet();
        boolean shouldLog = currentCount % LOG_INTERVAL == 0;

        if (shouldLog) {
            LOGGER.debug("Sending message #{}: {} for album: {}",
                    currentCount, reviewType, albumId);
        }

        try {
            HttpPost httpPost = new HttpPost(producerUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonMessage));

            // 异步执行HTTP请求
            httpClient.execute(httpPost, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(HttpResponse response) {
                    try {
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
                            successCounter.incrementAndGet();
                            resultFuture.complete(true);
                        } else {
                            String responseBody = EntityUtils.toString(response.getEntity());
                            LOGGER.warn("Failed to send message. Status: {}, Response: {}",
                                    statusCode, responseBody);
                            failureCounter.incrementAndGet();
                            retryOrFail(reviewType, albumId, 1, resultFuture, null);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error processing response", e);
                        failureCounter.incrementAndGet();
                        retryOrFail(reviewType, albumId, 1, resultFuture, e);
                    }
                }

                @Override
                public void failed(Exception e) {
                    LOGGER.error("Request failed", e);
                    failureCounter.incrementAndGet();
                    retryOrFail(reviewType, albumId, 1, resultFuture, e);
                }

                @Override
                public void cancelled() {
                    LOGGER.warn("Request cancelled");
                    failureCounter.incrementAndGet();
                    resultFuture.complete(false);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error sending message", e);
            failureCounter.incrementAndGet();
            resultFuture.completeExceptionally(e);
        }

        return resultFuture;
    }

    /**
     * 重试逻辑
     */
    private void retryOrFail(String reviewType, String albumId, int attempt,
                             CompletableFuture<Boolean> resultFuture, Exception exception) {
        if (attempt < MAX_RETRIES) {
            // 计算退避时间
            long backoffTime = (long) (Math.pow(2, attempt) * 100);

            // 异步重试，不阻塞当前线程
            CompletableFuture.delayedExecutor(backoffTime, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        LOGGER.debug("Retrying #{} for {} album: {}", attempt + 1, reviewType, albumId);
                        sendReviewMessageAsync(reviewType, albumId)
                                .thenAccept(resultFuture::complete)
                                .exceptionally(e -> {
                                    resultFuture.completeExceptionally(e);
                                    return null;
                                });
                    });
        } else {
            // 达到最大重试次数
            if (exception != null) {
                resultFuture.completeExceptionally(exception);
            } else {
                resultFuture.complete(false);
            }
        }
    }

    /**
     * 同步发送方法（兼容旧API）
     */
    public boolean sendReviewMessage(String reviewType, String albumId) {
        try {
            return sendReviewMessageAsync(reviewType, albumId).get();
        } catch (Exception e) {
            LOGGER.error("Sync sendReviewMessage failed", e);
            return false;
        }
    }

    /**
     * 关闭客户端资源
     */
    public void close() {
        try {
            running = false;
            if (statsLoggerThread != null) {
                statsLoggerThread.interrupt();
                try {
                    // 等待线程结束，但最多等待2秒
                    statsLoggerThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (httpClient != null) {
                httpClient.close();
                LOGGER.info("ProducerClient closed");
            }
        } catch (IOException e) {
            LOGGER.error("Error closing ProducerClient", e);
        }
    }
}