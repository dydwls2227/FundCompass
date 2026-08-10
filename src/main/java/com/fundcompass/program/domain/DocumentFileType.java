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

    public boolean isExtractable() {
        return this == PDF;
    }
}
