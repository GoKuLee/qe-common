package com.dankegongyu.app.common;

import com.dankegongyu.common.util.UUID19;
import org.slf4j.MDC;

public class TraceIdUtils {
    static String TRACEID = "traceId";

    public static void setTraceId(String traceId) {
        MDC.put(TRACEID, traceId);
    }

    public static void setTraceId() {
        if (getTraceId() == null)
            setTraceId(UUID19.randomUUID());
    }

    public static String getTraceId() {
        return MDC.get(TRACEID);
    }
}