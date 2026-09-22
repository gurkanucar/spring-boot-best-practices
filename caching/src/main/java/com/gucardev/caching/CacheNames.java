package com.gucardev.caching;

/** Logical buckets, created on demand. Select storage and TTL through {@link CacheManagers}. */
public final class CacheNames {

    private CacheNames() {
    }

    public static final String USERS = "users";
    public static final String ROLES = "roles";
    public static final String SETTINGS = "settings";
    public static final String FILE_METADATA = "file_metadata";
    public static final String FEATURE_FLAGS = "feature_flags";
    public static final String EXAMPLE_PRODUCTS = "example_products";
    public static final String EXAMPLE_ORDERS = "example_orders";
}
