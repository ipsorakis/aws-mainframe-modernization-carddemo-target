package com.carddemo.interest.application;

import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.application.port.CardXrefRepository;
import com.carddemo.interest.application.port.DisclosureGroupRepository;
import com.carddemo.interest.application.port.TransactionCategoryBalanceReader;
import com.carddemo.interest.application.port.TransactionWriter;

import java.util.Objects;

/**
 * Monthly interest calculation use case: the Java counterpart of the CBACT04C PROCEDURE DIVISION,
 * run by the INTCALC job ({@code PARM='2022071800'}) in the MONTHLY-InterestCalculation folder.
 */
public class InterestCalculationJob {

    private final TransactionCategoryBalanceReader categoryBalances;
    private final AccountRepository accounts;
    private final CardXrefRepository cardXrefs;
    private final DisclosureGroupRepository disclosureGroups;
    private final TransactionWriter transactions;

    public InterestCalculationJob(
            TransactionCategoryBalanceReader categoryBalances,
            AccountRepository accounts,
            CardXrefRepository cardXrefs,
            DisclosureGroupRepository disclosureGroups,
            TransactionWriter transactions) {
        this.categoryBalances = Objects.requireNonNull(categoryBalances);
        this.accounts = Objects.requireNonNull(accounts);
        this.cardXrefs = Objects.requireNonNull(cardXrefs);
        this.disclosureGroups = Objects.requireNonNull(disclosureGroups);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * Runs one monthly interest cycle.
     *
     * @param parmDate the JCL {@code PARM-DATE} (PIC X(10)), used as the transaction ID prefix
     */
    public void run(String parmDate) {
        // TODO(DM-6, DM-7, DM-8): port the CBACT04C main loop:
        //  for each TRAN-CAT-BAL record from categoryBalances.readAllInKeyOrder()   (1000-TCATBALF-GET-NEXT)
        //    if TRANCAT-ACCT-ID != last account id:
        //      if not first record: post totals to the previous account              (1050-UPDATE-ACCOUNT, DM-7)
        //      reset WS-TOTAL-INT; remember account id
        //      accounts.findById(acctId)                                              (1100-GET-ACCT-DATA)
        //      cardXrefs.findByAccountId(acctId)                                      (1110-GET-XREF-DATA)
        //    rate = disclosureGroups.findByKey(ACCT-GROUP-ID, TYPE-CD, CAT-CD)
        //           falling back to group 'DEFAULT'                                   (1200 / 1200-A, DM-6)
        //    if rate != 0:
        //      monthly interest = balance * rate / 1200; add to WS-TOTAL-INT          (1300-COMPUTE-INTEREST, DM-6)
        //      transactions.write(interest transaction)                               (1300-B-WRITE-TX, DM-8)
        //      fees                                                                   (1400-COMPUTE-FEES, DM-9)
        //  at end of file: post totals to the last account                           (1050-UPDATE-ACCOUNT, DM-7)
        throw new UnsupportedOperationException("CBACT04C port not implemented yet (DM-6, DM-7, DM-8)");
    }
}
