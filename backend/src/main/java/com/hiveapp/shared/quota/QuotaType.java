package com.hiveapp.shared.quota;

public enum QuotaType {
    COUNT,   // counting discrete items: members, companies, invoices, files
    STORAGE, // measuring size: stored in MB
    RATE     // measuring throughput: req/s, calls/month
}
