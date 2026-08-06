package com.fundcompass.program.infra;

import com.fundcompass.program.infra.dto.BizinfoProgramItem;
import com.fundcompass.program.infra.dto.BizinfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class BizinfoClient {
    private final RestClient restClient;
    private final BizinfoProperties properties;

    public BizinfoClient(BizinfoProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    public List<BizinfoProgramItem> fetch(int count){
        if(!properties.hasKey()){
            throw new IllegalStateException("API KEY가 설정되지 않았습니다");
        }

        BizinfoResponse response = restClient.get().uri(
                uriBuilder -> uriBuilder
                        .queryParam("crtfcKey", properties.key())
                        .queryParam("dataType","json")
                        .queryParam("searchCnt",count)
                        .build())
                .retrieve()
                .body(BizinfoResponse.class);

        if(response == null){
            throw new IllegalStateException("기업마당 응답이 비어있습니다.");
        }
        if(response.isError()){
            throw new IllegalStateException("기업마당 API 오류 : " + response.reqErr());
        }

        List<BizinfoProgramItem> items = response.jsonArray();

        if(items == null){
            return List.of();
        }

        log.info("기업마당 공고 {}건 조회", items.size());

        return items;
    }
}
