package com.dmu.dshorts.domain.report.entity;

/** Values match the DB CHECK constraint; stored as text. */
public enum ReportReason {
    INACCURATE,
    FAKE_QUOTE,
    HARMFUL,
    INAPPROPRIATE,
    OTHER
}
