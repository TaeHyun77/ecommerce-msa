package com.park.ecommerce.exception;

import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.category.CategoryException;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.exception.reservation.ReservationErrorCode;
import com.park.ecommerce.exception.reservation.ReservationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(ProductException e) {
        ProductErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, errorCode.getMessage(), e.getErrors()));
    }

    @ExceptionHandler(CategoryException.class)
    public ResponseEntity<ErrorResponse> handleCategoryException(CategoryException e) {
        CategoryErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(InboundException.class)
    public ResponseEntity<ErrorResponse> handleInboundException(InboundException e) {
        InboundErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<ErrorResponse> handleReservationException(ReservationException e) {
        ReservationErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // 메시지에는 첫 번째 검증 메시지를, errors에는 모든 필드 오류를 경로(예: products[3].price)와 함께 담는다
    // 일괄 등록은 여러 행이 함께 잘못될 수 있어 첫 번째 메시지만으로는 어느 행을 고칠지 알 수 없기 때문
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ProductErrorCode errorCode = ProductErrorCode.INVALID_INPUT;
        List<ErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        String message = errors.isEmpty() ? errorCode.getMessage() : errors.get(0).message();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message, errors));
    }

    // JSON 형식 오류나 존재하지 않는 enum 값(storageType 등) - 기본 처리에 맡기면 응답 형식이 달라져 함께 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadableException(HttpMessageNotReadableException e) {
        ProductErrorCode errorCode = ProductErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // 쿼리 파라미터 누락이나 존재하지 않는 enum 값(status 등) - 기본 처리에 맡기면 응답 형식이 달라져 함께 처리
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidParameterException(Exception e) {
        ProductErrorCode errorCode = ProductErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // @RequestParam에 붙인 제약(@Min, @Max 등) 위반 - 첫 번째 검증 메시지를 그대로 전달
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(HandlerMethodValidationException e) {
        ProductErrorCode errorCode = ProductErrorCode.INVALID_INPUT;
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message));
    }
}
