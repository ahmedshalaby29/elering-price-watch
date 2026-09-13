package com.elering.pricewatch.domain.enums;

/**
 * Direction of an alert threshold check.
 */
public enum AlertDirection {

    /** Notify when the hourly price falls <em>below</em> the configured threshold. */
    BELOW,

    /** Notify when the hourly price rises <em>above</em> the configured threshold. */
    ABOVE
}
