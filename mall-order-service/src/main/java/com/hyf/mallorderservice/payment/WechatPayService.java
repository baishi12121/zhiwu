package com.hyf.mallorderservice.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallorderservice.repository.PayRepository;
import com.hyf.mallorderservice.service.PayNotifyResult;
import com.hyf.mallorderservice.service.PayRequest;
import com.hyf.mallorderservice.service.PayResponse;
import com.hyf.mallorderservice.service.PayService;
import com.hyf.mallorderservice.dataobject.PayRecordDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 微信支付 V3 服务实现 — 上线时使用。
 *
 * <p>通过微信支付 V3 API 完成 JSAPI 下单、退款、回调解析。
 * 签名算法：SHA256withRSA；回调解密：AES-256-GCM。
 *
 * <p>所需配置（{@code mall.wechat.pay.*}）：
 * <ul>
 *   <li>appid — 小程序 appId</li>
 *   <li>mchid — 商户号</li>
 *   <li>mchSerialNo — 商户证书序列号</li>
 *   <li>privateKeyFilePath — 商户私钥 PEM 文件路径</li>
 *   <li>apiV3Key — API v3 密钥（32 字节）</li>
 *   <li>notifyUrl — 支付回调地址</li>
 * </ul>
 *
 * <p><b>注意</b>：回调签名验证需要微信平台证书，当前实现暂跳过签名验证（仅解密），
 * 生产环境应补充平台证书下载与验签逻辑。
 *
 * @author hyf
 */
public class WechatPayService implements PayService {

    private static final Logger log = LoggerFactory.getLogger(WechatPayService.class);

    private static final String JSAPI_ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
    private static final String REFUND_URL = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";

    private final WeChatPayProperties props;
    private final PayRepository payRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PrivateKey privateKey;

    public WechatPayService(WeChatPayProperties props, PayRepository payRepository) {
        this.props = props;
        this.payRepository = payRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.privateKey = loadPrivateKey(props.getPrivateKeyFilePath());
        log.info("[wechat-pay] WechatPayService 初始化完成: mchid={}", props.getMchid());
    }

    // ==================== PayService 接口实现 ====================

    @Override
    public PayResponse createOrder(PayRequest request) {
        try {
            // 1. 构建请求体
            int totalCents = request.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();

            Map<String, Object> body = new HashMap<>();
            body.put("appid", props.getAppid());
            body.put("mchid", props.getMchid());
            body.put("description", request.getDescription());
            body.put("out_trade_no", request.getOrderNo());
            body.put("notify_url", props.getNotifyUrl());

            Map<String, Object> amount = new HashMap<>();
            amount.put("total", totalCents);
            amount.put("currency", "CNY");
            body.put("amount", amount);

            Map<String, Object> payer = new HashMap<>();
            payer.put("openid", request.getOpenid());
            body.put("payer", payer);

            String bodyJson = objectMapper.writeValueAsString(body);

            // 2. 调用微信下单 API
            String responseJson = doPost(JSAPI_ORDER_URL, bodyJson);
            JsonNode response = objectMapper.readTree(responseJson);
            String prepayId = response.get("prepay_id").asText();
            log.info("[wechat-pay] 下单成功: orderNo={}, prepayId={}", request.getOrderNo(), prepayId);

            // 3. 构建前端调起支付参数并签名
            String appId = props.getAppid();
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr = UUID.randomUUID().toString().replace("-", "");
            String packageStr = "prepay_id=" + prepayId;

            String signMessage = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageStr + "\n";
            String paySign = signRsa(signMessage);

            return PayResponse.builder()
                    .appId(appId)
                    .timeStamp(timeStamp)
                    .nonceStr(nonceStr)
                    .packageStr(packageStr)
                    .signType("RSA")
                    .paySign(paySign)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[wechat-pay] 下单失败: orderNo={}, error={}", request.getOrderNo(), e.getMessage(), e);
            throw new BizException(ResultCode.PAY_CREATE_FAILED.getCode(),
                    "微信支付下单失败: " + e.getMessage());
        }
    }

