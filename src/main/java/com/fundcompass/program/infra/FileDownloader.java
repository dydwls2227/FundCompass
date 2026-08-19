package com.fundcompass.program.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Component
public class FileDownloader {
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private final RestClient restClient;

    public FileDownloader(){
        this.restClient = RestClient.builder()
                .requestFactory(clientRequestFactory())
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    public byte[] download(String url){
        byte[] body = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);

        if(body == null || body.length == 0){
            throw new IllegalStateException("빈 응답");
        }
        if(body.length > MAX_BYTES){
            throw new IllegalStateException("파일이 너무 큼: %d bytes".formatted(body.length));
        }
        return body;
    }
}
