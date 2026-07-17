package com.xqfx.requirements;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        return new ApiError("请求参数无效", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return new ApiError("请求参数无效", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleNoResource(NoResourceFoundException exception) {
        return new ApiError("资源不存在", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        return new ApiError("数据已被其他人修改，请刷新后重试", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleDataIntegrity(DataIntegrityViolationException exception) {
        return new ApiError("数据与现有记录冲突，请刷新后重试", null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        var status = exception.getStatusCode();
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getReason() == null ? "请求处理失败" : exception.getReason(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        var traceId = UUID.randomUUID().toString();
        log.error("未处理异常，追踪编号={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("系统处理失败，请联系管理员并提供追踪编号", traceId));
    }

    record ApiError(String message, String traceId) {
    }
}
