package com.hyf.malluserservice.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码加密工具类
 *
 * 保持与历史 UserServiceImpl.md5Encrypt 完全一致的输出（小写 32 位十六进制），
 * 供 LoginServiceImpl 等新模块复用，避免重复实现。
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * 标准 MD5 加密
     *
     * @param source 明文字符串
     * @return 32 位十六进制小写密文
     */
    public static String md5(String source) {
        if (source == null) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法初始化失败", e);
        }
    }
}
