package com.dmu.dshorts.global.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class UserIdentity {
    @Value("${app.user.header:X-Anonymous-User-Id}") private String header;
    public String current(HttpServletRequest request){
        String id=request.getHeader(header);
        if(id==null||id.isBlank()) return "local-demo-user";
        if(id.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"사용자 식별자는 100자 이하여야 합니다.");
        return id.trim();
    }
}
