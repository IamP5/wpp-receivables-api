package com.tubadev.receivables.infrastructure.rest.controllers;

import com.tubadev.receivables.application.contract.GetContract;
import com.tubadev.receivables.application.contract.GetContractDocument;
import com.tubadev.receivables.infrastructure.rest.ContractRestApi;
import com.tubadev.receivables.infrastructure.rest.models.res.ContractResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class ContractRestController implements ContractRestApi {

    private final GetContract getContract;
    private final GetContractDocument getContractDocument;

    public ContractRestController(final GetContract getContract, final GetContractDocument getContractDocument) {
        this.getContract = Objects.requireNonNull(getContract);
        this.getContractDocument = Objects.requireNonNull(getContractDocument);
    }

    @Override
    public ResponseEntity<ContractResponse> get(final String id) {
        record Input(String contractId) implements GetContract.Input {}
        return ResponseEntity.ok(this.getContract.execute(new Input(id), ContractResponse::new));
    }

    @Override
    public ResponseEntity<byte[]> document(final String id) {
        record Input(String contractId) implements GetContractDocument.Input {}
        final var document = this.getContractDocument.execute(new Input(id));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(document.filename()).build().toString())
                .body(document.content());
    }
}
