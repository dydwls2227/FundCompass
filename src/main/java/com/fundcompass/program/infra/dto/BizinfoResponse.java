package com.fundcompass.program.infra.dto;

import java.util.List;

public record BizinfoResponse(
        List<BizinfoProgramItem> jsonArray,
        String reqErr
) {
    public boolean isError(){
        return reqErr!=null && !reqErr.isBlank();
    }
}
