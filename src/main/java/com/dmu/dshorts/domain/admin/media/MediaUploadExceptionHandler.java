package com.dmu.dshorts.domain.admin.media;

import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Multipart failures are raised before a controller is chosen (so a controller-scoped advice cannot catch them)
 * and would otherwise become 500 in the global handler. Candidate to move into {@code GlobalExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MediaUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex) {
        return render(ApiException.validation("file", "업로드 가능한 파일 크기를 초과했습니다."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> missingPart(MissingServletRequestPartException ex) {
        return render(ApiException.validation(ex.getRequestPartName(), "필수 값입니다."));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorResponse> multipart(MultipartException ex) {
        return render(ApiException.badRequest("multipart 요청을 읽을 수 없습니다."));
    }

    private static ResponseEntity<ErrorResponse> render(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFields(), ex.getDetails()));
    }
}
