package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.DisclosureGroup;

/**
 * Neither the account's disclosure group nor the {@code DEFAULT} group exists for a category. In
 * CBACT04C this is {@code 'ERROR READING DEFAULT DISCLOSURE GROUP'} followed by
 * {@code 9999-ABEND-PROGRAM} (lines 452-459).
 */
public class DisclosureGroupNotFoundException extends RuntimeException {

    private final DisclosureGroup.Key accountGroupKey;
    private final DisclosureGroup.Key defaultGroupKey;

    public DisclosureGroupNotFoundException(DisclosureGroup.Key accountGroupKey, DisclosureGroup.Key defaultGroupKey) {
        super("ERROR READING DEFAULT DISCLOSURE GROUP: no record for " + accountGroupKey + " or " + defaultGroupKey);
        this.accountGroupKey = accountGroupKey;
        this.defaultGroupKey = defaultGroupKey;
    }

    public DisclosureGroup.Key accountGroupKey() {
        return accountGroupKey;
    }

    public DisclosureGroup.Key defaultGroupKey() {
        return defaultGroupKey;
    }
}
