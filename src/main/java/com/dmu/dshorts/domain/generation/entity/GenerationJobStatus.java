package com.dmu.dshorts.domain.generation.entity;

/** Values match the DB CHECK constraint; stored as text. */
public enum GenerationJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
