package com.tubadev.receivables.infrastructure.rest;

import com.tubadev.receivables.infrastructure.rest.models.res.ContractResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("contracts")
@Tag(name = "Contract")
public interface ContractRestApi {

    @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an anticipation contract: terms, boletos and signature evidence")
    ResponseEntity<ContractResponse> get(@PathVariable String id);

    @GetMapping(value = "{id}/document", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the contract PDF, the same file sent to the customer on WhatsApp")
    ResponseEntity<byte[]> document(@PathVariable String id);
}
