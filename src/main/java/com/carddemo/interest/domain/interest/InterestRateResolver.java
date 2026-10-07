package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * CBACT04C {@code 1200-GET-INTEREST-RATE} and {@code 1200-A-GET-DEFAULT-INT-RATE}: finds the
 * disclosure group for an account group and transaction category, falling back to group
 * {@code DEFAULT} when the account's own group has no record.
 *
 * <p>The lookup is the {@code DisclosureGroupRepository} port, passed as a method reference
 * ({@code new InterestRateResolver(repository::findByKey)}) so the domain does not depend on
 * {@code application}. An empty result is file status {@code 23} (record not found). Any other
 * read failure is the adapter's to throw, matching the abend on non-{@code 00}/{@code 23}
 * statuses at lines 422-435.
 */
public class InterestRateResolver {

    /** Literal moved to {@code FD-DIS-ACCT-GROUP-ID} on status 23 (line 437). */
    public static final String DEFAULT_GROUP_ID = "DEFAULT";

    private final Function<DisclosureGroup.Key, Optional<DisclosureGroup>> disclosureGroups;

    public InterestRateResolver(Function<DisclosureGroup.Key, Optional<DisclosureGroup>> disclosureGroups) {
        this.disclosureGroups = Objects.requireNonNull(disclosureGroups);
    }

    /**
     * Resolves the disclosure group for one TCATBALF record.
     *
     * @param accountGroupId ACCT-GROUP-ID PIC X(10) of the record's account, passed through as is
     * @param categoryKey    TRAN-CAT-KEY; supplies TRANCAT-TYPE-CD and TRANCAT-CD (lines 210-212)
     * @return the account group's record, or the {@code DEFAULT} group's record when the first is
     *         absent; its {@code interestRate} is DIS-INT-RATE and may be zero
     * @throws DisclosureGroupNotFoundException when the {@code DEFAULT} record is absent too
     */
    public DisclosureGroup resolve(String accountGroupId, TransactionCategoryBalance.Key categoryKey) {
        Objects.requireNonNull(categoryKey, "categoryKey");
        DisclosureGroup.Key accountGroupKey = new DisclosureGroup.Key(
                accountGroupId, categoryKey.transactionTypeCode(), categoryKey.categoryCode());
        Optional<DisclosureGroup> accountGroup = disclosureGroups.apply(accountGroupKey);
        if (accountGroup.isPresent()) {
            return accountGroup.get();
        }
        DisclosureGroup.Key defaultGroupKey = new DisclosureGroup.Key(
                DEFAULT_GROUP_ID, categoryKey.transactionTypeCode(), categoryKey.categoryCode());
        return disclosureGroups.apply(defaultGroupKey)
                .orElseThrow(() -> new DisclosureGroupNotFoundException(accountGroupKey, defaultGroupKey));
    }
}
