package com.dmu.dshorts.global.error;

/** One entry of {@code error.fields}: which input field was rejected and why. */
public record FieldErrorDetail(String field, String message) {
}
