package com.fundcompass.program.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bizinfo.api")
public record BizinfoProperties(String baseUrl,String key) {
    public boolean hasKey(){
        return key!= null && !key.isBlank();
    }

}
