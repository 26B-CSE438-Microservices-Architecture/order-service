# Order Service

Getir, Trendyol GO, Yemeksepeti gibi yemek sipariş platformlarının mikroservis mimarisine uygun **Order Microservice** implementasyonu. Sipariş yönetiminin tüm yaşam döngüsünü kapsar: sepet, ödeme akışı (Hold & Capture), restoran onayı ve teslimat.

## Teknoloji Stack

| Katman | Teknoloji |
|---|---|
| Runtime | Java 21, Spring Boot 3.2 |
| Veritabanı | PostgreSQL 15 |
| Mesajlaşma | Apache Kafka (Confluent 7.4) |
| Auth | JWT (JJWT 0.11) |
| Service Discovery | Spring Cloud OpenFeign |
| Resilience | Resilience4j (Circuit Breaker + Retry) |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Test | JUnit 5, Mockito, AssertJ, H2 |

---

## Mimari

### Temel Tasarım Kararları

**Clean Use Case Architecture** — Her iş operasyonu bağımsız bir `UseCase` sınıfında yaşar (tek sorumluluk). Controller'lar yalnızca HTTP adaptörü görevindedir.

**Transactional Outbox Pattern** — Kafka'ya direkt publish yerine, event'ler önce aynı DB transaction içinde `outbox_events` tablosuna yazılır. `OutboxRelayScheduler` periyodik olarak bu tabloyu okuyup Kafka'ya iletir. Bu sayede DB ve Kafka yazımı arasında hiçbir zaman tutarsızlık olmaz.

```
Use Case → DB (Order + OutboxEvent) → OutboxRelayScheduler → Kafka
```

**Hold & Capture Saga** — Ödeme iki aşamalı işlenir:
1. Checkout → Payment Service'ten para tutma (Hold) isteği
2. Restoran onayı → Payment Service'ten gerçek çekim (Capture) isteği

Herhangi bir adımda hata olursa hold otomatik serbest bırakılır.

**OrderStateMachine** — Tüm durum geçişleri `EnumMap` tabanlı bir state machine ile doğrulanır. Geçersiz bir transition `IllegalStateException` fırlatır.

**Optimistic Locking** — `Order` entity'si `@Version` alanı taşır; eş zamanlı güncelleme çakışmalarını önler.

**Idempotency** — Checkout endpoint'i `Idempotency-Key` header'ı ile korunur; aynı key ile gelen tekrar istekler mevcut siparişi döndürür.

---

## Sipariş Yaşam Döngüsü

```
CREATED
  └─► PAYMENT_PENDING          (Checkout: ödeme hold isteği gönderildi)
        ├─► PAYMENT_HELD        (Hold onaylandı: restoran onayı bekleniyor)
        │     ├─► CONFIRMED_BY_RESTAURANT   (Restoran onayladı)
        │     │     └─► PAYMENT_CAPTURE_PENDING  (Capture isteği gönderildi)
        │     │           ├─► PAID              (Capture başarılı)
        │     │           │     ├─► PREPARING
        │     │           │     ├─► READY_FOR_PICKUP
        │     │           │     ├─► IN_DELIVERY
        │     │           │     ├─► DELIVERED
        │     │           │     └─► REFUND_REQUESTED
        │     │           └─► PAYMENT_FAILED   → CANCELLED
        │     └─► CANCELLED     (Hold release isteği gönderilir)
        └─► PAYMENT_FAILED      → CANCELLED
```

---

## API Endpoints

