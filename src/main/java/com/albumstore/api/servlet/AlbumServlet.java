package com.albumstore.api.servlet;

import com.albumstore.api.db.AlbumDAO;
import com.albumstore.api.db.ReviewDAO;
import com.albumstore.api.model.AlbumInfo;
import com.albumstore.api.model.ErrorMsg;
import com.albumstore.api.model.ImageMetaData;
import com.albumstore.api.producer.ProducerClient;
import com.albumstore.api.util.Constants;
import com.google.gson.Gson;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@WebServlet(name = "AlbumServlet", urlPatterns = {"/albums", "/albums/*", "/review/*", "/admin/*"}, asyncSupported = true)
public class AlbumServlet extends HttpServlet {
    private static final Logger LOGGER = LogManager.getLogger(AlbumServlet.class);
    private static final long serialVersionUID = 1L;

    // 操作计数器，用于减少日志频率
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong albumCreationCounter = new AtomicLong(0);
    private final AtomicLong reviewCounter = new AtomicLong(0);
    private static final int LOG_INTERVAL = 100; // 每100个请求记录一次详细日志

    private AlbumDAO albumDAO;
    private ReviewDAO reviewDAO;
    private ProducerClient producerClient;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        LOGGER.info("Initializing AlbumServlet");
        albumDAO = new AlbumDAO();
        reviewDAO = new ReviewDAO();
        producerClient = new ProducerClient();
        gson = new Gson();
        LOGGER.info("AlbumServlet initialized");
    }

    @Override
    public void destroy() {
        LOGGER.info("Destroying AlbumServlet");
        producerClient.close();
        LOGGER.info("AlbumServlet destroyed");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        long requestId = requestCounter.incrementAndGet();
        boolean shouldLogDetails = requestId % LOG_INTERVAL == 0;

        if (shouldLogDetails) {
            LOGGER.info("Handling POST request #{}: {}", requestId, uri);
        } else {
            LOGGER.debug("Handling POST request #{}: {}", requestId, uri);
        }

        try {
            // 处理创建新专辑请求 - /albums
            if (uri.endsWith(Constants.ALBUMS_PATH)) {
                handleNewAlbum(request, response, shouldLogDetails);
            }
            // 处理喜欢/不喜欢专辑请求 - /review/{likeornot}/{albumID}
            else if (uri.contains(Constants.REVIEW_PATH)) {
                handleReview(request, response, shouldLogDetails);
            }
            // 处理数据库重置请求 - /admin/reset
            else if (uri.endsWith(Constants.ADMIN_RESET_PATH)) {
                handleDatabaseReset(response);
            } else {
                LOGGER.warn("Invalid request path: {}", uri);
                sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path: " + uri);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing POST request #{}: {}", requestId, uri, e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 处理清空数据库请求
     */
    private void handleDatabaseReset(HttpServletResponse response) throws IOException {
        LOGGER.info("Handling database reset request");

        boolean success = albumDAO.clearAllData();

        if (success) {
            // 发送成功响应
            response.setContentType(Constants.CONTENT_TYPE_JSON);
            response.setStatus(Constants.STATUS_OK);
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(new ErrorMsg("Reset database successful")));
            out.flush();
        } else {
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Reset database failed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        String uri = request.getRequestURI();
        long requestId = requestCounter.incrementAndGet();
        boolean shouldLogDetails = requestId % LOG_INTERVAL == 0;

        if (shouldLogDetails) {
            LOGGER.info("Handling GET request #{}: {}", requestId, uri);
        } else {
            LOGGER.debug("Handling GET request #{}: {}", requestId, uri);
        }

        try {
            // 处理获取专辑信息请求 - /albums/{albumID}
            if (uri.startsWith(Constants.ALBUMS_PATH) && pathInfo != null && !pathInfo.equals("/")) {
                String albumId = pathInfo.substring(1);
                handleGetAlbum(albumId, response);
            }
            // 处理获取专辑评论统计请求 - /review/{albumID}
            else if (uri.startsWith(Constants.REVIEW_PATH) && pathInfo != null && !pathInfo.equals("/")) {
                String albumId = pathInfo.substring(1);
                handleGetReviewStats(albumId, response);
            } else {
                LOGGER.warn("Invalid request path: {}", uri);
                sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path or album ID is required");
            }
        } catch (Exception e) {
            LOGGER.error("Error processing GET request #{}: {}", requestId, uri, e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 处理获取专辑评论统计请求
     */
    private void handleGetReviewStats(String albumId, HttpServletResponse response)
            throws IOException {
        LOGGER.debug("Getting review stats for album: {}", albumId);

        // 验证专辑是否存在
        if (!albumDAO.albumExists(albumId)) {
            LOGGER.warn("Album not found for review stats: {}", albumId);
            sendError(response, Constants.STATUS_NOT_FOUND, "Album not found");
            return;
        }

        // 获取评论统计
        Map<String, Integer> reviewStats = reviewDAO.getReviewStats(albumId);

        // 创建响应对象
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("likes", String.valueOf(reviewStats.get("like")));
        responseMap.put("dislikes", String.valueOf(reviewStats.get("dislike")));

        // 发送响应
        response.setContentType(Constants.CONTENT_TYPE_JSON);
        response.setStatus(Constants.STATUS_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(responseMap));
        out.flush();

        LOGGER.debug("Review stats sent for album: {}", albumId);
    }

    /**
     * 处理创建新专辑请求
     */
    private void handleNewAlbum(HttpServletRequest request, HttpServletResponse response, boolean shouldLogDetails)
            throws ServletException, IOException {
        long albumCreationId = albumCreationCounter.incrementAndGet();

        // 确保请求包含multipart内容
        if (!ServletFileUpload.isMultipartContent(request)) {
            LOGGER.warn("Request is not multipart");
            sendError(response, Constants.STATUS_BAD_REQUEST, "Multipart content expected");
            return;
        }

        try {
            // 创建文件上传处理器，设置临时文件阈值
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(4 * 1024 * 1024); // 4MB阈值，大文件写入磁盘
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setFileSizeMax(Constants.MAX_FILE_SIZE);

            // 解析请求
            List<FileItem> items = upload.parseRequest(request);
            if (shouldLogDetails) {
                LOGGER.info("Album creation #{}: Request parsed with {} items", albumCreationId, items.size());
            }

            // 处理上传的文件和表单字段
            byte[] imageData = null;
            String artist = null;
            String title = null;
            String year = null;

            for (FileItem item : items) {
                if (!item.isFormField()) {
                    // 处理图片文件
                    if ("image".equals(item.getFieldName())) {
                        imageData = item.get();
                        if (shouldLogDetails) {
                            LOGGER.info("Album creation #{}: Image received, size: {} bytes",
                                    albumCreationId, imageData.length);
                        }
                    }
                } else {
                    // 处理表单字段
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");

                    switch (fieldName) {
                        case "artist":
                            artist = fieldValue;
                            break;
                        case "title":
                            title = fieldValue;
                            break;
                        case "year":
                            year = fieldValue;
                            break;
                    }
                }
            }

            // 验证必填字段
            if (imageData == null || artist == null || title == null || year == null) {
                LOGGER.warn("Album creation #{}: Missing required fields", albumCreationId);
                sendError(response, Constants.STATUS_BAD_REQUEST, "Missing required fields");
                return;
            }

            // 保存专辑信息
            AlbumInfo albumInfo = new AlbumInfo(artist, title, year);
            String albumId = albumDAO.saveAlbum(albumInfo, imageData);

            if (albumId == null) {
                LOGGER.error("Album creation #{}: Failed to save album: {}",
                        albumCreationId, albumInfo);
                sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Failed to save album");
                return;
            }

            // 创建响应数据
            ImageMetaData metaData = new ImageMetaData(albumId, String.valueOf(imageData.length));

            // 发送响应
            response.setContentType(Constants.CONTENT_TYPE_JSON);
            response.setStatus(Constants.STATUS_OK);
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(metaData));
            out.flush();

            LOGGER.debug("Album creation #{}: Album created successfully with ID: {}",
                    albumCreationId, albumId);
        } catch (Exception e) {
            LOGGER.error("Album creation #{}: Error creating new album", albumCreationId, e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * 处理获取专辑信息请求
     */
    private void handleGetAlbum(String albumId, HttpServletResponse response)
            throws IOException {
        LOGGER.debug("Getting album info: {}", albumId);

        // 获取专辑信息
        AlbumInfo albumInfo = albumDAO.getAlbumById(albumId);

        if (albumInfo == null) {
            LOGGER.warn("Album not found: {}", albumId);
            sendError(response, Constants.STATUS_NOT_FOUND, "Album not found");
            return;
        }

        // 发送响应
        response.setContentType(Constants.CONTENT_TYPE_JSON);
        response.setStatus(Constants.STATUS_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(albumInfo));
        out.flush();

        LOGGER.debug("Album info sent: {}", albumId);
    }

    /**
     * 处理喜欢/不喜欢专辑请求 - 使用异步方式
     */
    private void handleReview(HttpServletRequest request, HttpServletResponse response, boolean shouldLogDetails)
            throws IOException {
        String uri = request.getRequestURI();
        String[] pathParts = uri.split("/");
        long reviewId = reviewCounter.incrementAndGet();

        // 验证路径格式: /review/{likeornot}/{albumID}
        if (pathParts.length < 4) {
            LOGGER.warn("Review #{}: Invalid review path: {}", reviewId, uri);
            sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path format");
            return;
        }

        String reviewType = pathParts[pathParts.length - 2];
        String albumId = pathParts[pathParts.length - 1];

        if (shouldLogDetails) {
            LOGGER.info("Review #{}: Processing review: {} for album: {}",
                    reviewId, reviewType, albumId);
        } else {
            LOGGER.debug("Review #{}: Processing review: {} for album: {}",
                    reviewId, reviewType, albumId);
        }

        // 验证reviewType
        if (!Constants.REVIEW_LIKE.equals(reviewType) && !Constants.REVIEW_DISLIKE.equals(reviewType)) {
            LOGGER.warn("Review #{}: Invalid review type: {}", reviewId, reviewType);
            sendError(response, Constants.STATUS_BAD_REQUEST,
                    "Review type must be '" + Constants.REVIEW_LIKE + "' or '" + Constants.REVIEW_DISLIKE + "'");
            return;
        }

        // 验证专辑是否存在
        if (!albumDAO.albumExists(albumId)) {
            LOGGER.warn("Review #{}: Album not found for review: {}", reviewId, albumId);
            sendError(response, Constants.STATUS_NOT_FOUND, "Album not found");
            return;
        }

        // 使用异步模式处理请求
        final AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(10000); // 10秒超时

        // 异步发送评论消息到Producer服务
        final String finalReviewType = reviewType;
        final String finalAlbumId = albumId;
        final long finalReviewId = reviewId;

        CompletableFuture<Boolean> future = producerClient.sendReviewMessageAsync(reviewType, albumId);

        future.thenAccept(success -> {
            try {
                HttpServletResponse asyncResponse = (HttpServletResponse) asyncContext.getResponse();

                if (success) {
                    // 发送成功响应
                    asyncResponse.setStatus(Constants.STATUS_CREATED);
                    LOGGER.debug("Review #{}: Message sent successfully: {} for album: {}",
                            finalReviewId, finalReviewType, finalAlbumId);
                } else {
                    // 发送错误响应
                    sendError(asyncResponse, Constants.STATUS_INTERNAL_SERVER_ERROR,
                            "Failed to process review");
                    LOGGER.error("Review #{}: Failed to send message: {} for album: {}",
                            finalReviewId, finalReviewType, finalAlbumId);
                }
            } catch (Exception e) {
                LOGGER.error("Review #{}: Error processing async response", finalReviewId, e);
            } finally {
                asyncContext.complete();
            }
        }).exceptionally(e -> {
            try {
                HttpServletResponse asyncResponse = (HttpServletResponse) asyncContext.getResponse();
                sendError(asyncResponse, Constants.STATUS_INTERNAL_SERVER_ERROR,
                        "Error processing review: " + e.getMessage());
                LOGGER.error("Review #{}: Exception in async processing", finalReviewId, e);
            } catch (Exception ex) {
                LOGGER.error("Review #{}: Error sending error response", finalReviewId, ex);
            } finally {
                asyncContext.complete();
            }
            return null;
        });
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        LOGGER.debug("Sending error response: {} - {}", statusCode, message);

        ErrorMsg errorMsg = new ErrorMsg(message);
        response.setContentType(Constants.CONTENT_TYPE_JSON);
        response.setStatus(statusCode);

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(errorMsg));
        out.flush();
    }
}