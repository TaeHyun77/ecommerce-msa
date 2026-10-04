package com.park.ecommerce.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SearchException.class)
    public ResponseEntity<ErrorResponse> handleSearchException(SearchException e) {
        SearchErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // 검색어 누락이나 숫자가 아닌 파라미터 - 기본 처리에 맡기면 응답 형식이 달라져 함께 처리
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidParameterException(Exception e) {
        SearchErrorCode errorCode = SearchErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // @RequestParam에 붙인 제약(@Min, @Max) 위반 - 첫 번째 검증 메시지를 그대로 전달
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(HandlerMethodValidationException e) {
        SearchErrorCode errorCode = SearchErrorCode.INVALID_INPUT;
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message));
    }

    // 서킷이 열려 호출이 차단된 경우 - 대상 서비스 장애와 같은 의미이므로 동일한 503으로 응답
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCallNotPermitted(CallNotPermittedException e) {
        SearchErrorCode errorCode = SearchErrorCode.PRODUCT_SERVICE_UNAVAILABLE;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }
}
