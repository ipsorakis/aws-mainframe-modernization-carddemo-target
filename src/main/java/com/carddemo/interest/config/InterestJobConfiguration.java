package com.carddemo.interest.config;

import com.carddemo.interest.adapter.persistence.PlaceholderAccountRepository;
import com.carddemo.interest.adapter.persistence.PlaceholderCardXrefRepository;
import com.carddemo.interest.adapter.persistence.PlaceholderDisclosureGroupRepository;
import com.carddemo.interest.adapter.persistence.PlaceholderTransactionCategoryBalanceReader;
import com.carddemo.interest.adapter.persistence.PlaceholderTransactionWriter;
import com.carddemo.interest.application.InterestCalculationJob;
import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.application.port.CardXrefRepository;
import com.carddemo.interest.application.port.DisclosureGroupRepository;
import com.carddemo.interest.application.port.TransactionCategoryBalanceReader;
import com.carddemo.interest.application.port.TransactionWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free application and adapter classes into the Spring context. */
@Configuration(proxyBeanMethods = false)
public class InterestJobConfiguration {

    @Bean
    TransactionCategoryBalanceReader transactionCategoryBalanceReader() {
        return new PlaceholderTransactionCategoryBalanceReader();
    }

    @Bean
    AccountRepository accountRepository() {
        return new PlaceholderAccountRepository();
    }

    @Bean
    CardXrefRepository cardXrefRepository() {
        return new PlaceholderCardXrefRepository();
    }

    @Bean
    DisclosureGroupRepository disclosureGroupRepository() {
        return new PlaceholderDisclosureGroupRepository();
    }

    @Bean
    TransactionWriter transactionWriter() {
        return new PlaceholderTransactionWriter();
    }

    @Bean
    InterestCalculationJob interestCalculationJob(
            TransactionCategoryBalanceReader categoryBalances,
            AccountRepository accounts,
            CardXrefRepository cardXrefs,
            DisclosureGroupRepository disclosureGroups,
            TransactionWriter transactions) {
        return new InterestCalculationJob(categoryBalances, accounts, cardXrefs, disclosureGroups, transactions);
    }
}
