package com.elering.pricewatch.domain.enums;

/**
 * Baltic electricity pricing zones supported by the Elering API.
 *
 * <p>The zone codes match the field names returned by the Elering NPS price endpoint,
 * e.g. {@code /nps/price?fields=ee} for Estonia.
 */
public enum Zone {

    /** Estonia */
    EE("ee", "Estonia"),

    /** Finland */
    FI("fi", "Finland"),

    /** Latvia */
    LV("lv", "Latvia"),

    /** Lithuania */
    LT("lt", "Lithuania");

    private final String apiCode;
    private final String displayName;

    Zone(String apiCode, String displayName) {
        this.apiCode = apiCode;
        this.displayName = displayName;
    }

    /** Returns the lowercase API field code used in Elering API requests. */
    public String getApiCode() {
        return apiCode;
    }

    /** Returns the human-readable country name. */
    public String getDisplayName() {
        return displayName;
    }
}
