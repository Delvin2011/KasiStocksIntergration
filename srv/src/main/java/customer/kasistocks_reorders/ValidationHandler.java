package customer.kasistocks_reorders;

import com.sap.cds.services.ServiceException;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import cds.gen.integrationservice.IntegrationService_;
import cds.gen.integrationservice.ReceiveWebhookContext;
import java.util.Set;

// Exam: @Component + EventHandler interface = required pattern
// Exam: @ServiceName links handler to CDS service definition
@Component
@ServiceName(IntegrationService_.CDS_NAME)
public class ValidationHandler implements EventHandler {

    private static final Logger log =
        LoggerFactory.getLogger(ValidationHandler.class);

    // Known event types from KasiStocks /webhooks/register
    private static final Set<String> VALID_EVENTS =
        Set.of("stock.low", "new_order", "delivery",
               "transfer", "stock_adjust");

    // Exam: @Before runs BEFORE @On — validation phase
    // Throwing ServiceException here prevents @On from running
    @Before(event = ReceiveWebhookContext.CDS_NAME)
    public void validateWebhook(ReceiveWebhookContext context) {
        var payload = context.getPayload();

        // Guard: null payload
        if (payload == null) {
            throw new ServiceException(
                ErrorStatuses.BAD_REQUEST,
                "Webhook payload must not be null");
        }

        // Guard: unknown event type
        if (!VALID_EVENTS.contains(payload.getEventType())) {
            log.warn("Unknown eventType received: {}",
                     payload.getEventType());
            throw new ServiceException(
                ErrorStatuses.BAD_REQUEST,
                "Unknown eventType: " + payload.getEventType());
        }

        // Guard: mandatory storeId
        if (payload.getStoreId() == null ||
            payload.getStoreId().isBlank()) {
            throw new ServiceException(
                ErrorStatuses.BAD_REQUEST, "storeId is required");
        }

        log.debug("Webhook validation passed for event: {}",
                  payload.getEventType());
    }
}
