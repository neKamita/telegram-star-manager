# 🔧 Техническая документация - Интеграция платежных систем

## 📋 Обзор

TelegramStarManager поддерживает интеграцию с четырьмя основными платежными системами:
- **TON Wallet** - криптоплатежи на блокчейне TON
- **YooKassa** - российская платежная система
- **QIWI** - электронный кошелек и платежи
- **SberPay** - платежная система Сбербанка

## 🏗️ Архитектура платежной системы

```
┌─────────────────── АРХИТЕКТУРА ПЛАТЕЖЕЙ ───────────────────┐
│                                                            │
│  ┌─────────────────┐                 ┌─────────────────────┐│
│  │ PaymentService  │◄────────────────┤ PaymentController   ││
│  │                 │                 │                     ││
│  │ • createPayment │                 │ • POST /payment     ││
│  │ • processPayment│                 │ • GET /status       ││
│  │ • verifyCallback│                 │ • POST /callback/*  ││
│  └─────────────────┘                 └─────────────────────┘│
│           │                                    │            │
│           ▼                                    ▼            │
│  ┌─────────────────┐                 ┌─────────────────────┐│
│  │ PaymentEntity   │                 │ CallbackHandler     ││
│  │                 │                 │                     ││
│  │ • ID, amount    │                 │ • verifySignature   ││
│  │ • status, method│                 │ • processWebhook    ││
│  │ • timestamps    │                 │ • updateStatus      ││
│  └─────────────────┘                 └─────────────────────┘│
│           │                                    │            │
│           ▼                                    ▼            │
│  ┌─────────────────┐                 ┌─────────────────────┐│
│  │ PaymentRepo     │                 │ External APIs       ││
│  │                 │                 │                     ││
│  │ • findByPaymentId│                │ • TON API          ││
│  │ • save          │                 │ • YooKassa API     ││
│  │ • findExpired   │                 │ • QIWI API         ││
│  └─────────────────┘                 │ • SberPay API      ││
│                                      └─────────────────────┘│
└────────────────────────────────────────────────────────────┘
```

## ⚙️ Конфигурация

### Основной файл конфигурации
Создайте `application-payment.properties`:

```properties
# ============================================
# ОБЩИЕ НАСТРОЙКИ ПЛАТЕЖНОЙ СИСТЕМЫ
# ============================================

payment.general.callback-base-url=${PAYMENT_CALLBACK_BASE_URL:https://yourdomain.com}
payment.general.payment-timeout-minutes=30
payment.general.max-retry-attempts=3
payment.general.retry-interval-minutes=5
payment.general.enable-detailed-logging=true
payment.general.callback-secret=${PAYMENT_CALLBACK_SECRET}

# ============================================
# НАСТРОЙКИ TON WALLET
# ============================================

payment.ton.enabled=${TON_ENABLED:false}
payment.ton.api-key=${TON_API_KEY:}
payment.ton.secret-key=${TON_SECRET_KEY:}
payment.ton.api-url=${TON_API_URL:https://toncenter.com/api/v3}
payment.ton.webhook-url=${TON_WEBHOOK_URL:}
payment.ton.wallet-address=${TON_WALLET_ADDRESS:}
payment.ton.network-fee-percent=1.0

# ============================================
# НАСТРОЙКИ YOOKASSA
# ============================================

payment.yookassa.enabled=${YOOKASSA_ENABLED:false}
payment.yookassa.shop-id=${YOOKASSA_SHOP_ID:}
payment.yookassa.secret-key=${YOOKASSA_SECRET_KEY:}
payment.yookassa.api-url=${YOOKASSA_API_URL:https://api.yookassa.ru/v3}
payment.yookassa.webhook-url=${YOOKASSA_WEBHOOK_URL:}
payment.yookassa.auto-capture=true
payment.yookassa.request-timeout-seconds=30

# ============================================
# НАСТРОЙКИ QIWI
# ============================================

payment.qiwi.enabled=${QIWI_ENABLED:false}
payment.qiwi.public-key=${QIWI_PUBLIC_KEY:}
payment.qiwi.secret-key=${QIWI_SECRET_KEY:}
payment.qiwi.api-url=${QIWI_API_URL:https://api.qiwi.com}
payment.qiwi.webhook-url=${QIWI_WEBHOOK_URL:}
payment.qiwi.site-id=${QIWI_SITE_ID:}
payment.qiwi.default-currency=RUB
payment.qiwi.bill-lifetime-minutes=60

# ============================================
# НАСТРОЙКИ SBERPAY
# ============================================

payment.sberpay.enabled=${SBERPAY_ENABLED:false}
payment.sberpay.merchant-id=${SBERPAY_MERCHANT_ID:}
payment.sberpay.secret-key=${SBERPAY_SECRET_KEY:}
payment.sberpay.api-url=${SBERPAY_API_URL:https://securepayments.sberbank.ru}
payment.sberpay.webhook-url=${SBERPAY_WEBHOOK_URL:}
payment.sberpay.api-login=${SBERPAY_API_LOGIN:}
payment.sberpay.api-password=${SBERPAY_API_PASSWORD:}
payment.sberpay.test-mode=${SBERPAY_TEST_MODE:true}
payment.sberpay.default-currency-code=643
```

