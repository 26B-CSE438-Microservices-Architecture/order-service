# orderService
# Order Service

**Order Service**, sipariş yaşam döngüsünü yönetir:
sipariş oluşturma, durum güncelleme, iptal, listeleme ve servisler arası koordinasyon.

---

## Responsibilities
- Sipariş oluşturma ve doğrulama (restaurant, items, address, price snapshot)
- Sipariş durum yönetimi (Created → Confirmed → Preparing → OnTheWay → Delivered / Cancelled)
- Sipariş iptali kuralları
- Sipariş listeleme/Detay görüntüleme
- Ödeme (Payment) ve Restoran (Restaurant) servisleriyle entegrasyon
- Event yayınlama (OrderCreated/OrderCancelled/OrderPaid vs.)

---

## Functional Requirements (MVP)
1. **Create Order**
   - Kullanıcı (UserId) bir restorandan ürün seçerek sipariş oluşturabilmeli.
   - Sipariş toplam tutarı ve kalemler Order içinde snapshot olarak tutulmalı.

2. **Get Order**
   - OrderId ile sipariş detayları getirilebilmeli.

3. **List Orders**
   - UserId’ye göre siparişler listelenebilmeli.
   - (Opsiyonel) RestaurantId’ye göre gelen siparişler listelenebilmeli.

4. **Cancel Order**
   - Sipariş belirli durumlarda iptal edilebilmeli (örn: Confirmed sonrası iptal kısıtlı).

5. **Update Status**
   - Restaurant/Delivery tarafı sipariş durumunu güncelleyebilmeli.

---

## Non-Functional Requirements
- Idempotency: Create/Payment callback gibi isteklerde tekrar çağrılmaya dayanıklı olmalı.
- Observability: log + correlationId + basic metrics
- Validation: input doğrulama ve anlamlı hata cevapları
- Authorization: rol bazlı (Customer/Restaurant/Admin/Delivery)

---

## Data Model (Draft)
### Order
- id (uuid)
- userId (string/int)
- restaurantId (string/int)
- status (enum)
- items: [OrderItem]
- totalAmount
- currency
- deliveryAddress (snapshot)
- paymentStatus (Pending/Paid/Failed/Refunded)
- createdAt, updatedAt

### OrderItem
- productId
- name (snapshot)
- unitPrice (snapshot)
- quantity
- lineTotal

### Status Enum (Draft)
- CREATED
- CONFIRMED
- PREPARING
- ON_THE_WAY
- DELIVERED
- CANCELLED

---

## REST API (Possible Interfaces)

### 1) Create Order
POST `/orders`
Request:
```json
{
  "userId": "20210808065",
  "restaurantId": "rest_123",
  "items": [
    { "productId": "p1", "quantity": 2 },
    { "productId": "p9", "quantity": 1 }
  ],
  "deliveryAddress": {
    "city": "Istanbul",
    "district": "Kadikoy",
    "text": "..."
  }
}
