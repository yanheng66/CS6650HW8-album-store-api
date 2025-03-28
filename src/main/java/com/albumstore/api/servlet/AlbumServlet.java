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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet(name = "AlbumServlet", urlPatterns = {"/albums", "/albums/*", "/review/*"})
public class AlbumServlet extends HttpServlet {
    private static final Logger LOGGER = LogManager.getLogger(AlbumServlet.class);
    private static final long serialVersionUID = 1L;

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
        String pathInfo = request.getPathInfo();
        String uri = request.getRequestURI();

        LOGGER.info("Handling POST request: {}", uri);

        try {
            // 处理创建新专辑请求 - /albums
            if (uri.endsWith(Constants.ALBUMS_PATH)) {
                handleNewAlbum(request, response);
            }
            // 处理喜欢/不喜欢专辑请求 - /review/{likeornot}/{albumID}
            else if (uri.contains(Constants.REVIEW_PATH)) {
                handleReview(request, response);
            } else {
                LOGGER.warn("Invalid request path: {}", uri);
                sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path: " + uri);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing POST request: {}", uri, e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        String uri = request.getRequestURI();

        LOGGER.info("Handling GET request: {}", uri);

        try {
            // 处理获取专辑信息请求 - /albums/{albumID}
            if (uri.startsWith(Constants.ALBUMS_PATH) && pathInfo != null && !pathInfo.equals("/")) {
                String albumId = pathInfo.substring(1);
                handleGetAlbum(albumId, response);
            } else {
                LOGGER.warn("Invalid request path: {}", uri);
                sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path or album ID is required");
            }
        } catch (Exception e) {
            LOGGER.error("Error processing GET request: {}", uri, e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 处理创建新专辑请求
     */
    private void handleNewAlbum(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 确保请求包含multipart内容
        if (!ServletFileUpload.isMultipartContent(request)) {
            LOGGER.warn("Request is not multipart");
            sendError(response, Constants.STATUS_BAD_REQUEST, "Multipart content expected");
            return;
        }

        try {
            // 创建文件上传处理器
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setFileSizeMax(Constants.MAX_FILE_SIZE);

            // 解析请求
            List<FileItem> items = upload.parseRequest(request);
            LOGGER.info("Request parsed with {} items", items.size());

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
                        LOGGER.info("Image received, size: {} bytes", imageData.length);
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

                    LOGGER.debug("Form field: {} = {}", fieldName, fieldValue);
                }
            }

            // 验证必填字段
            if (imageData == null || artist == null || title == null || year == null) {
                LOGGER.warn("Missing required fields");
                sendError(response, Constants.STATUS_BAD_REQUEST, "Missing required fields");
                return;
            }

            // 保存专辑信息
            AlbumInfo albumInfo = new AlbumInfo(artist, title, year);
            String albumId = albumDAO.saveAlbum(albumInfo, imageData);

            if (albumId == null) {
                LOGGER.error("Failed to save album: {}", albumInfo);
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

            LOGGER.info("Album created successfully: {}", albumId);
        } catch (Exception e) {
            LOGGER.error("Error creating new album", e);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * 处理获取专辑信息请求
     */
    private void handleGetAlbum(String albumId, HttpServletResponse response)
            throws IOException {
        LOGGER.info("Getting album info: {}", albumId);

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

        LOGGER.info("Album info sent: {}", albumId);
    }

    /**
     * 处理喜欢/不喜欢专辑请求
     */
    private void handleReview(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getRequestURI();
        String[] pathParts = uri.split("/");

        // 验证路径格式: /review/{likeornot}/{albumID}
        if (pathParts.length < 4) {
            LOGGER.warn("Invalid review path: {}", uri);
            sendError(response, Constants.STATUS_BAD_REQUEST, "Invalid path format");
            return;
        }

        String reviewType = pathParts[pathParts.length - 2];
        String albumId = pathParts[pathParts.length - 1];

        LOGGER.info("Processing review: {} for album: {}", reviewType, albumId);

        // 验证reviewType
        if (!Constants.REVIEW_LIKE.equals(reviewType) && !Constants.REVIEW_DISLIKE.equals(reviewType)) {
            LOGGER.warn("Invalid review type: {}", reviewType);
            sendError(response, Constants.STATUS_BAD_REQUEST,
                    "Review type must be '" + Constants.REVIEW_LIKE + "' or '" + Constants.REVIEW_DISLIKE + "'");
            return;
        }

        // 验证专辑是否存在
        if (!albumDAO.albumExists(albumId)) {
            LOGGER.warn("Album not found for review: {}", albumId);
            sendError(response, Constants.STATUS_NOT_FOUND, "Album not found");
            return;
        }

        // 发送评论消息到Producer服务
        boolean success = producerClient.sendReviewMessage(reviewType, albumId);

        if (!success) {
            LOGGER.error("Failed to send review message: {} for album: {}", reviewType, albumId);
            sendError(response, Constants.STATUS_INTERNAL_SERVER_ERROR, "Failed to process review");
            return;
        }

        // 发送成功响应
        response.setStatus(Constants.STATUS_CREATED);

        LOGGER.info("Review message sent successfully: {} for album: {}", reviewType, albumId);
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        LOGGER.warn("Sending error response: {} - {}", statusCode, message);

        ErrorMsg errorMsg = new ErrorMsg(message);
        response.setContentType(Constants.CONTENT_TYPE_JSON);
        response.setStatus(statusCode);

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(errorMsg));
        out.flush();
    }
}