## 🔗 API интеграции

### 1. TON Wallet Integration

#### Создание платежа TON:
```http
POST https://toncenter.com/api/v3/wallet/deploy
Authorization: Bearer {TON_API_KEY}
Content-Type: application/json

{
  "address": "{TON_WALLET_ADDRESS}",
  "amount": "{amount_in_nanotons}",
  "payload": "{payment_id}",
  "callback_url": "{CALLBACK_URL}/ton"
}
```

#### Webhook от TON:
```json
{
  "transaction_id": "12345678",
  "from_address": "EQD...",
  "to_address": "EQB...", 
  "amount": "1000000000",
  "payload": "PAY_1734012345_7892",
  "timestamp": 1734012345,
  "signature": "abc123..."
}
```

#### Верификация подписи TON:
```java
private boolean verifyTonSignature(String payload, String signature, String secretKey) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes());
        String expectedSignature = Base64.getEncoder().encodeToString(hash);
        
        return MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes());
    } catch (Exception e) {
        log.error("Ошибка верификации подписи TON: {}", e.getMessage());
        return false;
    }
}
```

### 2. YooKassa Integration

#### Создание платежа YooKassa:
```http
POST https://api.yookassa.ru/v3/payments
Authorization: Basic {base64(shop_id:secret_key)}
Idempotence-Key: {payment_id}
Content-Type: application/json

{
  "amount": {
    "value": "100.00",
    "currency": "RUB"
  },
  "confirmation": {
    "type": "redirect",
    "return_url": "https://yourdomain.com/payment/return"
  },
  "description": "Пополнение баланса",
  "metadata": {
    "payment_id": "PAY_1734012345_7892",
    "user_id": "123456789"
  }
}
```

#### Webhook от YooKassa:
```json
{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "abc123-def456",
    "status": "succeeded",
    "amount": {
      "value": "100.00",
      "currency": "RUB"
    },
    "metadata": {
      "payment_id": "PAY_1734012345_7892",
      "user_id": "123456789"
    },
    "created_at": "2024-12-12T12:30:45.123Z"
  }
}
```

#### Верификация webhook YooKassa:
```java
private boolean verifyYooKassaWebhook(String payload, String signature, String secretKey) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes());
        String expectedSignature = Hex.encodeHexString(hash);
        
        return MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes());
    } catch (Exception e) {
        log.error("Ошибка верификации YooKassa webhook: {}", e.getMessage());
        return false;
    }
}
```

### 3. QIWI Integration

#### Создание счета QIWI:
```http
PUT https://api.qiwi.com/partner/bill/v1/bills/{payment_id}
Authorization: Bearer {QIWI_SECRET_KEY}
Content-Type: application/json

{
  "amount": {
    "currency": "RUB",
    "value": "100.00"
  },
  "comment": "Пополнение баланса",
  "expirationDateTime": "2024-12-12T13:30:00+03:00",
  "customer": {
    "email": "user@example.com"
  },
  "customFields": {
    "paySourcesFilter": "card,qw"
  }
}
```

#### Webhook от QIWI:
```json
{
  "bill": {
    "siteId": "abc123",
    "billId": "PAY_1734012345_7892",
    "amount": {
      "currency": "RUB",
      "value": "100.00"
    },
    "status": {
      "value": "PAID",
      "changedDateTime": "2024-12-12T12:30:45+03:00"
    }
  },
  "version": "1"
}
```

#### Верификация QIWI webhook:
```java
private boolean verifyQiwiWebhook(String payload, String signature, String secretKey) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes());
        String expectedSignature = Base64.getEncoder().encodeToString(hash);
        
        return MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes());
    } catch (Exception e) {
        log.error("Ошибка верификации QIWI webhook: {}", e.getMessage());
        return false;
    }
}
```

### 4. SberPay Integration

