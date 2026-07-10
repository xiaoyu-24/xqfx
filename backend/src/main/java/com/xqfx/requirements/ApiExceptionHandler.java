package com.xqfx.requirements;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        return new ApiError("数据已被其他人修改，请刷新后重试");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        var status = exception.getStatusCode();
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getReason() == null ? "请求处理失败" : exception.getReason()));
    }

    record ApiError(String message) {
    }
}
