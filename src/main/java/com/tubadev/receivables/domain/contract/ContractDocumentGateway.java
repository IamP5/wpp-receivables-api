package com.tubadev.receivables.domain.contract;

/**
 * Port to the document generation: renders the contract (terms, boletos, signature evidence) as a PDF.
 */
public interface ContractDocumentGateway {

    ContractDocument render(Contract aContract);
}