#### Создание заказа SberPay:
```http
POST https://securepayments.sberbank.ru/payment/rest/register.do
Content-Type: application/x-www-form-urlencoded

userName={SBERPAY_API_LOGIN}&
password={SBERPAY_API_PASSWORD}&
orderNumber={payment_id}&
amount={amount_in_kopecks}&
returnUrl=https://yourdomain.com/payment/return&
description=Пополнение баланса&
jsonParams={"user_id":"123456789"}
```

#### Webhook от SberPay:
```json
{
  "orderNumber": "PAY_1734012345_7892",
  "orderId": "abc123-def456",
  "amount": 10000,
  "currency": "643",
  "orderStatus": 2,
  "actionCode": 0,
  "actionCodeDescription": "Успешно",
  "date": "20241212123045",
  "ip": "192.168.1.1"
}
```

## 🔐 Webhook Endpoints

### Настройка callback URL'ов:

```java
@RestController
@RequestMapping("/api/payment/callback")
@Slf4j
public class PaymentCallbackController {
    
    @PostMapping("/ton")
    public ResponseEntity<String> handleTonCallback(
            @RequestBody String payload,
            @RequestHeader("X-TON-Signature") String signature) {
        
        log.info("Получен TON callback: {}", payload);
        
        if (!verifyTonSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        // Обработка callback
        return ResponseEntity.ok("OK");
    }
    
    @PostMapping("/yookassa")
    public ResponseEntity<String> handleYooKassaCallback(
            @RequestBody String payload,
            @RequestHeader("X-YooKassa-Signature") String signature) {
        
        log.info("Получен YooKassa callback: {}", payload);
        
        if (!verifyYooKassaSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        // Обработка callback
        return ResponseEntity.ok("OK");
    }
    
    @PostMapping("/qiwi")
    public ResponseEntity<String> handleQiwiCallback(
            @RequestBody String payload,
            @RequestHeader("X-Api-Signature-SHA256") String signature) {
        
        log.info("Получен QIWI callback: {}", payload);
        
        if (!verifyQiwiSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        // Обработка callback
        return ResponseEntity.ok("OK");
    }
    
    @PostMapping("/sberpay")
    public ResponseEntity<String> handleSberPayCallback(
            @RequestParam Map<String, String> params) {
        
        log.info("Получен SberPay callback: {}", params);
        
        if (!verifySberPayCallback(params)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid data");
        }
        
        // Обработка callback
        return ResponseEntity.ok("OK");
    }
}
```

## 🛡️ Безопасность

### 1. Верификация подписей
Все webhook'и должны проверяться на подлинность:

```java
@Component
public class WebhookSecurityService {
    
    public boolean verifyWebhookSignature(String provider, String payload, String signature, String secret) {
        return switch (provider.toLowerCase()) {
            case "ton" -> verifyTonSignature(payload, signature, secret);
            case "yookassa" -> verifyYooKassaSignature(payload, signature, secret);
            case "qiwi" -> verifyQiwiSignature(payload, signature, secret);
            case "sberpay" -> verifySberPaySignature(payload, signature, secret);
            default -> {
                log.warn("Неизвестный провайдер: {}", provider);
                yield false;
            }
        };
    }
}
```

### 2. Rate Limiting
Ограничение количества запросов к callback'ам:

```java
@Component
public class CallbackRateLimiter {
    private final Map<String, List<Long>> requestCounts = new ConcurrentHashMap<>();
    private final int maxRequestsPerMinute = 100;
    
    public boolean isAllowed(String ip) {
        long currentTime = System.currentTimeMillis();
        List<Long> requests = requestCounts.computeIfAbsent(ip, k -> new ArrayList<>());
        
        // Удаляем старые запросы
        requests.removeIf(time -> currentTime - time > 60000);
        
        if (requests.size() >= maxRequestsPerMinute) {
            return false;
        }
        
        requests.add(currentTime);
        return true;
    }
}
```

### 3. IP Whitelist
Проверка IP адресов платежных систем:

```properties
# IP адреса платежных систем для whitelist
payment.security.allowed-ips.ton=95.142.46.34,95.142.46.35
payment.security.allowed-ips.yookassa=185.71.76.0/27,185.71.77.0/27
payment.security.allowed-ips.qiwi=79.142.16.0/20,195.189.100.0/22
payment.security.allowed-ips.sberpay=185.71.76.0/27,212.19.125.0/25
```

## 📊 Мониторинг и логирование

