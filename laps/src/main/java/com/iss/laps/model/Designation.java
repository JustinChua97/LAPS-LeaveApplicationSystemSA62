package com.iss.laps.model;

public enum Designation {
    ADMINISTRATIVE(14),
    PROFESSIONAL(18),
    SENIOR_PROFESSIONAL(21);

    private final int annualLeaveEntitlement;

    Designation(int annualLeaveEntitlement) {
        this.annualLeaveEntitlement = annualLeaveEntitlement;
    }

    public int getAnnualLeaveEntitlement() {
        return annualLeaveEntitlement;
    }
}
