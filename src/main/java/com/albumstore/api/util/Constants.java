package com.albumstore.api.util;

public class Constants {
    // API 路径
// API 路径
    public static final String ALBUMS_PATH = "/albums";
    public static final String REVIEW_PATH = "/review";
    public static final String ADMIN_RESET_PATH = "/admin/reset"; // 新增的管理员重置路径

    // 评论类型
    public static final String REVIEW_LIKE = "like";
    public static final String REVIEW_DISLIKE = "dislike";

    // HTTP状态码
    public static final int STATUS_OK = 200;
    public static final int STATUS_CREATED = 201;
    public static final int STATUS_BAD_REQUEST = 400;
    public static final int STATUS_NOT_FOUND = 404;
    public static final int STATUS_INTERNAL_SERVER_ERROR = 500;

    // Content-Type
    public static final String CONTENT_TYPE_JSON = "application/json";

    // 文件上传限制
    public static final int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
}