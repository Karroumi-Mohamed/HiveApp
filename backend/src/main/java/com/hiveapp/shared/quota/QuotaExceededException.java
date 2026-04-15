package com.hiveapp.shared.quota;

public class QuotaExceededException extends RuntimeException {

    private final String resource;
    private final long limit;
    private final long current;
    private final String unit;

    public QuotaExceededException(String resource, long limit, long current, String unit) {
        super(String.format("Quota exceeded for '%s': limit is %d %s, current usage is %d",
                resource, limit, unit, current));
        this.resource = resource;
        this.limit = limit;
        this.current = current;
        this.unit = unit;
    }

    public String getResource() { return resource; }
    public long getLimit() { return limit; }
    public long getCurrent() { return current; }
    public String getUnit() { return unit; }
}
