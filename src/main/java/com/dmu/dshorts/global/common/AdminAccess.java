package com.dmu.dshorts.global.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminAccess {
    @Value("${app.admin.token:}") private String expected;
    public void require(HttpServletRequest request){
        String token=request.getHeader("X-Admin-Token");
        if(expected==null||expected.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"운영자 토큰이 설정되지 않았습니다.");
        if(token==null||!expected.equals(token))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"운영자 권한이 필요합니다.");
    }
}
