package customer.kasistocks_reorders;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import cds.gen.integrationservice.IntegrationService_;
import cds.gen.integrationservice.ReceiveWebhookContext;

@Component
@ServiceName(IntegrationService_.CDS_NAME)
public class AlertHandler implements EventHandler {

    private static final Logger log =
        LoggerFactory.getLogger(AlertHandler.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${vcap.services.kasistocks-reorders-ans.credentials.url:}")
    private String ansUrl;

    @Value("${vcap.services.kasistocks-reorders-ans.credentials.client_id:}")
    private String clientId;

    @Value("${vcap.services.kasistocks-reorders-ans.credentials.client_secret:}")
    private String clientSecret;

    @Value("${vcap.services.kasistocks-reorders-ans.credentials.oauth_url:}")
    private String oauthUrl;

    @After(event = ReceiveWebhookContext.CDS_NAME)
    public void sendAlert(ReceiveWebhookContext context) {
        var payload = context.getPayload();

        if (!"stock.low".equals(payload.getEventType())) {
            return;
        }

        if (ansUrl.isBlank() || clientId.isBlank()) {
            log.warn("ANS service binding not configured — skipping alert");
            return;
        }

        try {
            String token = getAnsToken(oauthUrl, clientId, clientSecret);

            Map<String, Object> alertBody = new HashMap<>();
            alertBody.put("eventType",      "stock.low_alert");
            alertBody.put("eventTimestamp", System.currentTimeMillis() / 1000);
            alertBody.put("severity",       "WARNING");
            alertBody.put("category",       "ALERT");
            alertBody.put("subject",        "Low Stock: " + payload.getProductName());
            alertBody.put("body",           "Store " + payload.getStoreId() +
                                            " \u2014 " + payload.getProductName() +
                                            " has " + payload.getQtyOnHand() +
                                            " units remaining.");
            alertBody.put("resource", Map.of(
                "resourceName", "KasiStocks",
                "resourceType", "bottle-store-pos"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            String jsonBody = objectMapper.writeValueAsString(alertBody);
            byte[] jsonBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            headers.setContentLength(jsonBytes.length);

            restTemplate.postForEntity(
                ansUrl + "/cf/producer/v1/resource-events",
                new HttpEntity<>(jsonBody, headers),
                String.class
            );

            log.info("ANS alert sent for: {}", payload.getProductName());

        } catch (Exception e) {
            log.error("ANS alert failed (non-fatal): {}", e.getMessage());
        }
    }
    
    private String getAnsToken(String tokenUrl,
                                String clientId,
                                String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        // grant_type is already in the URL as a query param
        // send empty body
        var response = restTemplate.postForObject(
            tokenUrl,
            new HttpEntity<>("", headers),
            Map.class
        );

        if (response == null || response.get("access_token") == null) {
            throw new RuntimeException(
                "No access_token in ANS response: " + response);
        }

        return response.get("access_token").toString();
    }
}


