package com.apollo.elevator.enquiry.model.enums;

public enum InquiryStatus {
    NEW,
    IN_PROGRESS,
    WORK_ORDER,
    COMPLETED,
    CLOSED;

    public boolean canTransitionTo(InquiryStatus targetStatus) {
        if (targetStatus == null || this == targetStatus) {
            return false;
        }

        return switch (this) {
            case NEW -> targetStatus == IN_PROGRESS || targetStatus == CLOSED;
            case IN_PROGRESS -> targetStatus == WORK_ORDER || targetStatus == CLOSED;
            case WORK_ORDER -> targetStatus == CLOSED;
            case COMPLETED, CLOSED -> false;
        };
    }

    public String invalidTransitionMessage(InquiryStatus targetStatus) {
        return "Invalid enquiry status transition from " + this + " to " + targetStatus
                + ". Allowed transitions: NEW -> IN_PROGRESS, NEW -> CLOSED, "
                + "IN_PROGRESS -> WORK_ORDER, IN_PROGRESS -> CLOSED, WORK_ORDER -> CLOSED.";
    }
}
