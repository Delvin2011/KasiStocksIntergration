package customer.kasistocks_reorders;

import com.sap.cds.services.ServiceException;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.ql.Insert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cds.gen.integrationservice.IntegrationService_;
import cds.gen.integrationservice.ReceiveWebhookContext;
import cds.gen.kasistocks.integration.StockEventLog;
import cds.gen.kasistocks.integration.StockEventLog_;

@Component
@ServiceName(IntegrationService_.CDS_NAME)
public class IntegrationEventHandler implements EventHandler {

    private static final Logger log =
        LoggerFactory.getLogger(IntegrationEventHandler.class);

    // Exam: @Autowired PersistenceService — CAP's DB interface
    // PersistenceService bypasses service-layer auth checks
    // Use ApplicationService injection when auth checks ARE needed
    @Autowired
    private PersistenceService db;
// Exam: @On replaces the generic handler — full control
    // context.setResult() MUST be called to complete the response
    @On(event = ReceiveWebhookContext.CDS_NAME)
    public void onReceiveWebhook(ReceiveWebhookContext context) {

        var payload = context.getPayload();
        log.info("Processing webhook: eventType={}, storeId={}",
                 payload.getEventType(), payload.getStoreId());

        try {
            // Build HANA record using generated type-safe accessor
            StockEventLog record = StockEventLog.create();
            record.setEventType(payload.getEventType());
            record.setStoreId(payload.getStoreId());
            record.setProductId(payload.getProductId());
            record.setProductName(payload.getProductName());
            record.setQtyOnHand(payload.getQtyOnHand());
            record.setThreshold(payload.getThreshold());

            // Apply routing logic
            applyBusinessLogic(record);

            // Exam: type-safe CQN Insert API
            // Insert.into(Class) preferred over Insert.into(String)
            db.run(Insert.into(StockEventLog_.class).entry(record));

            log.info("StockEventLog persisted. Status: {}",
                     record.getStatus());

            // Exam: context.setResult() completes the @On handler
            context.setResult(
                "Webhook processed. Status: " + record.getStatus());

        } catch (ServiceException se) {
            // Re-throw CAP ServiceExceptions (validation, auth)
            throw se;

        } catch (Exception e) {
            log.error("Unexpected error processing webhook", e);
            // Return error string — iFlow logs it without crashing
            context.setResult(
                "ERROR: " + e.getMessage());
        }
    }

    private void applyBusinessLogic(StockEventLog record) {
        switch (record.getEventType()) {
            case "stock.low" -> {
                record.setStatus("PROCESSED");
                record.setProcessedNote(
                    "Qty " + record.getQtyOnHand() +
                    " at or below threshold " + record.getThreshold() +
                    ". Alert queued.");
            }
            case "new_order" -> {
                record.setStatus("PROCESSED");
                record.setProcessedNote(
                    "Order received for store " + record.getStoreId());
            }
            default -> {
                record.setStatus("IGNORED");
                record.setProcessedNote(
                    "No routing rule for: " + record.getEventType());
            }
        }
    }
}
