# Generic Communication Service 🚀

Bu proje, birden fazla iletişim protokolü (REST/HTTP, RabbitMQ, WebSocket) destekleyen, mesaj takibi yapan 
ve broker yönetimi sağlayan bir Spring Boot mikroservisidir. Sistem, bir protokolden gelen mesajları farklı
protokoller üzerinden iletebilen ve her aşamada mesaj durumunu takip eden kapsamlı bir iletişim ağ geçidi 
görevi görür.

## 🔍 İçindekiler

1. [Temel Teknolojiler ve Konseptler](#temel-teknolojiler-ve-konseptler)
2. [Proje Mimarisi](#proje-mimarisi)
3. [İletişim Protokolleri](#iletişim-protokolleri)
4. [Sınıf ve Metod Analizi](#sınıf-ve-metod-analizi)
5. [Mesaj Yaşam Döngüsü](#mesaj-yaşam-döngüsü)
6. [Broker Yönetimi](#broker-yönetimi)
7. [API Kullanımı](#api-kullanımı)
8. [Kurulum ve Çalıştırma](#kurulum-ve-çalıştırma)

## 🛠️ Temel Teknolojiler ve Konseptler

### Spring Boot Framework (v3.5.4)
- **Ne Yapar**: Java tabanlı mikro servis geliştirme framework'ü
- **Kullanıldığı Yer**: Tüm projenin temelini oluşturur
- **Bağımlılıklar**: Java 21, Maven, Spring Web, Spring Data JPA, Spring AMQP, Spring WebSocket

### REST (REpresentational State Transfer)
- **Ne Yapar**: HTTP protokolü üzerinde çalışan web servisleri standardı
- **Nasıl Çalışır**: 
  - İstemci HTTP istekleri gönderir (GET, POST, PUT, DELETE)
  - Sunucu JSON/XML formatında yanıt döner
  - Durumsuz (stateless) iletişim
- **Projede Kullanımı**: `/api/v1/rest/send` endpoint'i ile gelen mesajları başka HTTP servislerine yönlendirir
- **Örnek**:
  ```json
  {
    "headers": {
      "url": "http://example.com/api/users",
      "method": "POST"
    },
    "body": "{\"name\": \"John\", \"email\": \"john@example.com\"}"
  }
  ```

### RabbitMQ (Advanced Message Queuing Protocol)
- **Ne Yapar**: Asenkron mesaj kuyruğu sistemi
- **Temel Kavramlar**:
  - **Producer**: Mesaj gönderen uygulama
  - **Queue**: Mesajların bekletildiği kuyruk
  - **Consumer**: Mesajları tüketen uygulama
  - **Exchange**: Mesajları kuyruklara yönlendiren bileşen
  - **Routing Key**: Exchange'in mesajı hangi kuyruğa göndereceğini belirler
- **Avantajları**:
  - Yüksek performanslı asenkron iletişim
  - Mesaj kaybı olmadan güvenli iletim
  - Otomatik yeniden deneme mekanizması
- **Projede Kullanımı**: Birden fazla RabbitMQ broker'ı yönetir, Docker ile otomatik kurulum yapar
- **Örnek**:
  ```json
  {
    "broker": "rabbitmq-local",
    "queue": "notifications",
    "payload": "{\"userId\": 123, \"message\": \"Hello World\"}",
    "sender": "api-client-1"
  }
  ```

### WebSocket
- **Ne Yarar**: Gerçek zamanlı, çift yönlü iletişim protokolü
- **HTTP'den Farkları**:
  - Sürekli bağlantı (persistent connection)
  - Sunucu da istemciye mesaj gönderebilir
  - Düşük latency, yüksek performans
- **STOMP (Simple Text Oriented Messaging Protocol)**:
  - WebSocket üzerinde çalışan mesajlaşma protokolü
  - Topic tabanlı pub/sub modeli
  - Kullanıcı bazlı özel mesajlar (/user/{userId}/messages)
- **Projede Kullanımı**: Anlık bildirimler, canlı güncellemeler için kullanılır
- **Örnek**:
  ```json
  {
    "broker": "websocket-local",
    "destination": "/topic/notifications",
    "payload": "{\"type\": \"info\", \"message\": \"System update\"}",
    "messageType": "topic"
  }
  ```

### PostgreSQL ve JSONB
- **PostgreSQL**: İlişkisel veritabanı yönetim sistemi
- **JSONB**: PostgreSQL'in binary JSON veri tipi
  - Normal JSON'dan daha hızlı sorgulama
  - İndeksleme desteği
  - Projede mesaj header'ları için kullanılır
- **Kullanım Örneği**: 
  ```sql
  SELECT * FROM messages WHERE headers->>'protocol' = 'REST';
  ```

### Docker Integration
- **Docker**: Container teknolojisi
- **Projede Kullanımı**: RabbitMQ broker'ları otomatik olarak Docker container olarak oluşturur
- **Avantajları**:
  - İzole ortam
  - Kolay kurulum ve kaldırma
  - Port yönetimi
  - Kaynak sınırlamaları

## 🏗️ Proje Mimarisi

### Genel Mimari Yaklaşımı
Proje **Multi-Protocol Gateway** tasarım desenini kullanır:
1. **Tek Giriş Noktası**: Farklı protokollerden gelen mesajlar tek bir serviste toplanır
2. **Protocol-Specific DTOs: Her protokol için özelleşmiş mesaj formatları
3. **Broker Yönetimi**: Runtime'da broker eklenip çıkarılabilir
4. **Mesaj Takibi**: Her mesajın yaşam döngüsü takip edilir

### Katmanlı Mimari
```
┌─────────────────────────────────────┐
│          CONTROLLER LAYER           │ ← REST API Endpoints
├─────────────────────────────────────┤
│           SERVICE LAYER             │ ← Business Logic
├─────────────────────────────────────┤
│         REPOSITORY LAYER            │ ← Data Access
├─────────────────────────────────────┤
│           ENTITY LAYER              │ ← Data Models
├─────────────────────────────────────┤
│         DATABASE LAYER              │ ← PostgreSQL
└─────────────────────────────────────┘
```

### Paket Yapısı
```
org.argela.genericcommunicationservice/
├── GenericCommunicationServiceApplication.java    ← Main Class
├── config/                                         ← Configuration Classes
│   ├── CorsConfig.java                            ← CORS ayarları
│   ├── RabbitMQConfig.java                        ← RabbitMQ konfigürasyonu
│   ├── RestTemplateConfig.java                    ← HTTP client ayarları
│   └── WebSocketConfig.java                       ← WebSocket konfigürasyonu
├── controller/                                     ← REST Controllers
│   ├── GeneralMessageController.java              ← Genel mesaj işlemleri
│   ├── RabbitMQController.java                    ← RabbitMQ işlemleri
│   ├── RestMessageController.java                 ← HTTP mesaj işlemleri
│   └── WebSocketController.java                   ← WebSocket işlemleri
├── dto/                                            ← Data Transfer Objects
│   ├── RabbitMQBrokerConfigDto.java              ← RabbitMQ broker config
│   ├── RabbitSendDto.java                         ← RabbitMQ mesaj formatı
│   ├── RestSendDto.java                           ← HTTP mesaj formatı
│   └── WebSocketSendDto.java                      ← WebSocket mesaj formatı
├── entity/                                         ← JPA Entities
│   ├── MessageEntity.java                         ← Mesaj tablosu
│   ├── RabbitMQBrokerEntity.java                 ← RabbitMQ broker tablosu
│   └── WebSocketEntity.java                      ← WebSocket entity tablosu
├── enums/                                          ← Enum Classes
│   ├── MessageStatus.java                         ← Mesaj durumları
│   └── ProtocolType.java                          ← Protokol tipleri
├── exception/                                      ← Exception Handling
│   └── GlobalExceptionHandler.java               ← Global hata yönetimi
├── health/                                         ← Health Checks
│   ├── DatabaseHealthIndicator.java              ← DB sağlık kontrolü
│   └── RabbitMQHealthIndicator.java              ← RabbitMQ sağlık kontrolü
├── repository/                                     ← Data Access Layer
│   ├── MessageRepository.java                     ← Mesaj veritabanı işlemleri
│   ├── RabbitMQBrokerRepository.java             ← RabbitMQ broker CRUD
│   └── WebSocketRepository.java                  ← WebSocket repository CRUD
└── service/                                        ← Business Logic
    ├── MessageService.java                         ← Mesaj servisi interface
    ├── RabbitMQBrokerService.java                 ← RabbitMQ broker yönetimi
    ├── docker/
    │   └── DockerRabbitManager.java               ← Docker container yönetimi
    ├── http/
    │   └── HttpRelaySender.java                   ← HTTP mesaj gönderimi
    ├── impl/
    │   └── MessageServiceImpl.java                ← Mesaj servisi implementation
    ├── rabbit/
    │   ├── RabbitMessageListener.java             ← RabbitMQ mesaj dinleyici
    │   └── RabbitPublisher.java                   ← RabbitMQ mesaj gönderici
    └── websocket/
        ├── WebSocketService.java                  ← WebSocket service yönetimi
        └── WebSocketSender.java                   ← WebSocket mesaj gönderici
```

## 🔄 İletişim Protokolleri

### 1. REST/HTTP Protokolü

#### Nasıl Çalışır
1. İstemci `/api/v1/rest/send` endpoint'ine POST request gönderir
2. Sistem mesajı `RECEIVED` statusü ile veritabanına kaydeder
3. Header'lardan hedef URL'i extract eder
4. `HttpRelaySender` ile hedef servise HTTP isteği gönderir
5. Başarılıysa `DELIVERED`, hatalıysa `FAILED` statusüne günceller

#### Mesaj Formatı (RestSendDto.java:1-25)
```java
public class RestSendDto {
    @NotNull(message = "Headers boş olamaz")
    private Map<String, String> headers;  // URL ve HTTP method bilgileri
    
    private String body;                  // HTTP request body
    private String sender;                // Gönderici bilgisi
    private String groupId;               // Mesaj grup ID'si
}
```

#### HTTP İletim Süreci (HttpRelaySender.java:45-120)
```java
public HttpRelayResult send(RestSendDto dto) {
    // 1. URL'i extract et
    String targetUrl = extractUrl(dto.getHeaders());
    
    // 2. HTTP method'u belirle
    String method = dto.getHeaders().getOrDefault("method", "POST");
    
    // 3. HTTP isteği oluştur ve gönder
    ResponseEntity<String> response = restTemplate.exchange(
        targetUrl, HttpMethod.valueOf(method), httpEntity, String.class
    );
    
    // 4. Sonucu döndür
    return HttpRelayResult.success(response.getBody());
}
```

### 2. RabbitMQ Protokolü

#### Temel Kavramlar Detaylı Açıklaması

**Queue (Kuyruk)**
- Mesajların saklandığı veri yapısı
- FIFO (First In, First Out) mantığıyla çalışır
- Projede kullanılan kuyruklar: `generic-messages-queue`, `notifications`, `user-notifications`

**Exchange (Değişim)**
- Mesajları kuyruklara yönlendiren bileşen
- Türleri:
  - **Direct**: Routing key ile tam eşleşme
  - **Topic**: Pattern-based routing (örn: `notify.*`)
  - **Fanout**: Tüm bağlı kuyruklara gönderir
  - **Headers**: Header bilgileri ile routing

**Routing Key**
- Exchange'in mesajı hangi kuyruğa göndereceğini belirler
- Örnek: `user.notification.email` → email bildirimlerini ilgili kuyruğa yönlendirir

**Virtual Host**
- RabbitMQ içinde mantıksal ayrım
- Farklı uygulamalar için farklı sanal ortamlar
- Default: `/`

#### Broker Yönetimi (RabbitMQBrokerService.java:26-441)

**Dinamik Broker Oluşturma**
```java
public BrokerCreationResult createBroker(RabbitMQBrokerConfigDto config) {
    // 1. Docker container oluştur (eğer autoCreate=true)
    if (config.isAutoCreate()) {
        DockerContainerResult dockerResult = dockerManager.createRabbitContainer(config);
    }
    
    // 2. Broker entity'sini database'e kaydet
    RabbitMQBrokerEntity broker = new RabbitMQBrokerEntity();
    broker.setBrokerKey(config.getBrokerKey());
    broker.setHost(host);
    
    // 3. Connection test et
    boolean connectionOk = testBrokerConnection(savedBroker);
}
```

**Runtime RabbitTemplate Oluşturma**
```java
public RabbitTemplate createRabbitTemplate(RabbitMQBrokerEntity broker) {
    CachingConnectionFactory factory = createRabbitConnectionFactory(broker);
    RabbitTemplate template = new RabbitTemplate(factory);
    template.setMessageConverter(messageConverter);
    return template;
}
```

#### Mesaj Gönderim Süreci (RabbitPublisher.java)
1. Broker key ile veritabanından broker bilgisini al
2. Runtime'da RabbitTemplate oluştur
3. Queue'ya direkt gönderim veya Exchange üzerinden routing
4. Broker sağlık durumunu güncelle
5. Sonucu döndür

### 3. WebSocket Protokolü

#### STOMP (Simple Text Oriented Messaging Protocol)
- WebSocket üzerinde çalışan text-based protokol
- Pub/Sub (Publisher/Subscriber) modeli
- Frame-based message format

#### Mesaj Türleri
1. **Topic Messages**: `/topic/notifications` - Tüm subscribers'a
2. **User-Specific**: `/user/{userId}/messages` - Belirli kullanıcıya
3. **Broadcast**: Tüm bağlı kullanıcılara

#### WebSocket Endpoint Konfigürasyonu (WebSocketConfig.java:15-40)
```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")          // STOMP over WebSocket
            .setAllowedOrigins("*")
            .withSockJS();               // Fallback desteği
    
    registry.addEndpoint("/websocket")   // Raw WebSocket
            .setAllowedOrigins("*");
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/user");  // In-memory broker
    registry.setApplicationDestinationPrefixes("/app"); // Client messages
}
```

#### WebSocket Mesaj Gönderimi (WebSocketSender.java)
```java
public WebSocketSendResult send(WebSocketSendDto dto) {
    switch (dto.getMessageType()) {
        case "topic":
            // Topic'e broadcast
            messagingTemplate.convertAndSend(dto.getDestination(), dto.getPayload());
            
        case "user":
            // Belirli kullanıcıya
            messagingTemplate.convertAndSendToUser(userId, destination, dto.getPayload());
            
        case "broadcast":
            // Tüm bağlı kullanıcılara
            messagingTemplate.convertAndSend("/topic/broadcast", dto.getPayload());
    }
}
```

## 📊 Sınıf ve Metod Analizi

### Entity Classes (Veritabanı Modelleri)

#### MessageEntity.java (1-46)
**Amaç**: Tüm protokollerin mesajlarını tek tabloda tutan universal mesaj modeli

**Önemli Alanlar**:
```java
@Entity
@Table(name = "messages")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // Unique mesaj ID'si
    
    private String method;              // HTTP method / PUBLISH / SEND
    private String url;                 // Target URL veya resource path
    private String version;             // Protokol versiyonu
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headers; // Protocol-specific headers (JSONB)
    
    @Column(columnDefinition = "text")
    private String body;                 // Mesaj içeriği
    
    private String sender;               // Gönderici bilgisi
    private String groupId;              // Mesaj grubu
    
    @Enumerated(EnumType.STRING)
    private ProtocolType protocol;       // REST, RABBITMQ, WEBSOCKET
    
    @Enumerated(EnumType.STRING)
    private MessageStatus status;        // RECEIVED, QUEUED, DELIVERED, FAILED
    
    private Instant timestamp = Instant.now(); // Mesaj zamanı
}
```

**Tasarım Kararı**: 
- Tek tablo tüm protokoller (Table per Class Hierarchy)
- JSONB kullanarak esnek header depolama
- Protocol-agnostic canonical format

#### RabbitMQBrokerEntity.java (14-86)
**Amaç**: RabbitMQ broker konfigürasyonlarını veritabanında saklar

**Key Features**:
```java
@Column(name = "broker_key", unique = true)
private String brokerKey;               // Unique identifier

// Connection bilgileri
private String host;
private Integer port = 5672;
private String username = "guest";
private String password = "guest";
private String virtualHost = "/";

// Broker durumu
private Boolean isPrimary = false;      // Primary broker mi?
private Boolean isActive = true;        // Aktif mi?

// Docker integration
private Boolean isDockerManaged = false; // Docker container mi?
private String dockerContainerId;
private String dockerContainerName;
private Integer managementPort = 15672;

// Health monitoring
private Instant lastHealthCheck;
private HealthStatus healthStatus = HealthStatus.UNKNOWN;

public enum HealthStatus {
    ONLINE, OFFLINE, ERROR, UNKNOWN
}
```

#### WebSocketEntity.java (14-71)
**Amaç**: WebSocket entity bilgilerini yönetir

**Önemli Alanlar**:
```java
private String brokerKey;              // Unique identifier
private String endpointUrl;            // ws://localhost:8080/ws
private String protocolType = "STOMP";  // STOMP, SockJS, Raw
private Integer maxConnections = 1000; // Connection limiti
private Integer heartbeatInterval = 60000; // Heartbeat süresi
```

### Service Layer Analizi

#### MessageService.java ve MessageServiceImpl.java

**Amaç**: Merkezi mesaj yönetimi servisi - tüm protokollerin mesajlarını tek interface'den yönetir

**Core Methods**:

1. **saveRestMessage(RestSendDto, MessageStatus)** (Line 28-51)
```java
public MessageEntity saveRestMessage(RestSendDto dto, MessageStatus status) {
    MessageEntity entity = new MessageEntity();
    
    // HTTP-style canonical mapping
    entity.setMethod(extractMethod(dto.getHeaders()));      // GET, POST, etc.
    entity.setUrl(extractUrl(dto.getHeaders()));            // Target URL
    entity.setVersion("HTTP/1.1");                          // HTTP version
    entity.setHeaders(dto.getHeaders());                    // Original headers
    entity.setBody(dto.getBody());                          // Request body
    
    // Meta information
    entity.setProtocol(ProtocolType.REST);
    entity.setStatus(status);
    entity.setTimestamp(Instant.now());
    
    return messageRepository.save(entity);
}
```

2. **saveRabbitMessage(RabbitSendDto, MessageStatus)** (Line 54-84)
```java
public MessageEntity saveRabbitMessage(RabbitSendDto dto, MessageStatus status) {
    // RabbitMQ'yu HTTP-style canonical format'a dönüştür
    entity.setMethod("PUBLISH");
    entity.setUrl(String.format("rabbitmq://%s/%s", dto.getBroker(), dto.getQueue()));
    entity.setVersion("AMQP/0.9.1");
    
    // RabbitMQ-specific headers
    Map<String, String> headers = new HashMap<>();
    headers.put("broker", dto.getBroker());
    headers.put("queue", dto.getQueue());
    if (dto.getExchange() != null) headers.put("exchange", dto.getExchange());
    
    entity.setHeaders(headers);
    entity.setBody(dto.getPayload());
    entity.setProtocol(ProtocolType.RABBITMQ);
}
```

3. **saveWebSocketMessage(WebSocketSendDto, MessageStatus)** (Line 87-118)
```java
public MessageEntity saveWebSocketMessage(WebSocketSendDto dto, MessageStatus status) {
    // WebSocket'i HTTP-style canonical format'a dönüştür
    entity.setMethod("SEND");
    entity.setUrl(String.format("websocket://%s%s", dto.getBroker(), dto.getDestination()));
    entity.setVersion("STOMP/1.2");
    
    // WebSocket-specific headers
    Map<String, String> headers = new HashMap<>();
    headers.put("broker", dto.getBroker());
    headers.put("destination", dto.getDestination());
    headers.put("message-type", dto.getMessageType());
}
```

**Tasarım Avantajı**: Farklı protokollerin mesajları aynı formatta saklanır - bu sayede:
- Unified querying
- Cross-protocol analytics
- Consistent monitoring

#### RabbitMQBrokerService.java (26-441)
**Amaç**: RabbitMQ broker'larının tüm lifecycle'ını yönetir

**1. Broker Discovery & Management**
```java
public RabbitMQBrokerEntity findActiveBrokerByKey(String brokerKey) {
    return rabbitMQBrokerRepository.findByBrokerKeyAndIsActiveTrue(brokerKey)
        .orElseThrow(() -> new BrokerNotFoundException(...));
}

public List<String> getAvailableBrokerKeys() {
    return listActiveBrokers().stream()
           .map(RabbitMQBrokerEntity::getBrokerKey)
           .toList();
}
```

**2. Runtime Connection Management**
```java
public RabbitTemplate createRabbitTemplate(RabbitMQBrokerEntity broker) {
    CachingConnectionFactory factory = createRabbitConnectionFactory(broker);
    RabbitTemplate template = new RabbitTemplate(factory);
    template.setMessageConverter(messageConverter);
    template.setMandatory(true);
    
    // Return callback for failed deliveries
    template.setReturnsCallback(returned -> {
        log.warn("⚠️ RabbitMQ mesaj geri döndü: broker={}, queue={}", 
                broker.getBrokerKey(), returned.getRoutingKey());
    });
    
    return template;
}
```

**3. Docker Integration & Auto-Provisioning**
```java
public BrokerCreationResult createBroker(RabbitMQBrokerConfigDto config) {
    // Docker container oluştur (eğer autoCreate=true)
    if (config.isAutoCreate()) {
        DockerContainerResult dockerResult = dockerManager.createRabbitContainer(config);
        
        if (!dockerResult.isSuccess()) {
            return BrokerCreationResult.failure("Docker container oluşturulamadı");
        }
        
        // Docker sonuçlarını al
        host = dockerResult.getHost();
        port = dockerResult.getPort();
        dockerContainerId = dockerResult.getContainerId();
    }
    
    // Broker entity oluştur ve kaydet
    RabbitMQBrokerEntity broker = new RabbitMQBrokerEntity();
    broker.setBrokerKey(config.getBrokerKey());
    broker.setHost(host);
    broker.setIsDockerManaged(dockerCreated);
    
    // Connection test
    boolean connectionOk = testBrokerConnection(savedBroker);
    updateBrokerHealth(config.getBrokerKey(), 
        connectionOk ? HealthStatus.ONLINE : HealthStatus.OFFLINE);
}
```

**4. Health Monitoring**
```java
public boolean testBrokerConnection(RabbitMQBrokerEntity broker) {
    try {
        CachingConnectionFactory factory = createRabbitConnectionFactory(broker);
        factory.createConnection().close();
        return true;
    } catch (Exception e) {
        log.error("RabbitMQ broker bağlantı testi başarısız: {}", e.getMessage());
        return false;
    }
}
```

#### DockerRabbitManager.java
**Amaç**: Docker API üzerinden RabbitMQ container'larını otomatik yönetir

**Key Features**:
1. **Container Creation**
2. **Port Management** 
3. **Resource Limits**
4. **Health Monitoring**
5. **Windows Docker Desktop Integration**

```java
public DockerContainerResult createRabbitContainer(RabbitMQBrokerConfigDto config) {
    // Container image ve configurasyon
    CreateContainerResponse container = dockerClient
        .createContainerCmd("rabbitmq:3.13-management")
        .withName(containerName)
        .withExposedPorts(ExposedPort.tcp(5672), ExposedPort.tcp(15672))
        .withPortBindings(
            new PortBinding(Ports.Binding.bindPort(config.getPort()), ExposedPort.tcp(5672)),
            new PortBinding(Ports.Binding.bindPort(managementPort), ExposedPort.tcp(15672))
        )
        .withEnv(
            "RABBITMQ_DEFAULT_USER=" + username,
            "RABBITMQ_DEFAULT_PASS=" + password
        )
        .exec();
        
    // Container'ı başlat
    dockerClient.startContainerCmd(container.getId()).exec();
}
```

### Controller Layer Analizi

#### GeneralMessageController.java (18-61)
**Amaç**: Protocol-agnostic mesaj listeleme ve genel işlemler

**Key Endpoints**:

1. **GET /api/v1/messages** - Mesaj listeleme
```java
public ResponseEntity<Page<MessageEntity>> listMessages(
    @RequestParam(required = false) String protocol,
    @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable
) {
    if (protocol != null) {
        return ResponseEntity.ok(
            messageService.getMessagesByProtocol(protocol.toUpperCase(), pageable)
        );
    }
    return ResponseEntity.ok(messageService.getAllMessages(pageable));
}
```

2. **GET /api/v1/messages/statistics** - Mesaj istatistikleri
```java
public ResponseEntity<Map<String, Object>> getStatistics() {
    return ResponseEntity.ok(messageService.getMessageStatistics());
}
```

#### RabbitMQController.java (29-295)
**Amaç**: RabbitMQ mesajları ve broker yönetimi

**1. Message Publishing Endpoint**
```java
@PostMapping("/publish")
public ResponseEntity<MessageEntity> publish(@Valid @RequestBody RabbitSendDto dto) {
    // Default broker
    String brokerKey = dto.getBroker() != null ? dto.getBroker() : "rabbitmq-local";
    
    // 1. İlk kayıt - QUEUED status
    MessageEntity savedMessage = messageService.saveRabbitMessage(dto, MessageStatus.QUEUED);
    
    // 2. Database'den broker'a gönder
    RabbitPublisher.RabbitSendResult result = rabbitPublisher.publish(dto);
    
    // 3. Başarısızsa durumu güncelle
    if (!result.isSuccess()) {
        MessageEntity updatedMessage = messageService.updateMessageStatus(
            savedMessage.getId(), MessageStatus.FAILED);
        
        // Error details'i body'e ekle
        String errorDetails = String.format("%s\n\n❌ PUBLISH ERROR: %s",
            updatedMessage.getBody(), result.getErrorMessage());
        updatedMessage.setBody(errorDetails);
        messageService.updateMessage(updatedMessage);
    }
    
    return ResponseEntity.ok(updatedMessage);
}
```

**2. Broker Management Endpoints**
```java
@PostMapping("/brokers")  // Yeni broker oluştur
@DeleteMapping("/brokers/{brokerKey}")  // Broker sil
@GetMapping("/brokers")   // Broker listele
@GetMapping("/brokers/{brokerKey}/status")  // Broker durumu
```

### Repository Layer

#### MessageRepository.java
**Amaç**: Message entity için data access operations

**Custom Queries**:
```java
Page<MessageEntity> findByProtocol(ProtocolType protocol, Pageable pageable);

@Query("SELECT p.protocol, COUNT(m) FROM MessageEntity m GROUP BY m.protocol")
List<Object[]> countMessagesByProtocol();

@Query("SELECT m.status, COUNT(m) FROM MessageEntity m GROUP BY m.status") 
List<Object[]> countMessagesByStatus();
```

#### RabbitMQBrokerRepository.java
**Amaç**: RabbitMQ broker configuration management

**Key Queries**:
```java
// Active broker bulma
Optional<RabbitMQBrokerEntity> findByBrokerKeyAndIsActiveTrue(String brokerKey);

// Primary broker
Optional<RabbitMQBrokerEntity> findByIsPrimaryTrueAndIsActiveTrue();

// Docker-managed brokers
List<RabbitMQBrokerEntity> findByIsDockerManagedTrueAndIsActiveTrue();

// Statistics
@Query("SELECT COUNT(b) FROM RabbitMQBrokerEntity b WHERE b.isActive = true")
Long countActiveBrokers();
```

### Configuration Classes

#### RabbitMQConfig.java (15-65)
**Amaç**: RabbitMQ infrastructure kurulumu

**Key Beans**:
```java
@Bean
@Primary
@ConfigurationProperties(prefix = "spring.rabbitmq")
public CachingConnectionFactory connectionFactory() {
    return new CachingConnectionFactory();
}

@Bean
public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
    return new Jackson2JsonMessageConverter();
}

// Pre-defined queues
@Bean
public Queue genericMessagesQueue() {
    return QueueBuilder.durable("generic-messages-queue").build();
}
```

#### WebSocketConfig.java (15-40)
**Amaç**: WebSocket/STOMP konfigürasyonu

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    // SockJS ile fallback desteği
    registry.addEndpoint("/ws")
            .setAllowedOrigins(corsOrigins.toArray(new String[0]))
            .withSockJS()
            .setSessionCookieNeeded(false);
            
    // Raw WebSocket endpoint
    registry.addEndpoint("/websocket")
            .setAllowedOrigins(corsOrigins.toArray(new String[0]));
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/user");  // In-memory simple broker
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
}
```

### Exception Handling

#### GlobalExceptionHandler.java
**Amaç**: Merkezi exception handling

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidationException(
    MethodArgumentNotValidException ex) {
    
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
        errors.put(error.getField(), error.getDefaultMessage())
    );
    
    Map<String, Object> response = createErrorResponse(
        HttpStatus.BAD_REQUEST, "Validation failed", errors);
    return ResponseEntity.badRequest().body(response);
}

@ExceptionHandler(RabbitMQBrokerService.BrokerNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleBrokerNotFound(
    RabbitMQBrokerService.BrokerNotFoundException ex) {
    
    Map<String, Object> response = createErrorResponse(
        HttpStatus.NOT_FOUND, "RabbitMQ broker not found", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
}
```

### Health Monitoring

#### DatabaseHealthIndicator.java
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "Connection successful")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "PostgreSQL") 
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

#### RabbitMQHealthIndicator.java
```java
@Override
public Health health() {
    try {
        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        channel.close();
        connection.close();
        
        return Health.up()
                .withDetail("rabbitmq", "Default broker")
                .withDetail("host", connectionFactory.getHost())
                .withDetail("port", connectionFactory.getPort())
                .build();
    } catch (Exception e) {
        return Health.down()
                .withDetail("rabbitmq", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
    }
}
```

## 🔄 Mesaj Yaşam Döngüsü

### MessageStatus Enum States
```java
public enum MessageStatus {
    RECEIVED,    // Mesaj alındı fakat henüz işlenmedi
    QUEUED,      // Mesaj kuyrukta bekliyor  
    DELIVERED,   // Mesaj başarıyla teslim edildi
    FAILED,      // Mesaj iletiminde hata oluştu
    RETRYING     // Mesaj yeniden gönderilmeye çalışılıyor
}
```

### Status Transition Flow

#### REST Messages
```
[Client Request] → RECEIVED → [HTTP Call] → DELIVERED ✅
                                        → FAILED ❌
```

#### RabbitMQ Messages  
```
[Client Request] → QUEUED → [Broker Publish] → DELIVERED ✅
                                            → FAILED ❌
                                            → RETRYING → DELIVERED ✅
                                                      → FAILED ❌
```

#### WebSocket Messages
```
[Client Request] → QUEUED → [STOMP Send] → DELIVERED ✅
                                        → FAILED ❌
```

### Detailed Message Processing

#### 1. REST Message Processing (RestMessageController.java)
```java
@PostMapping("/send")
public ResponseEntity<MessageEntity> send(@Valid @RequestBody RestSendDto dto) {
    // 1️⃣ İlk kayıt - RECEIVED status
    MessageEntity savedMessage = messageService.saveRestMessage(dto, MessageStatus.RECEIVED);
    
    // 2️⃣ HTTP call
    HttpRelaySender.HttpRelayResult result = httpRelaySender.send(dto);
    
    // 3️⃣ Status update
    MessageStatus finalStatus = result.isSuccess() ? MessageStatus.DELIVERED : MessageStatus.FAILED;
    MessageEntity updatedMessage = messageService.updateMessageStatus(savedMessage.getId(), finalStatus);
    
    // 4️⃣ Error details ekleme (başarısızsa)
    if (!result.isSuccess()) {
        String errorDetails = String.format("%s\n\n❌ HTTP RELAY ERROR: %s", 
                                           updatedMessage.getBody(), result.getErrorMessage());
        updatedMessage.setBody(errorDetails);
        messageService.updateMessage(updatedMessage);
    }
    
    return ResponseEntity.ok(updatedMessage);
}
```

#### 2. RabbitMQ Message Processing (RabbitMQController.java)
```java
@PostMapping("/publish")
public ResponseEntity<MessageEntity> publish(@Valid @RequestBody RabbitSendDto dto) {
    // 1️⃣ İlk kayıt - QUEUED status (RabbitMQ için)
    MessageEntity savedMessage = messageService.saveRabbitMessage(dto, MessageStatus.QUEUED);
    
    // 2️⃣ Database'den broker'a publish
    RabbitPublisher.RabbitSendResult result = rabbitPublisher.publish(dto);
    
    // 3️⃣ Status update (success = otomatik DELIVERED, failure = FAILED)
    if (!result.isSuccess()) {
        MessageEntity updatedMessage = messageService.updateMessageStatus(savedMessage.getId(), MessageStatus.FAILED);
        
        // Error details
        String errorDetails = String.format("%s\n\n❌ PUBLISH ERROR: %s",
                                           originalBody, result.getErrorMessage());
        updatedMessage.setBody(errorDetails);
        messageService.updateMessage(updatedMessage);
    }
    
    return ResponseEntity.ok(savedMessage);
}
```

#### 3. RabbitMQ Consumer Side (RabbitMessageListener.java)
```java
@RabbitListener(queues = "generic-messages-queue")
public void handleGenericMessage(@Payload String payload, 
                                @Header Map<String, Object> headers) {
    try {
        // Consume edilen mesajı otomatik DELIVERED olarak kaydet
        RabbitSendDto dto = new RabbitSendDto();
        dto.setBroker("consumer");
        dto.setQueue("generic-messages-queue");  
        dto.setPayload(payload);
        
        messageService.saveRabbitMessage(dto, MessageStatus.DELIVERED);
        
        log.info("✅ RabbitMQ mesaj consume edildi: {}", payload.substring(0, Math.min(100, payload.length())));
        
    } catch (Exception e) {
        // Hata durumunda FAILED olarak kaydet
        messageService.saveRabbitMessage(dto, MessageStatus.FAILED);
        log.error("❌ RabbitMQ mesaj consume hatası: {}", e.getMessage());
    }
}
```

## 🔧 Broker Yönetimi

### RabbitMQ Broker Management

#### 1. Manual Broker Addition
```json
POST /api/v1/rabbitmq/brokers
{
  "brokerKey": "external-rabbit",
  "host": "192.168.1.100", 
  "port": 5672,
  "username": "guest",
  "password": "guest",
  "autoCreate": false
}
```

#### 2. Docker Auto-Provisioning  
```json
POST /api/v1/rabbitmq/brokers
{
  "brokerKey": "docker-rabbit-1",
  "autoCreate": true,
  "port": 5673,
  "managementPort": 15673,
  "username": "admin", 
  "password": "secret123",
  "memoryLimitMb": 512
}
```

**Behind the Scenes:**
1. DockerRabbitManager creates new container
2. Database'e broker configuration kaydedilir
3. Connection test yapılır
4. Health status güncellenir
5. Management UI link döndürülür

#### 3. Runtime Broker Selection
```java
// RabbitPublisher.java
public RabbitSendResult publish(RabbitSendDto dto) {
    // 1. Database'den broker bul
    RabbitMQBrokerEntity broker = rabbitMQBrokerService.findActiveBrokerByKey(dto.getBroker());
    
    // 2. Runtime RabbitTemplate oluştur  
    RabbitTemplate template = rabbitMQBrokerService.createRabbitTemplate(broker);
    
    // 3. Mesaj gönder
    if (dto.getExchange() != null) {
        template.convertAndSend(dto.getExchange(), dto.getRoutingKey(), dto.getPayload());
    } else {
        template.convertAndSend(dto.getQueue(), dto.getPayload());
    }
}
```

### WebSocket Broker Management

#### WebSocketService.java
```java
public WebSocketEntity findActiveBrokerByKey(String brokerKey) {
    return webSocketRepository.findByBrokerKeyAndIsActiveTrue(brokerKey)
        .orElseThrow(() -> new BrokerNotFoundException("WebSocket broker not found: " + brokerKey));
}

public boolean isBrokerAvailable(String brokerKey) {
    try {
        WebSocketEntity broker = findActiveBrokerByKey(brokerKey);
        // WebSocket broker availability check
        boolean available = checkWebSocketEndpoint(broker.getEndpointUrl());
        
        // Health status güncelle
        WebSocketEntity.HealthStatus status = available ? 
            WebSocketEntity.HealthStatus.ONLINE : 
            WebSocketEntity.HealthStatus.OFFLINE;
            
        updateBrokerHealth(brokerKey, status);
        return available;
    } catch (Exception e) {
        return false;
    }
}
```

## 📋 API Kullanımı ve Örnekler

### 1. REST Message Relay

**Endpoint**: `POST /api/v1/rest/send`

**Amaç**: HTTP mesajı başka bir HTTP servisine yönlendirir

**Request Body**:
```json
{
  "headers": {
    "url": "https://jsonplaceholder.typicode.com/posts",
    "method": "POST",
    "Content-Type": "application/json",
    "sender": "api-client-1"
  },
  "body": "{\"title\": \"Test Post\", \"body\": \"This is a test post\", \"userId\": 1}"
}
```

**Response**:
```json
{
  "id": 1,
  "method": "POST",
  "url": "https://jsonplaceholder.typicode.com/posts",
  "version": "HTTP/1.1",
  "headers": {
    "url": "https://jsonplaceholder.typicode.com/posts",
    "method": "POST",
    "Content-Type": "application/json",
    "sender": "api-client-1"
  },
  "body": "{\"title\": \"Test Post\", \"body\": \"This is a test post\", \"userId\": 1}\n\n✅ HTTP RELAY SUCCESS: {\"id\": 101, \"title\": \"Test Post\", \"body\": \"This is a test post\", \"userId\": 1}",
  "sender": "api-client-1",
  "protocol": "REST",
  "status": "DELIVERED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 2. RabbitMQ Message Publishing

**Endpoint**: `POST /api/v1/rabbitmq/publish`

**Scenario 1: Direct Queue Publishing**
```json
{
  "broker": "rabbitmq-local",
  "queue": "notifications",
  "payload": "{\"userId\": 123, \"message\": \"Welcome to our system!\", \"type\": \"welcome\"}",
  "sender": "user-service",
  "groupId": "user-onboarding"
}
```

**Scenario 2: Exchange Routing**
```json
{
  "broker": "docker-rabbit-1", 
  "exchange": "notification-exchange",
  "routingKey": "user.welcome",
  "queue": "user-notifications",
  "payload": "{\"level\": \"info\", \"message\": \"Account created successfully\", \"userId\": 456}",
  "sender": "auth-service"
}
```

**Response**:
```json
{
  "id": 2,
  "method": "PUBLISH",
  "url": "rabbitmq://rabbitmq-local/notifications", 
  "version": "AMQP/0.9.1",
  "headers": {
    "broker": "rabbitmq-local",
    "queue": "notifications"
  },
  "body": "{\"userId\": 123, \"message\": \"Welcome to our system!\", \"type\": \"welcome\"}",
  "sender": "user-service",
  "groupId": "user-onboarding",
  "protocol": "RABBITMQ",
  "status": "QUEUED",
  "timestamp": "2024-01-15T10:35:00Z"
}
```

### 3. WebSocket Message Publishing

**Endpoint**: `POST /api/v1/websocket/publish`

**Scenario 1: Topic Broadcasting**
```json
{
  "broker": "websocket-local",
  "destination": "/topic/system-announcements",
  "messageType": "topic",
  "payload": "{\"type\": \"maintenance\", \"message\": \"System will be down for maintenance at 2 AM\", \"scheduledTime\": \"2024-01-16T02:00:00Z\"}",
  "sender": "admin-panel"
}
```

**Scenario 2: User-Specific Message**
```json
{
  "broker": "websocket-local", 
  "destination": "/user/123/notifications",
  "messageType": "user",
  "headers": {
    "targetUserId": "123",
    "priority": "high"
  },
  "payload": "{\"type\": \"private_message\", \"from\": \"admin\", \"message\": \"Your account has been verified\"}",
  "sender": "verification-service"
}
```

**Scenario 3: Broadcast to All Connected Users**
```json
{
  "broker": "websocket-local",
  "destination": "/topic/broadcast", 
  "messageType": "broadcast",
  "payload": "{\"type\": \"news\", \"title\": \"New Feature Released\", \"description\": \"Chat functionality is now available\"}",
  "sender": "product-team"
}
```

### 4. Broker Management Operations

#### RabbitMQ Broker Operations

**Create New Docker-Managed Broker**:
```bash
POST /api/v1/rabbitmq/brokers
{
  "brokerKey": "microservice-rabbit",
  "autoCreate": true,
  "port": 5674,
  "managementPort": 15674, 
  "username": "microservice_user",
  "password": "secure_password_123",
  "memoryLimitMb": 1024
}
```

**List All Brokers**:
```bash
GET /api/v1/rabbitmq/brokers
```

**Response**:
```json
{
  "brokers": {
    "rabbitmq-local": {
      "brokerKey": "rabbitmq-local",
      "connectionInfo": "localhost:5672//",
      "available": true,
      "isPrimary": true,
      "dockerManaged": false,
      "dockerStatus": "NOT_FOUND",
      "healthStatus": "ONLINE",
      "lastHealthCheck": "2024-01-15T10:45:00Z"
    },
    "docker-rabbit-1": {
      "brokerKey": "docker-rabbit-1", 
      "connectionInfo": "localhost:5673//",
      "available": true,
      "isPrimary": false,
      "dockerManaged": true,
      "dockerStatus": "RUNNING", 
      "healthStatus": "ONLINE",
      "lastHealthCheck": "2024-01-15T10:44:30Z"
    }
  },
  "statistics": {
    "totalBrokers": 2,
    "dockerManagedBrokers": 1,
    "manualBrokers": 1
  }
}
```

**Check Broker Status**:
```bash
GET /api/v1/rabbitmq/brokers/docker-rabbit-1/status
```

**Remove Broker**:
```bash
DELETE /api/v1/rabbitmq/brokers/docker-rabbit-1
```

### 5. Message Listing and Statistics

**List All Messages with Protocol Filter**:
```bash
GET /api/v1/messages?protocol=RABBITMQ&page=0&size=10&sort=timestamp,desc
```

**Response**:
```json
{
  "content": [
    {
      "id": 15,
      "method": "PUBLISH",
      "url": "rabbitmq://docker-rabbit-1/user-notifications",
      "version": "AMQP/0.9.1", 
      "headers": {
        "broker": "docker-rabbit-1",
        "queue": "user-notifications",
        "exchange": "notification-exchange",
        "routing-key": "user.welcome"
      },
      "body": "{\"level\": \"info\", \"message\": \"Account created\", \"userId\": 456}",
      "sender": "auth-service",
      "protocol": "RABBITMQ",
      "status": "DELIVERED",
      "timestamp": "2024-01-15T11:00:00Z"
    }
  ],
  "pageable": {
    "page": 0,
    "size": 10
  },
  "totalElements": 25,
  "totalPages": 3
}
```

**Get Message Statistics**:
```bash
GET /api/v1/messages/statistics
```

**Response**:
```json
{
  "totalMessages": 150,
  "byProtocol": {
    "REST": 45,
    "RABBITMQ": 78, 
    "WEBSOCKET": 27
  },
  "byStatus": {
    "DELIVERED": 128,
    "FAILED": 12,
    "QUEUED": 8,
    "RECEIVED": 2
  }
}
```

### 6. Health Check Endpoints

**Application Health** (Spring Boot Actuator):
```bash
GET /actuator/health
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "database": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "status": "Connection successful"
      }
    },
    "rabbitmq": {
      "status": "UP", 
      "details": {
        "rabbitmq": "Default broker",
        "host": "localhost",
        "port": 5672
      }
    }
  }
}
```

**Prometheus Metrics**:
```bash
GET /actuator/prometheus
```

**Application Info**:
```bash
GET /actuator/info
```

## 🚀 Kurulum ve Çalıştırma

### Gereksinimler
- **Java 21** veya üzeri
- **Maven 3.8+**
- **PostgreSQL 14+**
- **Docker** (RabbitMQ auto-provisioning için)
- **RabbitMQ** (manuel kurulum için, opsiyonel)

### 1. Database Kurulumu

**PostgreSQL Installation** (Windows):
```bash
# Docker ile PostgreSQL
docker run --name postgres-gcs \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=1234 \
  -e POSTGRES_DB=argela \
  -p 5432:5432 \
  -d postgres:14
```

**Database Schema Oluşturma**:
```sql
-- messages tablosu otomatik oluşturulur (Hibernate ddl-auto: update)

-- Opsiyonel: Tabloları manuel oluşturma
\i src/main/resources/messages.sql
\i src/main/resources/brocker.sql  
\i src/main/resources/websocket.sql
```

### 2. RabbitMQ Kurulumu (Opsiyonel)

**Docker ile RabbitMQ**:
```bash
docker run --name rabbitmq-local \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  -p 5672:5672 \
  -p 15672:15672 \
  -d rabbitmq:3.13-management
```

**Not**: Uygulama kendi RabbitMQ broker'larını otomatik olarak Docker ile oluşturabilir.

### 3. Application Configuration

**application.yml** düzenlemesi:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/argela
    username: postgres 
    password: 1234
    
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    
server:
  port: 8080

# CORS configuration
app:
  cors:
    allowed-origins: "*"  # Production'da specific origins kullanın
```

### 4. Uygulama Çalıştırma

**Maven ile**:
```bash
# Proje dizinine git
cd generic-communication-service

# Dependencies yükle
mvn clean install

# Uygulamayı başlat  
mvn spring-boot:run
```

**IDE ile**:
1. `GenericCommunicationServiceApplication.java` sınıfını run edin
2. Port 8080'de başlayacaktır

**JAR File ile**:
```bash
# JAR oluştur
mvn clean package

# JAR çalıştır
java -jar target/generic-communication-service-0.0.1-SNAPSHOT.jar
```

### 5. Environment Variables (Opsiyonel)

```bash
# Database
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/argela
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=1234

# RabbitMQ  
export SPRING_RABBITMQ_HOST=localhost
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest

# Application
export SERVER_PORT=8080
export CORS_ORIGINS=http://localhost:3000,http://localhost:8080

# Logging
export LOG_SQL=DEBUG
export LOG_RABBITMQ=INFO
export LOG_APP=DEBUG
```

### 6. Verification

**Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

**API Documentation** (Swagger UI):
```bash
http://localhost:8080/swagger-ui.html
```

**WebSocket Test** (Browser Console):
```javascript
// SockJS connection
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // Subscribe to topic
    stompClient.subscribe('/topic/notifications', function(message) {
        console.log('Received: ' + message.body);
    });
});
```

**Test Message Sending**:
```bash
# REST message
curl -X POST http://localhost:8080/api/v1/rest/send \
  -H "Content-Type: application/json" \
  -d '{
    "headers": {
      "url": "https://jsonplaceholder.typicode.com/posts",
      "method": "POST"
    },
    "body": "{\"title\": \"test\", \"body\": \"test body\", \"userId\": 1}"
  }'

# RabbitMQ message  
curl -X POST http://localhost:8080/api/v1/rabbitmq/publish \
  -H "Content-Type: application/json" \
  -d '{
    "broker": "rabbitmq-local",
    "queue": "notifications", 
    "payload": "{\"message\": \"Test message\"}",
    "sender": "test-client"
  }'

# WebSocket message
curl -X POST http://localhost:8080/api/v1/websocket/publish \
  -H "Content-Type: application/json" \
  -d '{
    "broker": "websocket-local",
    "destination": "/topic/test",
    "messageType": "topic",
    "payload": "{\"message\": \"Test WebSocket message\"}",
    "sender": "test-client"
  }'
```

### 7. Production Considerations

**Database Connection Pooling**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      leak-detection-threshold: 60000
```

**RabbitMQ Connection Settings**:
```yaml
spring:
  rabbitmq:
    connection-timeout: 30000
    template:
      retry:
        enabled: true
        max-attempts: 3
        initial-interval: 1000
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
```

**Security Configuration**:
```yaml
# CORS - Production'da specific origins
app:
  cors:
    allowed-origins: https://yourdomain.com,https://app.yourdomain.com

# Management endpoints security
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

**Logging Configuration**:
```yaml
logging:
  level:
    org.argela.genericcommunicationservice: INFO
    org.springframework.amqp: WARN
    org.hibernate.SQL: WARN
  file:
    name: logs/application.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 📚 Ek Kaynaklar

### Teknoloji Dökümantasyonları
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html) 
- [WebSocket & STOMP Guide](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [PostgreSQL JSONB](https://www.postgresql.org/docs/current/datatype-json.html)
- [Docker Java API](https://github.com/docker-java/docker-java)

### API Testing Tools
- **Postman Collection**: İmport edilebilir API koleksiyonu oluşturabilirsiniz
- **curl Scripts**: Yukarıdaki örnekleri kullanarak test scriptleri
- **WebSocket Test Clients**: 
  - Browser Developer Tools
  - WebSocket King (Chrome Extension)
  - wscat (npm package)

### Monitoring ve Observability
- **Prometheus Metrics**: `/actuator/prometheus` endpoint'i
- **Health Checks**: `/actuator/health` 
- **Application Logs**: `logs/application.log` dosyası
- **Database Monitoring**: PostgreSQL pg_stat_activity tablosu
- **RabbitMQ Management UI**: `http://localhost:15672` (Docker broker için)

---

## 🎯 Sonuç

Generic Communication Service, modern mikroservis mimarilerinde farklı protokoller arası iletişimi kolaylaştıran, ölçeklenebilir ve yönetilebilir bir çözümdür. Comprehensive message tracking, dynamic broker management ve Docker entegrasyonu ile production-ready bir platform sunar.

**Ana Faydaları**:
- ✅ Multi-protocol destek (REST, RabbitMQ, WebSocket)
- ✅ Comprehensive message audit trail
- ✅ Dynamic broker management
- ✅ Docker automation
- ✅ Real-time health monitoring
- ✅ Production-ready configuration
- ✅ Extensive API documentation
- ✅ Error handling ve retry mechanisms

Bu README, projeyi hiç bilmeyen birinin bile sistemin tüm detaylarını anlayabileceği şekilde hazırlanmıştır. Her teknoloji, sınıf ve method'un ne işe yaradığı, nasıl çalıştığı ve neden böyle tasarlandığı detaylıca açıklanmıştır.