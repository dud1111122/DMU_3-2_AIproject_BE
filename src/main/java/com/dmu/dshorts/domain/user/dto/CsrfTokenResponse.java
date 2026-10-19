package com.dmu.dshorts.domain.user.dto;

/** Token to send back in header {@code headerName} on state-changing requests. */
public record CsrfTokenResponse(String headerName, String token) {
}
