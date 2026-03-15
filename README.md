# Order Service

Sipariş yaşam döngüsünü (cart → checkout → payment hold → restaurant approval → payment capture → delivery) yöneten mikroservis.

**Tech Stack:** Java 21, Spring Boot 3.2, PostgreSQL, Apache Kafka, Spring Security (JWT)

---

## Table of Contents

1. [Responsibilities](#responsibilities)
2. [Payment Flow (Hold & Capture)](#payment-flow-hold--capture)
3. [Order State Machine](#order-state-machine)
4. [Domain Events](#domain-events)
5. [Integration Map](#integration-map)
6. [REST API](#rest-api)
7. [Architecture](#architecture)
8. [Database Schema](#database-schema)
9. [Configuration](#configuration)

---

## Responsibilities

- Sepet yönetimi (ekleme, çıkarma, güncelleme)
- Sipariş oluşturma ve fiyat snapshot'ı alma
- Ödeme hold/capture akışı koordinasyonu
- Restoran onay akışı yönetimi
- Sipariş durum yönetimi ve geçmiş kaydı
- İptal ve iade kuralları
- Sipariş listeleme / detay görüntüleme
- Zaman aşımı kontrolü (payment ve restaurant timeout)
- Kafka üzerinden event yayınlama (Transactional Outbox Pattern)

---

## Payment Flow (Hold & Capture)

Kullanıcının parası sipariş anında değil, yalnızca restoran onayladıktan sonra çekilir.

```
Kullanıcı "Sipariş Ver" → Ödeme Authorize (Hold) + Restorana Bildirim
                                    │
              ┌─────────────────────┴──────────────────────┐
              │ Restoran Onaylarsa                          │ Restoran Reddederse / Timeout
              ▼                                             ▼
     Payment Capture (Para Çekilir)              Hold Release (Para Asla Çekilmez)
              │                                             │
              ▼                                             ▼
        PAID → PREPARING...                           CANCELLED
```

### Payment Service Entegrasyonu

Payment Service, HTTP REST API üzerinden çağrılır ve RabbitMQ üzerinden event yayınlar.

| Aksiyon | Order Service Çağrısı | Payment Service Yanıtı |
|---|---|---|
| Sipariş ver → para tut | `POST /payments` | `AUTHORIZED` veya `FAILED` |
| Restoran onayladı → para çek | `POST /payments/:id/capture` | `CAPTURED` |
| Restoran reddetti/iptal → hold serbest | `POST /payments/:id/cancel` | `VOIDED` veya `REFUNDED` |

**Payment Service Payment Durumları:**

| Payment Service Durumu | Order Service Karşılığı |
|---|---|
| `AUTHORIZED` | `PAYMENT_HELD` — Para tutuldu |
| `CAPTURED` | `PAID` — Para çekildi |
| `VOIDED` | Hold serbest bırakıldı (kullanıcı ücretlendirilmedi) |
| `FAILED` | `PAYMENT_FAILED` |
| `REFUNDED` | `REFUNDED` |

**Payment Service Events (RabbitMQ, `payment.events` exchange):**

| Routing Key | Order Service Aksiyonu |
|---|---|
| `payment.authorized` | Sipariş → `PAYMENT_HELD`, restorana bildirim gönder |
| `payment.failed` | Sipariş → `PAYMENT_FAILED` → `CANCELLED` |
| `payment.captured` | Sipariş → `PAID`, restoran hazırlamaya başlayabilir |
| `payment.voided` | Hold serbest bırakıldı, `paymentStatus = RELEASED` |
| `payment.refunded` | Sipariş → `REFUNDED` |

---

## Order State Machine

```
CREATED
  │
  └─► PAYMENT_PENDING ──────────────────────────────────────────────────────┐
        │  (POST /payments çağrıldı, hold bekleniyor)                      │
        │                                                                   │
        ├─► PAYMENT_HELD ─────────────────────────────────────────────────►┤
        │     │  (payment.authorized geldi, restorana bildirim gönderildi) │
        │     │                                                             │
        │     ├─► CONFIRMED_BY_RESTAURANT                                  │
        │     │       │  (restoran onayladı, POST /capture çağrıldı)       │
        │     │       └─► PAYMENT_CAPTURE_PENDING                          │
        │     │                 │  (payment.captured bekleniyor)           │
        │     │                 └─► PAID ─► PREPARING ─► READY_FOR_PICKUP  │
        │     │                               ─► ON_THE_WAY ─► DELIVERED   │
        │     │                                                             │
        │     ├─► REJECTED_BY_RESTAURANT ─► CANCELLED ◄────────────────────┤
        │     │       (POST /cancel → VOIDED)                              │
        │     │                                                             │
        │     └─► RESTAURANT_TIMEOUT ─► CANCELLED ◄───────────────────────┤
        │               (POST /cancel → VOIDED)                            │
        │                                                                   │
        ├─► PAYMENT_FAILED ─► CANCELLED ◄──────────────────────────────────┤
        │     (payment.failed geldi)                                        │
        │                                                                   │
        └─► EXPIRED (payment timeout, scheduler tarafından)                │
                                                                            │
CANCELLED ─► REFUND_REQUESTED ─► REFUNDED                                  │
  ◄──────────────────────────────────────────────────────────────────────────┘
```

### OrderStatus Enum

| Status | Açıklama |
|---|---|
| `CREATED` | Sipariş oluşturuldu |
| `PAYMENT_PENDING` | Payment service'e authorize isteği gönderildi |
| `PAYMENT_HELD` | Para tutuldu (AUTHORIZED), restorana bildirim gönderildi |
| `PAYMENT_CAPTURE_PENDING` | Restoran onayladı, capture isteği gönderildi |
| `PAYMENT_FAILED` | Ödeme başarısız (hold veya capture) |
| `PAID` | Para çekildi (CAPTURED) |
| `CONFIRMED_BY_RESTAURANT` | Restoran onayladı (geçici, hemen CAPTURE_PENDING'e geçer) |
| `REJECTED_BY_RESTAURANT` | Restoran reddetti |
| `RESTAURANT_TIMEOUT` | Restoran süre doldu |
| `PREPARING` | Restoran hazırlıyor |
| `READY_FOR_PICKUP` | Sipariş hazır |
| `ON_THE_WAY` | Kuryede |
| `DELIVERED` | Teslim edildi (terminal) |
| `CANCELLED` | İptal edildi |
| `EXPIRED` | Ödeme zaman aşımı (terminal) |
| `REFUND_REQUESTED` | İade talep edildi |
| `REFUNDED` | İade tamamlandı (terminal) |

### PaymentStatus Enum

| Status | Açıklama |
|---|---|
| `PENDING` | Henüz ödeme işlemi yok |
| `HELD` | Para tutuldu (AUTHORIZED) |
| `PAID` | Para çekildi (CAPTURED) |
| `FAILED` | Ödeme başarısız |
| `RELEASED` | Hold serbest bırakıldı (VOIDED), kullanıcı ücretlendirilmedi |
| `REFUNDED` | Para iade edildi |

---

## Domain Events

### Order Service'in Yayınladığı Eventler (Kafka, Outbox Pattern)

| Event / Topic | Ne Zaman | Tüketen |
|---|---|---|
| `order.restaurant.approval.requested` | `PAYMENT_HELD` durumuna girilince | Restaurant Service |
| `order.confirmed` | `PAID` durumuna girilince | Restaurant Service, Notification |
| `order.cancelled` | Sipariş iptal edilince | Notification Service |
| `order.ready` | `READY_FOR_PICKUP` olunca | Notification Service |
| `order.delivered` | `DELIVERED` olunca | Notification Service, Analytics |

### Order Service'in Tükettiği Eventler (RabbitMQ, Payment Service)

| Routing Key | Aksiyon |
|---|---|
| `payment.authorized` | Sipariş → `PAYMENT_HELD`, restorana bildirim |
| `payment.captured` | Sipariş → `PAID` |
| `payment.failed` | Sipariş → `PAYMENT_FAILED` → `CANCELLED` |
| `payment.voided` | `paymentStatus = RELEASED` |
| `payment.refunded` | Sipariş → `REFUNDED` |

---

## Integration Map

### Payment Service

- **HTTP (Giden):** Order Service, ödeme işlemlerini Payment Service REST API'si üzerinden başlatır.
  - `POST /payments` — authorize (para tut)
  - `POST /payments/:id/capture` — capture (para çek)
  - `POST /payments/:id/cancel` — void veya refund
- **RabbitMQ (Gelen):** Payment Service, işlem sonuçlarını `payment.events` exchange üzerinden yayınlar; Order Service tüketir.
- **Callback (Opsiyonel):** `POST /internal/orders/{orderId}/payment-callback` — senkron fallback endpoint

### Restaurant Service

- **HTTP (Giden, Feign + Resilience4j):**
  - `GET /internal/restaurants/{id}/is-open` — sipariş öncesi açık mı kontrolü
  - `POST /internal/restaurants/{id}/validate-items` — fiyat snapshot + stok kontrolü
- **Kafka (Gelen):** `order.restaurant.approval.requested` — stok kontrolü ve onay için dinler
- **HTTP (Gelen):** Restaurant panel üzerinden `PATCH /orders/restaurant/{id}/confirm|reject|status`

### Notification Service / Analytics

- **Kafka (Gelen):** `order.confirmed`, `order.cancelled`, `order.ready`, `order.delivered`

---

## REST API

### Müşteri Endpointleri

```
GET    /cart                          — Aktif sepeti getir
POST   /cart/items                    — Sepete ürün ekle
PUT    /cart/items/{itemId}           — Ürün miktarını güncelle
DELETE /cart/items/{itemId}           — Ürünü sepetten çıkar
DELETE /cart                          — Sepeti temizle
POST   /cart/checkout                 — Siparişe dönüştür (Hold başlatır)

GET    /orders                        — Kullanıcının siparişlerini listele
GET    /orders/{orderId}              — Sipariş detayı
POST   /orders/{orderId}/cancel       — Siparişi iptal et
POST   /orders/{orderId}/reorder      — Önceki siparişten yeni sepet oluştur
```

### Restoran Endpointleri

```
GET    /orders/restaurant                          — Restoranın siparişlerini listele
PATCH  /orders/restaurant/{orderId}/confirm        — Siparişi onayla (Capture tetikler)
PATCH  /orders/restaurant/{orderId}/reject         — Siparişi reddet (Hold serbest bırakır)
PATCH  /orders/restaurant/{orderId}/status         — Durum güncelle (PREPARING/READY/etc.)
```

### İç Servis Endpointleri

```
POST   /internal/orders/{orderId}/payment-callback — Payment service senkron callback
```

### Checkout Request

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

Headers:
```
Authorization: Bearer <jwt>
Idempotency-Key: <uuid>
X-Correlation-Id: <uuid>
```

---

## Architecture

```
src/main/java/com/foodapp/orderservice/
├── application/
│   ├── cart/                    Use cases: Add/Remove/Update/Clear/Get/Checkout
│   └── order/                   Use cases: Confirm/Reject/Cancel/Get/List/UpdateStatus/Reorder
├── config/
│   ├── KafkaConfig.java
│   ├── SecurityConfig.java
│   ├── jwt/
│   └── scheduler/
│       ├── OutboxRelayScheduler.java    Outbox → Kafka (her 2 saniye)
│       └── OrderTimeoutScheduler.java   Payment/Restaurant timeout kontrolü (her 1 dakika)
├── controller/
│   ├── CartController.java
│   ├── OrderController.java
│   ├── RestaurantOrderController.java
│   └── InternalOrderController.java
├── domain/
│   ├── aggregate/Order.java             Root aggregate
│   ├── entity/                          Cart, CartItem, OrderItem, OrderStatusHistory, OutboxEvent
│   ├── enums/                           OrderStatus, PaymentStatus, OrderType, PaymentMethod, ...
│   ├── statemachine/OrderStateMachine.java
│   └── valueobject/                     Money, Address
├── dto/                                 Request/Response DTO'ları
├── event/
│   ├── consumer/                        RabbitMQ consumers: payment.authorized/captured/failed/voided/refunded
│   └── producer/OrderEventPublisher.java
├── exception/
├── gateway/
│   ├── PaymentGateway.java              Interface (HTTP → Payment Service)
│   ├── RestaurantGateway.java           Interface
│   └── feign/                           Feign implementasyonları (Resilience4j ile)
└── repository/                          JPA repositories
```

### Transactional Outbox Pattern

Order Service, Kafka'ya doğrudan yazmaz. Olaylar önce `outbox_events` tablosuna kaydedilir, ardından `OutboxRelayScheduler` her 2 saniyede bir Kafka'ya iletir. Bu sayede DB ve Kafka arasında tutarlılık sağlanır.

```
Use Case → DB (Order + OutboxEvent) → OutboxRelayScheduler → Kafka
```

---

## Database Schema

### `orders` tablosu

| Kolon | Tip | Açıklama |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | Müşteri |
| restaurant_id | UUID | Restoran |
| cart_id | UUID | Kaynak sepet |
| correlation_id | VARCHAR | Dağıtık izleme ID |
| status | VARCHAR | OrderStatus enum |
| order_type | VARCHAR | DELIVERY / PICKUP |
| total_amount | DECIMAL | Toplam tutar |
| currency | VARCHAR | TRY |
| delivery_fee_amount | DECIMAL | Teslimat ücreti |
| payment_id | UUID | Payment Service'teki paymentId |
| payment_status | VARCHAR | PaymentStatus enum |
| payment_method | VARCHAR | CREDIT_CARD / DEBIT_CARD / CASH_ON_DELIVERY |
| payment_timeout_at | TIMESTAMP | Hold zaman aşımı |
| restaurant_timeout_at | TIMESTAMP | Restoran onay zaman aşımı |
| cancellation_reason | VARCHAR | OrderCancellationReason enum |
| idempotency_key | VARCHAR | UNIQUE — tekrar sipariş koruması |
| version | BIGINT | Optimistic locking |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

### `outbox_events` tablosu

| Kolon | Tip | Açıklama |
|---|---|---|
| id | UUID | PK |
| aggregate_type | VARCHAR | "ORDER" |
| aggregate_id | VARCHAR | correlationId |
| event_type | VARCHAR | ORDER_CONFIRMED vb. |
| payload | TEXT | JSON |
| processed | BOOLEAN | Kafka'ya gönderildi mi |
| created_at | TIMESTAMP | |

---

## Configuration

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/orderdb
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: order-service

order:
  payment-timeout-minutes: 10     # Hold zaman aşımı
  restaurant-timeout-minutes: 5   # Restoran yanıt zaman aşımı
  delivery-fee: 15.00             # TRY

restaurant-service:
  url: http://restaurant-service:8083
payment-service:
  url: http://payment-service:8084
```

### Environment Variables

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL bağlantı string'i |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka adresi |
| `RABBITMQ_URL` | Payment Service eventleri için RabbitMQ |
| `RESTAURANT_SERVICE_URL` | Restaurant Service URL |
| `PAYMENT_SERVICE_URL` | Payment Service URL |
| `JWT_SECRET` | JWT imza anahtarı |
| `ORDER_DELIVERY_FEE` | Teslimat ücreti (TRY) |