> Tüm endpoint'ler `Authorization: Bearer <JWT>` header'ı gerektirir (iç endpoint'ler hariç).

### Sepet — `/cart`

| Method | Path | Açıklama |
|---|---|---|
| `GET` | `/cart` | Kullanıcının aktif sepetini getir |
| `POST` | `/cart/items` | Sepete ürün ekle |
| `PUT` | `/cart/items/{itemId}` | Sepet ürün miktarını güncelle |
| `DELETE` | `/cart/items/{itemId}` | Sepetten ürün çıkar |
| `DELETE` | `/cart` | Sepeti tamamen temizle |
| `POST` | `/cart/checkout` | Sepeti siparişe dönüştür |

**Checkout özel header'ları:**
- `Idempotency-Key` *(zorunlu)* — Tekrar isteği önlemek için
- `X-Correlation-Id` *(opsiyonel)* — Servisler arası izleme

**Checkout request body:**
```json
{
  "deliveryAddress": {
    "street": "Bağdat Caddesi No:1",
    "district": "Kadıköy",
    "city": "İstanbul",
    "postalCode": "34710",
    "lat": 40.9817,
    "lng": 29.0567
  },
  "paymentMethod": "CREDIT_CARD",
  "orderType": "DELIVERY",
  "notes": "Zil çalışmıyor, lütfen arayın"
}
```

---

### Müşteri Siparişleri — `/orders`

| Method | Path | Açıklama |
|---|---|---|
| `GET` | `/orders/{orderId}` | Sipariş detayı (ADMIN tümünü, CUSTOMER sadece kendi siparişini görebilir) |
| `GET` | `/orders/my` | Kendi siparişlerini listele — `?status=DELIVERED&page=0&size=10` |
| `POST` | `/orders/{orderId}/cancel` | Siparişi iptal et |
| `POST` | `/orders/{orderId}/reorder` | Siparişi tekrar sepete ekle |
| `POST` | `/orders/{orderId}/request-refund` | İade talebi oluştur (sipariş `PAID` durumunda olmalı) |

---

### Restoran Siparişleri — `/orders/restaurant`

| Method | Path | Açıklama |
|---|---|---|
| `GET` | `/orders/restaurant` | Restorana ait siparişleri listele — `?status=PAID&page=0&size=20` |
| `PATCH` | `/orders/restaurant/{orderId}/confirm` | Siparişi onayla (Capture akışını tetikler) |
| `PATCH` | `/orders/restaurant/{orderId}/reject` | Siparişi reddet (Hold release tetikler) |
| `PATCH` | `/orders/restaurant/{orderId}/status` | Sipariş durumunu güncelle (PREPARING → READY → IN_DELIVERY → DELIVERED) |

---

### İç Endpoint — `/internal/orders`

> JWT yerine `X-Internal-Secret` header'ı ile korunur. Sadece aynı internal ağdaki servisler erişebilir.

| Method | Path | Açıklama |
|---|---|---|
| `POST` | `/internal/orders/{orderId}/payment-callback` | Payment Service'ten gelen ödeme sonucu |

**Desteklenen `status` değerleri:**

| Status | İşlem |
|---|---|
| `HOLD_CONFIRMED` | Para tutuldu → `PAYMENT_HELD`, restorana bildirim gönder |
| `HOLD_FAILED` | Para tutma başarısız → `CANCELLED` |
| `CAPTURE_COMPLETED` | Ödeme çekildi → `PAID`, sipariş onaylandı |
| `CAPTURE_FAILED` | Ödeme çekimi başarısız → `CANCELLED` |
| `HOLD_RELEASED` | Para tutma serbest bırakıldı |

---

## Kafka Event'leri

### Yayınlanan Event'ler (Outbox → Kafka)

| Topic | Tetikleyici |
|---|---|
| `order.payment.hold.requested` | Checkout tamamlandı |
| `order.restaurant.approval.requested` | Payment hold onaylandı |
| `order.payment.capture.requested` | Restoran siparişi onayladı |
| `order.payment.hold.release.requested` | İptal (hold edilmişse) |
| `order.confirmed` | Ödeme capture tamamlandı |
| `order.cancelled` | Sipariş herhangi bir nedenle iptal edildi |
| `order.ready` | Sipariş teslimata hazır |
| `order.delivered` | Sipariş teslim edildi |
| `order.refund.requested` | İade talebi oluşturuldu |

### Dinlenen Event'ler (Kafka → Handler)

| Topic | Handler | İşlem |
|---|---|---|
| `payment.hold_confirmed` | `PaymentHoldConfirmedEventHandler` | `PAYMENT_PENDING` → `PAYMENT_HELD`, restoran onayı iste |
| `payment.hold_failed` | `PaymentHoldFailedEventHandler` | `PAYMENT_PENDING` → `CANCELLED` |
| `payment.capture_completed` | `PaymentCaptureCompletedEventHandler` | `PAYMENT_CAPTURE_PENDING` → `PAID` |
| `payment.capture_failed` | `PaymentCaptureFailedEventHandler` | `PAYMENT_CAPTURE_PENDING` → `CANCELLED` |
| `payment.hold_released` | `PaymentHoldReleasedEventHandler` | PaymentStatus → `RELEASED` |
| `payment.refunded` | `PaymentRefundedEventHandler` | PaymentStatus → `REFUNDED` |

> Tüm consumer handler'ları `@Transactional` çalışır. `enable.auto.commit=false` + `AckMode.RECORD` ile DB başarısız olursa Kafka offset commit edilmez, mesaj yeniden işlenir.

---

## Güvenlik

### JWT Kimlik Doğrulama
`Authorization: Bearer <token>` header'ından okunur. Token içinden `userId` ve `role` (`CUSTOMER`, `RESTAURANT_OWNER`, `ADMIN`) çekilir.

### Internal Secret Filtresi
`/internal/**` path'lerine gelen tüm istekler `InternalSecretFilter` tarafından kontrol edilir. `X-Internal-Secret` header'ı eksik veya yanlışsa `401 Unauthorized` döner.

---

## Ortam Değişkenleri

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/orderdb` | PostgreSQL bağlantı URL'i |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB kullanıcı adı |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | DB şifresi |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker adresi |
| `JWT_SECRET` | *(zorunlu)* | JWT imzalama anahtarı (min 256-bit) |
| `INTERNAL_SECRET` | `change-me-in-production` | Internal servisler arası paylaşılan gizli anahtar |
| `RESTAURANT_SERVICE_URL` | `http://restaurant-service:8083` | Restaurant Service URL |
| `PAYMENT_SERVICE_URL` | `http://payment-service:8084` | Payment Service URL |
| `PAYMENT_TIMEOUT_MINUTES` | `10` | Ödeme bekleme süresi (dakika) |
| `RESTAURANT_TIMEOUT_MINUTES` | `5` | Restoran onay bekleme süresi (dakika) |
| `ORDER_DELIVERY_FEE` | `15.00` | Sabit teslimat ücreti (TRY) |

---

## Çalıştırma

### Bağımlılıkları Başlat (Docker Compose)

```bash
# Proje kökünde (orderService/)
docker-compose up -d
```

PostgreSQL (port `5433`) ve Kafka (port `9092`) başlar.

### Uygulamayı Başlat

```bash
cd orderService
mvn spring-boot:run
```

Uygulama `http://localhost:8082` adresinde çalışır.

### Swagger UI

```
http://localhost:8082/swagger-ui.html
```

---

## Testler

Proje **~115 test** içerir:

| Katman | Araç | Kapsam |
|---|---|---|
| **Domain (Unit)** | JUnit 5 + AssertJ | `Money`, `Cart`, `Order` değer nesneleri ve domain mantığı |
| **State Machine (Unit)** | JUnit 5 + `@ParameterizedTest` | Tüm geçerli ve geçersiz durum geçişleri |
| **Use Case (Unit)** | Mockito | Checkout, sepet, iptal, onay, iade, reorder iş akışları |
| **Event Handler (Unit)** | Mockito | Tüm Kafka consumer handler'ları ve Outbox relay scheduler |
| **Repository (Integration)** | `@DataJpaTest` + H2 | JPA sorguları, pagination, timeout filtreleri |
| **Controller (Component)** | `@WebMvcTest` + MockMvc | HTTP katmanı, request/response doğrulama, yetkilendirme |
| **Security (Unit)** | Servlet mock | `InternalSecretFilter` geçerli/geçersiz/eksik secret senaryoları |

```bash
# Tüm testleri çalıştır
mvn test

# Belirli bir test sınıfı
mvn test -Dtest=OrderStateMachineTest
```

---

## Proje Yapısı

```
src/main/java/com/foodapp/orderservice/
├── application/
│   ├── cart/          # Add, Remove, Update, Get, Clear, Checkout use case'leri
│   └── order/         # Get, List, Cancel, Confirm, Reject, UpdateStatus, Reorder, RequestRefund
├── config/
│   ├── jwt/           # JwtAuthenticationFilter, AuthenticatedUser
│   ├── scheduler/     # OutboxRelayScheduler, OrderTimeoutScheduler
│   ├── InternalSecretFilter.java
│   ├── KafkaConfig.java
│   ├── SchedulerConfig.java
│   └── SecurityConfig.java
├── controller/        # CartController, OrderController, RestaurantOrderController, InternalOrderController
├── domain/
│   ├── aggregate/     # Order (ana aggregate, @Version ile optimistic locking)
│   ├── entity/        # Cart, CartItem, OrderItem, OrderStatusHistory, OutboxEvent
│   ├── enums/         # OrderStatus, PaymentStatus, OrderType, PaymentMethod, vb.
│   ├── statemachine/  # OrderStateMachine (EnumMap tabanlı)
│   └── valueobject/   # Money (immutable), Address
├── dto/
│   ├── request/       # CheckoutRequest, AddCartItemRequest, PaymentCallbackRequest, vb.
│   └── response/      # OrderResponse, CartResponse, PageResponse, vb.
├── event/
│   ├── consumer/      # 6 Kafka event handler (@Transactional)
│   └── producer/      # OrderEventPublisher (Outbox tabanlı)
├── exception/         # GlobalExceptionHandler, domain exception sınıfları
├── gateway/           # RestaurantGateway (Feign + Resilience4j Circuit Breaker + Retry)
└── repository/        # OrderRepository, OutboxEventRepository, CartRepository, vb.
```
