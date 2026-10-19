package com.dmu.dshorts.global.error;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to the contract error body {@code {"error": {"code", "message", "fields"}}}.
 *
 * <ul>
 *   <li>{@link ApiException}: its own status/code/message/fields/details.</li>
 *   <li>Bean validation ({@code @Valid} body, {@code @Validated} params): 400 VALIDATION_FAILED with fields.</li>
 *   <li>Missing request parameter: 400 VALIDATION_FAILED. Unreadable JSON body: 400 BAD_REQUEST.</li>
 *   <li>Type mismatch: on a {@code @PathVariable} 404 NOT_FOUND (an ID that cannot exist), otherwise
 *       400 VALIDATION_FAILED.</li>
 *   <li>Unknown URL: 404. DB unique/FK violation not caught by the service: 409 CONFLICT.</li>
 *   <li>Anything else: logged with stack trace, 500 INTERNAL_ERROR.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("API error {} {}: {}", ex.getStatus().value(), ex.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFields(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), messageOrDefault(error.getDefaultMessage())))
                .toList();
        if (fields.isEmpty()) {
            fields = ex.getBindingResult().getGlobalErrors().stream()
                    .map(error -> new FieldErrorDetail(error.getObjectName(), messageOrDefault(error.getDefaultMessage())))
                    .toList();
        }
        return validationFailed(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(),
                                messageOrDefault(error.getDefaultMessage()))))
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> fields = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationFailed(List.of(new FieldErrorDetail(ex.getParameterName(), "필수 값입니다.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.BAD_REQUEST, "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
        }
        return validationFailed(List.of(new FieldErrorDetail(ex.getName(), "형식이 올바르지 않습니다.")));
    }

    @ExceptionHandler({NoResourceFoundException.class, MissingPathVariableException.class})
    ResponseEntity<ErrorResponse> handleNoResource(Exception ex) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.info("Data integrity violation mapped to 409: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.CONFLICT, "다른 데이터와 충돌하여 저장할 수 없습니다.");
    }

    /** Thrown by method security (e.g. @PreAuthorize) inside the MVC layer. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
        ErrorCode code = anonymous ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN;
        return respond(code, code.defaultMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ErrorResponse> validationFailed(List<FieldErrorDetail> fields) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED.name(),
                        ErrorCode.VALIDATION_FAILED.defaultMessage(), fields, null));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message));
    }

    private static String messageOrDefault(String message) {
        return message == null ? "잘못된 값입니다." : message;
    }

    private static String lastPathNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
