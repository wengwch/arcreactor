package cn.veryai.arcreactor.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class FundWalletService {

  @Value("${fund-service.base-url}")
  private String baseUrl;

  private static final String CLIENT_REGISTRATION_ID = "veryai-api";
  private static final String MACHINE_PRINCIPAL = "veryai-api";

  /** 资金系统时间字段格式（yyyy-MM-dd HH:mm:ss），与 billing 侧 FORMATTER 保持一致 */
  private static final DateTimeFormatter FUND_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final RestClient restClient;


  public FundWalletService(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * 调用资金系统冻结余额
   *
   * @param userId 用户ID
   * @param amount 冻结金额（必须 > 0）
   * @param orderId 订单ID（幂等，重复调用不会重复冻结）
   * @param lockType 冻结类型（如 GPU_RENTAL）
   * @param expireAt 过期时间（ISO 8601），过期后自动释放，可为null
   * @return 资金系统返回的钱包信息
   */
  public FundSystemResponse<FundWalletLockData> lock(String userId, BigDecimal amount,
                                                      String orderId, String lockType,
                                                      String expireAt) {
    FundLockRequest request = new FundLockRequest();
    request.setUserId(userId);
    request.setAmount(amount);
    request.setOrderId(orderId);
    request.setLockType(lockType);
    request.setExpireAt(expireAt);

    try {
      FundSystemResponse<FundWalletLockData> response = restClient.post()
          .uri(baseUrl + "/srv/api/internal/wallets/lock")
          .header("Authorization", "Bearer " + getToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(new ParameterizedTypeReference<FundSystemResponse<FundWalletLockData>>() {});

      if (response == null || !response.isSuccess()) {
        log.error("资金系统冻结余额返回异常, userId: {}, orderId: {}, response: {}", userId, orderId, response);
      }
      return response;
    } catch (Exception e) {
      log.error("调用资金系统冻结余额失败, userId: {}, orderId: {}, amount: {}", userId, orderId, amount, e);
      throw new RuntimeException("调用资金系统冻结余额失败: " + e.getMessage(), e);
    }
  }

  /**
   * 调用资金系统确认扣款（从冻结金额中扣款）
   *
   * @param userId 用户ID
   * @param orderId 订单ID（从该订单的冻结金额中扣款）
   * @param amount 扣款金额：null=全额消费，0=全额退还，>0=消费指定金额
   * @param refundAmount 退款金额（未使用金额），用于迁移数据退订场景（无冻结记录时直接退款）
   * @return 资金系统返回的钱包信息
   */
  public FundSystemResponse<FundWalletLockData> deduct(String userId, String orderId,
                                                        BigDecimal amount, BigDecimal refundAmount) {
    FundDeductRequest request = new FundDeductRequest();
    request.setUserId(userId);
    request.setOrderId(orderId);
    request.setAmount(amount);
    request.setRefundAmount(refundAmount);

    try {
      FundSystemResponse<FundWalletLockData> response = restClient.post()
          .uri(baseUrl + "/srv/api/internal/wallets/deduct")
          .header("Authorization", "Bearer " + getToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(new ParameterizedTypeReference<FundSystemResponse<FundWalletLockData>>() {});

      if (response == null || !response.isSuccess()) {
        log.error("资金系统扣款返回异常, userId: {}, orderId: {}, response: {}", userId, orderId, response);
      }
      return response;
    } catch (Exception e) {
      log.error("调用资金系统扣款失败, userId: {}, orderId: {}, amount: {}", userId, orderId, amount, e);
      throw new RuntimeException("调用资金系统扣款失败: " + e.getMessage(), e);
    }
  }


  /**
   * 通过 OAuth2 client_credentials 获取 JWT access token
   * 与 AuthUserService 使用同一份 client 配置（veryai-api），token 自动缓存
   */
  private String getToken() {
    return "";
  }

  @Data
  public static class FundWalletLockData {
    private Long id;
    private String userId;
    private String username;
    private BigDecimal cashBalance;
    private BigDecimal pointsBalance;
    private List<Map<String, BigDecimal>> lockCashBalance;
    private List<Map<String, BigDecimal>> lockPointsBalance;
    private List<Map<String, Long>> lockCashExpireAt;
    private List<Map<String, Long>> lockPointsExpireAt;
    private BigDecimal cashTotal;
    private BigDecimal pointsTotal;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
  }

  @Data
  public static class FundLockRequest {
    private String userId;
    private BigDecimal amount;
    private String orderId;
    private String lockType;
    private String expireAt;
  }

  @Data
  public class FundDeductRequest {
    private String userId;
    private String orderId;
    private BigDecimal amount;
    /** 退款金额（未使用金额），用于迁移数据退订场景（无冻结记录时直接退款） */
    private BigDecimal refundAmount;
  }


  @Data
  public static class FundSystemResponse<T> {
    private int code;
    private String message;
    private T data;

    public boolean isSuccess() {
      return code == 200;
    }
  }
}
