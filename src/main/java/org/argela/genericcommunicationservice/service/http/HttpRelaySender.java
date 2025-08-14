package org.argela.genericcommunicationservice.service.http;

import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RestSendDto;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP Relay servisi.
 * Headers'dan URL ve method bilgisini alarak HTTP çağrısı yapar.
 */
@Slf4j
@Service
public class HttpRelaySender {

    private final RestTemplate restTemplate;

    // Constructor'da RestTemplate'i kendimiz oluşturuyoruz
    public HttpRelaySender() {
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(30))      // ✅ Yeni API
                .readTimeout(Duration.ofSeconds(60))         // ✅ Yeni API
                .build();
    }

    /**
     * REST mesajını headers'daki URL'e gönderir.
     *
     * @param dto HTTP mesaj formatı (headers + body)
     * @return Gönderim sonucu
     */
    public HttpRelayResult send(RestSendDto dto) {
        try {
            // Headers'dan URL'i al
            String targetUrl = extractUrl(dto.getHeaders());
            if (targetUrl == null) {
                return HttpRelayResult.failure(0, "Headers'da 'url' bilgisi bulunamadı", null);
            }

            // Headers'dan HTTP method'u al (default: POST)
            String method = dto.getHeaders().getOrDefault("method", "POST").toUpperCase();

            log.info("HTTP mesajı gönderiliyor: {} -> {}", method, targetUrl);

            // HTTP headers hazırla (url ve method'u çıkar, gerisi kalır)
            HttpHeaders headers = buildHeaders(dto.getHeaders());

            // HTTP entity oluştur
            HttpEntity<String> entity = createHttpEntity(dto.getBody(), method, headers);

            // HTTP method belirle
            HttpMethod httpMethod;
            try {
                httpMethod = HttpMethod.valueOf(method);
            } catch (IllegalArgumentException e) {
                return HttpRelayResult.failure(0, "Geçersiz HTTP method: " + method, null);
            }

            // REST çağrısı yap
            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    httpMethod,
                    entity,
                    String.class
            );

            log.info("HTTP çağrısı başarılı: {} - {}", response.getStatusCode(), targetUrl);

            return HttpRelayResult.success(
                    response.getStatusCode().value(),
                    response.getBody()
            );

        } catch (HttpClientErrorException e) {
            log.error("HTTP çağrısı 4xx hatası: {} -> {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            return HttpRelayResult.failure(
                    e.getStatusCode().value(),
                    "Client Error: " + e.getMessage(),
                    e.getResponseBodyAsString()
            );

        } catch (HttpServerErrorException e) {
            log.error("HTTP çağrısı 5xx hatası: {} -> {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            return HttpRelayResult.failure(
                    e.getStatusCode().value(),
                    "Server Error: " + e.getMessage(),
                    e.getResponseBodyAsString()
            );

        } catch (ResourceAccessException e) {
            log.error("HTTP çağrısı bağlantı hatası: {}", e.getMessage());

            return HttpRelayResult.failure(
                    0,
                    "Connection Error: " + e.getMessage(),
                    null
            );

        } catch (Exception e) {
            log.error("HTTP çağrısı genel hata: {}", e.getMessage(), e);

            return HttpRelayResult.failure(
                    0,
                    "Unexpected Error: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Headers'dan URL'i extract et.
     * Önce 'url', sonra 'target-url', sonra 'endpoint' key'lerini dener.
     */
    private String extractUrl(Map<String, String> headers) {
        if (headers == null) return null;

        // Muhtemel URL key'leri
        String[] urlKeys = {"url", "target-url", "endpoint", "target", "destination"};

        for (String key : urlKeys) {
            String url = headers.get(key);
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
        }

        return null;
    }

    /**
     * HTTP headers oluştur.
     * url, method, target-url gibi özel key'leri çıkararak gerçek HTTP header'ları hazırla.
     */
    private HttpHeaders buildHeaders(Map<String, String> customHeaders) {
        HttpHeaders headers = new HttpHeaders();

        if (customHeaders != null) {
            customHeaders.forEach((key, value) -> {
                // URL ve method bilgilerini header'a eklemeyelim (bunlar HTTP'nin bir parçası değil)
                if (!isMetadataKey(key)) {
                    headers.add(key, value);
                }
            });
        }

        // Content-Type yoksa default olarak application/json ekle
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        return headers;
    }

    /**
     * Metadata key'leri kontrol et (bunlar gerçek HTTP header'ı değil)
     */
    private boolean isMetadataKey(String key) {
        return key.equalsIgnoreCase("url") ||
                key.equalsIgnoreCase("target-url") ||
                key.equalsIgnoreCase("endpoint") ||
                key.equalsIgnoreCase("target") ||
                key.equalsIgnoreCase("destination") ||
                key.equalsIgnoreCase("method") ||
                key.equalsIgnoreCase("sender") ||
                key.equalsIgnoreCase("group-id") ||
                key.equalsIgnoreCase("groupId");
    }

    /**
     * HTTP method'a göre entity oluştur.
     * GET isteklerinde body gönderilmez.
     */
    private HttpEntity<String> createHttpEntity(String body, String method, HttpHeaders headers) {
        // GET isteklerinde body göndermiyoruz
        if ("GET".equalsIgnoreCase(method)) {
            return new HttpEntity<>(headers);
        }

        // Diğer method'larda body gönderebiliriz
        String requestBody = body != null ? body : "";
        return new HttpEntity<>(requestBody, headers);
    }

    /**
     * HTTP Relay sonuç nesnesi
     */
    public static class HttpRelayResult {
        private final boolean success;
        private final int statusCode;
        private final String responseBody;
        private final String errorMessage;

        private HttpRelayResult(boolean success, int statusCode, String responseBody, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
        }

        public static HttpRelayResult success(int statusCode, String responseBody) {
            return new HttpRelayResult(true, statusCode, responseBody, null);
        }

        public static HttpRelayResult failure(int statusCode, String errorMessage, String responseBody) {
            return new HttpRelayResult(false, statusCode, responseBody, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
        public String getErrorMessage() { return errorMessage; }

        /**
         * HTTP status code'a göre mesajın durumunu belirle
         */
        public boolean isDelivered() {
            return success && statusCode >= 200 && statusCode < 300;
        }
    }
}