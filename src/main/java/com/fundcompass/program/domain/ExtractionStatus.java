package com.fundcompass.program.domain;

public enum ExtractionStatus {
    PENDING,
    EXTRACTED,
    UNSUPPORTED,
    FAILED;

    public boolean isRetryable(){
        return this == PENDING || this == FAILED;
    }
}