### 1. Метрики платежей:
```java
@Component
public class PaymentMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordPaymentAttempt(String provider) {
        meterRegistry.counter("payment.attempts", "provider", provider).increment();
    }
    
    public void recordPaymentSuccess(String provider, double amount) {
        meterRegistry.counter("payment.success", "provider", provider).increment();
        meterRegistry.counter("payment.amount", "provider", provider).increment(amount);
    }
    
    public void recordPaymentFailure(String provider, String reason) {
        meterRegistry.counter("payment.failures", 
            "provider", provider, "reason", reason).increment();
    }
}
```

### 2. Structured logging:
```java
@Slf4j
public class PaymentLogger {
    
    public void logPaymentCreated(PaymentEntity payment) {
        log.info("payment_created user_id={} payment_id={} amount={} method={}", 
            payment.getUserId(), payment.getPaymentId(), 
            payment.getAmount(), payment.getPaymentMethod());
    }
    
    public void logPaymentCompleted(PaymentEntity payment) {
        log.info("payment_completed user_id={} payment_id={} amount={} method={} duration={}ms", 
            payment.getUserId(), payment.getPaymentId(), 
            payment.getAmount(), payment.getPaymentMethod(),
            ChronoUnit.MILLIS.between(payment.getCreatedAt(), payment.getUpdatedAt()));
    }
    
    public void logWebhookReceived(String provider, String paymentId) {
        log.info("webhook_received provider={} payment_id={}", provider, paymentId);
    }
}
```

## 🔄 Retry Logic

### Обработка неудачных callback'ов:
```java
@Service
public class PaymentRetryService {
    
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public void processPaymentCallback(String paymentId, Map<String, String> data) {
        try {
            PaymentEntity payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
            
            // Обработка callback
            processSuccessfulPayment(payment);
            
        } catch (Exception e) {
            log.error("Ошибка обработки callback для платежа {}: {}", paymentId, e.getMessage());
            throw e; // Для повторной попытки
        }
    }
    
    @Recover
    public void recoverFromFailedCallback(Exception ex, String paymentId, Map<String, String> data) {
        log.error("Исчерпаны попытки обработки callback для платежа {}: {}", paymentId, ex.getMessage());
        
        // Отправка уведомления администратору
        notificationService.sendAdminAlert("Failed payment callback", paymentId, ex.getMessage());
    }
}
```

## 🧪 Тестирование

### 1. Unit тесты для верификации:
```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    @Test
    void shouldVerifyTonSignature() {
        String payload = "{\"payment_id\":\"TEST_123\",\"amount\":\"100\"}";
        String secret = "test-secret";
        String validSignature = generateTonSignature(payload, secret);
        
        assertTrue(paymentService.verifyTonSignature(payload, validSignature, secret));
    }
    
    @Test
    void shouldRejectInvalidSignature() {
        String payload = "{\"payment_id\":\"TEST_123\",\"amount\":\"100\"}";
        String secret = "test-secret";
        String invalidSignature = "invalid-signature";
        
        assertFalse(paymentService.verifyTonSignature(payload, invalidSignature, secret));
    }
}
```

### 2. Integration тесты:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentIntegrationTest {
    
    @Test
    void shouldHandleValidTonCallback() {
        String validPayload = createValidTonCallback();
        String validSignature = generateTonSignature(validPayload, tonSecret);
        
        ResponseEntity<String> response = testRestTemplate.postForEntity(
            "/api/payment/callback/ton",
            createCallbackRequest(validPayload, validSignature),
            String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }
}
```

## 🚨 Troubleshooting

### Частые проблемы:

#### 1. Неверная подпись webhook
```
Ошибка: HTTP 401 - Invalid signature
Решение: 
- Проверить секретный ключ
- Убедиться в правильном алгоритме подписи
- Проверить кодировку данных
```

#### 2. Timeout платежей
```
Ошибка: Payment timeout (>30 минут)
Решение:
- Проверить связь с API платежной системы
- Увеличить timeout в конфигурации
- Проверить статус в личном кабинете провайдера
```

#### 3. Дублирование платежей
```
Ошибка: Duplicate payment processing
Решение:
- Добавить idempotency key
- Проверить уникальность payment_id
- Добавить блокировки на уровне БД
```

### Отладка:
```bash
# Включить детальное логирование
export PAYMENT_DEBUG=true
export LOGGING_LEVEL_SHIT_BACK_SERVICE_PAYMENT=DEBUG

# Проверить webhook endpoint
curl -X POST https://yourdomain.com/api/payment/callback/test \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'

# Мониторинг логов
tail -f logs/payment.log | grep -E "(payment_|webhook_)"
```

---

*Последнее обновление: 12 декабря 2024*