package com.tubadev.receivables.infrastructure.configuration.usecases;

import com.tubadev.receivables.application.contract.GetContract;
import com.tubadev.receivables.application.contract.GetContractDocument;
import com.tubadev.receivables.application.contract.IssueContract;
import com.tubadev.receivables.application.contract.impl.DefaultGetContract;
import com.tubadev.receivables.application.contract.impl.DefaultGetContractDocument;
import com.tubadev.receivables.application.contract.impl.DefaultIssueContract;
import com.tubadev.receivables.domain.contract.ContractDocumentGateway;
import com.tubadev.receivables.domain.contract.ContractGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ContractUseCaseConfig {

    @Bean
    IssueContract issueContract(final ContractGateway contractGateway, final ContractDocumentGateway contractDocumentGateway) {
        return new DefaultIssueContract(contractGateway, contractDocumentGateway);
    }

    @Bean
    GetContract getContract(final ContractGateway contractGateway) {
        return new DefaultGetContract(contractGateway);
    }

    @Bean
    GetContractDocument getContractDocument(final ContractGateway contractGateway) {
        return new DefaultGetContractDocument(contractGateway);
    }
}
