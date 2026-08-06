package com.fundcompass.program.infra;

import com.fundcompass.program.infra.dto.BizinfoProgramItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BizinfoClientTest {

    @Autowired
    BizinfoClient bizinfoClient;

    @Test
    void 공고를_조회한다() {
        List<BizinfoProgramItem> items = bizinfoClient.fetch(3);

        assertThat(items).hasSize(3);
        items.forEach(item -> System.out.println(item.pblancNm()));
    }
}