    @Override
    public void refund(String orderNo) {
        try {
            // 1. 查询支付记录获取交易号和金额
            PayRecordDO record = payRepository.findByOrderNo(orderNo);
            if (record == null) {
                throw new BizException(ResultCode.PAY_ORDER_NOT_FOUND);
            }

            String outRefundNo = "RF" + System.currentTimeMillis() + (int) (Math.random() * 10000);
            int totalCents = record.getPayAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();

            // 2. 构建退款请求
            Map<String, Object> body = new HashMap<>();
            body.put("out_trade_no", orderNo);
            body.put("out_refund_no", outRefundNo);
            body.put("reason", record.getRefundReason() != null ? record.getRefundReason() : "用户申请退款");
            body.put("notify_url", props.getRefundNotifyUrl());

            Map<String, Object> amount = new HashMap<>();
            amount.put("refund", totalCents);
            amount.put("total", totalCents);
            amount.put("currency", "CNY");
            body.put("amount", amount);

            String bodyJson = objectMapper.writeValueAsString(body);

            // 3. 调用微信退款 API
            String responseJson = doPost(REFUND_URL, bodyJson);
            JsonNode response = objectMapper.readTree(responseJson);
            String refundId = response.has("refund_id") ? response.get("refund_id").asText() : null;

            log.info("[wechat-pay] 退款申请成功: orderNo={}, outRefundNo={}, refundId={}",
                    orderNo, outRefundNo, refundId);

            // 4. 更新支付记录（退款中）
            payRepository.updateRefundStatus(record.getId(), 0, outRefundNo, refundId,
                    record.getPayAmount(), null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[wechat-pay] 退款失败: orderNo={}, error={}", orderNo, e.getMessage(), e);
            throw new BizException(ResultCode.PAY_REFUND_FAILED.getCode(),
                    "微信退款失败: " + e.getMessage());
        }
    }

    @Override
    public PayNotifyResult parseNotify(String body, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.get("resource");

            if (resource == null) {
                throw new BizException(ResultCode.PAY_NOTIFY_VERIFY_FAILED.getCode(),
                        "回调数据缺少 resource 字段");
            }

            String ciphertext = resource.get("ciphertext").asText();
            String nonce = resource.get("nonce").asText();
            String associatedData = resource.has("associated_data")
                    ? resource.get("associated_data").asText() : "";

            // TODO: 生产环境应补充签名验证（需下载微信平台证书）
            // 当前仅通过 AES 解密保证数据完整性
            String decrypted = decryptAesGcm(ciphertext, nonce, associatedData);
            log.info("[wechat-pay] 回调解密成功: {}", decrypted);

            JsonNode tx = objectMapper.readTree(decrypted);
            String outTradeNo = tx.get("out_trade_no").asText();
            String transactionId = tx.get("transaction_id").asText();
            String tradeState = tx.get("trade_state").asText();
            int amountTotal = tx.has("amount") && tx.get("amount").has("total")
                    ? tx.get("amount").get("total").asInt() : 0;

            return PayNotifyResult.builder()
                    .outTradeNo(outTradeNo)
                    .transactionId(transactionId)
                    .tradeState(tradeState)
                    .amountTotal(amountTotal)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[wechat-pay] 回调解析失败: error={}", e.getMessage(), e);
            throw new BizException(ResultCode.PAY_NOTIFY_VERIFY_FAILED.getCode(),
                    "支付回调解析失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isMock() {
        return false;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 加载商户私钥（PEM 格式）。
     */
    private PrivateKey loadPrivateKey(String filePath) {
        try {
            String pem = Files.readString(Path.of(filePath));
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("加载商户私钥失败: " + filePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * SHA256withRSA 签名 + Base64 编码。
     */
    private String signRsa(String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 构建微信支付 V3 Authorization 头。
     */
    private String buildAuthorization(String method, String urlPath, String body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String message = method + "\n" + urlPath + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = signRsa(message);
        return String.format(
                "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",serial_no=\"%s\",timestamp=\"%s\",nonce_str=\"%s\",signature=\"%s\"",
                props.getMchid(), props.getMchSerialNo(), timestamp, nonce, signature);
    }

    /**
     * 发送 POST 请求（带签名）。
     */
    private String doPost(String url, String body) throws Exception {
        String path = new URL(url).getPath();
        String authorization = buildAuthorization("POST", path, body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("[wechat-pay] API调用失败: url={}, status={}, body={}", url, response.statusCode(), response.body());
            throw new BizException(ResultCode.PAY_CREATE_FAILED.getCode(),
                    "微信支付API调用失败 [" + response.statusCode() + "]: " + response.body());
        }
        return response.body();
    }

    /**
     * AES-256-GCM 解密回调资源。
     */
    private String decryptAesGcm(String ciphertext, String nonce, String associatedData) throws Exception {
        byte[] key = props.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
        byte[] aadBytes = associatedData.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = Base64.getDecoder().decode(ciphertext);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, nonceBytes);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        cipher.updateAAD(aadBytes);

        byte[] decrypted = cipher.doFinal(cipherBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
