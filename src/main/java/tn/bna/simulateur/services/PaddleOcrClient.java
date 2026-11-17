package tn.bna.simulateur.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PaddleOcrClient {

    private final RestTemplate restTemplate;

    @Value("${app.ocr.paddle.url:http://localhost:8090/api/ocr/extract}")
    private String endpoint;

    public PaddleOcrClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(300));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public Map<String, Object> extract(String typeDocument, byte[] bytes, String mimeType) {
        try {
            String filename = "document" + extensionFor(mimeType);
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(mimeType));
            partHeaders.setContentDisposition(
                    ContentDisposition.formData().name("file").filename(filename).build()
            );

            ByteArrayResource file = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("typeDocument", typeDocument);
            form.add("file", new HttpEntity<>(file, partHeaders));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() == null) {
                return unavailable("Le microservice PaddleOCR a renvoyé une réponse vide.");
            }
            return response.getBody();
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Microservice PaddleOCR indisponible ({}).", e.getClass().getSimpleName());
            return unavailable("Le microservice PaddleOCR est indisponible.");
        }
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
    }

    private Map<String, Object> unavailable(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ocrDisponible", false);
        result.put("typeDocument", null);
        result.put("erreur", message);
        result.put("champs", Map.of());
        result.put("confiance", 0.0);
        result.put("alertes", List.of(message));
        return result;
    }
}
