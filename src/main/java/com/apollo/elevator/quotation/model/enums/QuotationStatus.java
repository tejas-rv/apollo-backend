package com.apollo.elevator.quotation.model.enums;

public enum QuotationStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED;

    public boolean canTransitionTo(QuotationStatus targetStatus) {
        if (targetStatus == null || this == targetStatus) {
            return false;
        }

        return switch (this) {
            case DRAFT -> targetStatus == SENT || targetStatus == REJECTED;
            case SENT -> targetStatus == ACCEPTED || targetStatus == REJECTED || targetStatus == EXPIRED;
            case ACCEPTED, REJECTED, EXPIRED -> false;
        };
    }

    public String invalidTransitionMessage(QuotationStatus targetStatus) {
        return "Invalid quotation status transition from " + this + " to " + targetStatus
                + ". Allowed transitions: DRAFT -> SENT, DRAFT -> REJECTED, "
                + "SENT -> ACCEPTED, SENT -> REJECTED, SENT -> EXPIRED.";
    }
}
