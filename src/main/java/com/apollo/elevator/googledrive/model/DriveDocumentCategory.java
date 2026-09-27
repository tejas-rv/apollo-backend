package com.apollo.elevator.googledrive.model;

public enum DriveDocumentCategory {
    AMC_CONTRACT("Contracts"),
    BILL("Bills"),
    SERVICE_HISTORY("Service History"),
    WORK_ORDER("Work Orders"),
    QUOTATION("Quotations"),
    LIFT_IMAGES("Lift Images");

    private final String folderName;

    DriveDocumentCategory(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }
}
