package com.lia.core.pipeline.connector;

/** 열린국회 API가 ERROR-* 코드를 반환했을 때. */
public class AssemblyApiException extends RuntimeException {
    public AssemblyApiException(String message) {
        super(message);
    }
}
