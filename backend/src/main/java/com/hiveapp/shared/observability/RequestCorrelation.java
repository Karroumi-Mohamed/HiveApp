package com.hiveapp.shared.observability;

import org.slf4j.MDC;

/** Shared access to the bounded request correlation id stored in logging MDC. */
public final class RequestCorrelation {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private RequestCorrelation() {}

    public static String currentId() {
        return MDC.get(MDC_KEY);
    }
}
