package com.hyf.mallcommon.feign;

/**
 * Feign 调用透传的请求头持有者（ThreadLocal）
 *
 * <p>待 mall-common-security 落地后由其拦截器写入；目前仅留接口。
 *
 * @author hyf
 */
public final class FeignAuthHolder {

    private static final ThreadLocal<String> AUTH = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT = new ThreadLocal<>();

    private FeignAuthHolder() {
    }

    public static void set(String auth, String client) {
        AUTH.set(auth);
        CLIENT.set(client);
    }

    public static String getAuth() {
        return AUTH.get();
    }

    public static String getClient() {
        return CLIENT.get();
    }

    public static void clear() {
        AUTH.remove();
        CLIENT.remove();
    }
}
