package com.cue.reply.data.db

/** Every event Cue records in the audit log. Mirrors the honesty/trust surface. */
enum class ActivityType {
    RECEIVED,
    SUGGESTED,
    INSERTED,
    AUTO_SENT,
    SEND_RETRY,
    SEND_FAILED,
    BLOCKED_NOT_ALLOWLISTED,
    RATE_LIMITED,
    CADENCE_HELD,
    A11Y_DETECTED,
    SELECTOR_BROKEN,
    KILLED,
    RESUMED,
    ERROR,
}
