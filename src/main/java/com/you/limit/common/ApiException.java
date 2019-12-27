package com.you.limit.common;

import lombok.Data;

/**
 * 自定义的api异常
 *
 * @author yangfm
 */
@Data
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private String code;
    private String message;
    private Object data;
    private Exception exception;

    public ApiException() {
        super();
    }

    public ApiException(String code, String message, Object data, Exception exception) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.exception = exception;
    }

    public ApiException(ApiResultEnum apiResultEnum) {
        this(apiResultEnum.getCode(), apiResultEnum.getMessage(), null, null);
    }

    public ApiException(ApiResultEnum apiResultEnum, Object data) {
        this(apiResultEnum.getCode(), apiResultEnum.getMessage(), data, null);
    }

    public ApiException(ApiResultEnum apiResultEnum, Object data, Exception exception) {
        this(apiResultEnum.getCode(), apiResultEnum.getMessage(), data, exception);
    }

}
