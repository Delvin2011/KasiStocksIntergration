using { kasistocks.integration as db } from '../db/schema';

@path: '/service/integration'
@requires: 'authenticated-user' // Securing via XSUAA
service IntegrationService {

    entity StockEvents  as projection on db.StockEventLog;

    entity AlertHistory as projection on db.StockAlertLog;

    @requires: 'authenticated-user'
    action ReceiveWebhook(payload: db.WebhookPayload) returns String;

    action ApprovePurchaseOrder(eventLogId: UUID) returns String;
}
