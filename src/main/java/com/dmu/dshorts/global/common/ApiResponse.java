package com.dmu.dshorts.global.common;

/** Success body {@code {"data": ...}}. Return {@code ApiResponse.of(dto)} from controllers. */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
