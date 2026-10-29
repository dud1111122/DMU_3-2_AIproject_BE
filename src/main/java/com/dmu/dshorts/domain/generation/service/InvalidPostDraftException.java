package com.dmu.dshorts.domain.generation.service;

/** A post draft (seed file or AI output) breaks a content rule. The message names the rule. */
public class InvalidPostDraftException extends RuntimeException {

    public InvalidPostDraftException(String message) {
        super(message);
    }
}
