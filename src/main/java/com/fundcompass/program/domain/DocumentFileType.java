package com.fundcompass.program.domain;

import java.util.Locale;

public enum DocumentFileType {
    PDF,
    HWP,
    IMAGE,
    OTHER;

    public static DocumentFileType fromFileName(String fileName) {
        if(fileName==null || !fileName.contains(".")) {
            return OTHER;
        }

        String ext = fileName.substring(fileName.lastIndexOf(".") + 1)
                .toLowerCase(Locale.ROOT);

        return switch(ext){
            case "pdf" -> PDF;
            case "hwp","hwpx" -> HWP;
            case "png","jpg","jpeg" -> IMAGE;
            default -> OTHER;
        };
    }

    /**
     * IMAGE는 OCR 범위 밖이라 제외한다(122건, 전체의 4%).
     * OTHER는 zip·xlsx 등이라 공고문 본문이 들어 있지 않다.
     */
    public boolean isExtractable() {
        return this == PDF || this == HWP;
    }
}
