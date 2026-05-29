namespace kasistocks.integration;

using { cuid, managed } from '@sap/cds/common';

// Structured type — replaces raw String payload
// Exam topic: CDS type definitions and their OData exposure
type WebhookPayload {
    eventType   : String(50);
    storeId     : String(50);
    productId   : String(50);
    productName : String(100);
    qtyOnHand   : Integer;
    threshold   : Integer;
}

// cuid    : CAP generates UUID automatically (exam: aspects)
// managed : createdAt, createdBy, modifiedAt, modifiedBy auto-set
entity StockEventLog : cuid, managed {
    eventType     : String(50)  @mandatory;
    productId     : String(50);
    productName   : String(100);
    storeId       : String(50)  @mandatory;
    qtyOnHand     : Integer;
    threshold     : Integer;
    status        : String(20)  default 'NEW';   // NEW | PROCESSED | ERROR
    processedNote : String(500);
    // Exam: Association vs Composition
    alerts        : Composition of many StockAlertLog on alerts.event = $self;
}

entity StockAlertLog : cuid, managed {
    event       : Association to StockEventLog;
    alertType   : String(20);  // EMAIL | WHATSAPP | SAP_ANS
    recipient   : String(100);
    sentAt      : Timestamp;
    success     : Boolean default false;
}
