package customer.kasistocks_reorders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exam: @SpringBootTest loads full application context
// Exam: @AutoConfigureMockMvc wires MockMvc without starting a real server
// @ActiveProfiles uses application-local.properties (H2 + mock auth)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ValidationHandlerTest {

    private static final String ACTION_URI =
        "/odata/v4/service/integration/ReceiveWebhook";

    private static final String EVENTS_URI =
        "/odata/v4/service/integration/StockEvents";

    // Valid stock.low payload — all required fields present
    private static final String VALID_PAYLOAD = """
        {
          "payload": {
            "eventType": "stock.low",
            "storeId": "1",
            "productId": "42",
            "productName": "Castle Lager 500ml",
            "qtyOnHand": 0,
            "threshold": 6
          }
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    // ─────────────────────────────────────────────────────────────
    // Test 1 — Happy path: valid payload returns PROCESSED
    // Proves: @On handler runs, PersistenceService writes to H2
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "alice", authorities = {"IntegrationProcessor"})
    void validPayload_shouldReturnProcessed() throws Exception {
        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(VALID_PAYLOAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value")
                .value("Webhook processed. Status: PROCESSED"));
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2 — Record written to H2 after successful POST
    // Proves: managed aspect sets createdBy from mock user
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "alice", authorities = {"IntegrationProcessor", "Viewer"})
    void validPayload_shouldPersistRecord() throws Exception {
        // POST the webhook
        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYLOAD));

        // Verify record exists in StockEvents
        mockMvc.perform(get(EVENTS_URI))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value[0].eventType")
                .value("stock.low"))
            .andExpect(jsonPath("$.value[0].status")
                .value("PROCESSED"))
            .andExpect(jsonPath("$.value[0].createdBy")
                .value("alice"))          // managed aspect
            .andExpect(jsonPath("$.value[0].ID")
                .isNotEmpty());               // cuid aspect
    }

    // ─────────────────────────────────────────────────────────────
    // Test 3 — Null payload rejected by @Before handler
    // Proves: @Before stops the chain — @On never runs
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "alice", authorities = {"IntegrationProcessor"})
    void nullPayload_shouldReturnBadRequest() throws Exception {
        String nullPayload = """
            { "payload": null }
            """;

        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(nullPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value("Webhook payload must not be null"));
    }

    // ─────────────────────────────────────────────────────────────
    // Test 4 — Unknown eventType rejected by @Before handler
    // Proves: VALID_EVENTS set enforcement works correctly
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "alice", authorities = {"IntegrationProcessor"})
    void unknownEventType_shouldReturnBadRequest() throws Exception {
        String unknownEvent = """
            {
              "payload": {
                "eventType": "unknown_event",
                "storeId": "1"
              }
            }
            """;

        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(unknownEvent))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value("Unknown eventType: unknown_event"));
    }

    // ─────────────────────────────────────────────────────────────
    // Test 5 — Missing storeId rejected by @Before handler
    // Proves: mandatory field guard prevents incomplete records
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "alice", authorities = {"IntegrationProcessor"})
    void missingStoreId_shouldReturnBadRequest() throws Exception {
        String noStoreId = """
            {
              "payload": {
                "eventType": "stock.low",
                "productName": "Castle Lager 500ml"
              }
            }
            """;

        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(noStoreId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value("storeId is required"));
    }

    // ─────────────────────────────────────────────────────────────
    // Test 6 — Wrong role returns 403 Forbidden
    // Proves: @requires: 'IntegrationProcessor' CDS annotation works
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "bob", authorities = {"Viewer"})
    void viewerRole_shouldBeForbidden() throws Exception {
        mockMvc.perform(post(ACTION_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYLOAD))
            .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────
    // Test 7 — GET StockEvents returns 200 for Viewer role
    // Proves: @restrict READ grant works for Viewer
    // ─────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(username = "carol", authorities = {"Viewer"})
    void viewerRole_canReadStockEvents() throws Exception {
        mockMvc.perform(get(EVENTS_URI))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value").isArray());
    }
}