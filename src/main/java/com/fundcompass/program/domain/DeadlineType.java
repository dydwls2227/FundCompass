package com.fundcompass.program.domain;

public enum DeadlineType {
    FIXED_PERIOD,
    UNTIL_BUDGET,
    ALWAYS_OPEN,
    FIRST_COME,
    VARIES,
    UNKNOWN;

    public boolean isAlwaysOpen(){
        return this == UNTIL_BUDGET || this == ALWAYS_OPEN || this == FIRST_COME;
    }

    public boolean needsManualCheck(){
        return this == VARIES || this == UNKNOWN;
    }
}
