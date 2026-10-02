package com.tubadev.receivables.infrastructure.rest;

import com.tubadev.receivables.infrastructure.rest.models.req.CreateCampaignRequest;
import com.tubadev.receivables.infrastructure.rest.models.res.CampaignResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.CreateCampaignResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.StartCampaignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("campaigns")
@Tag(name = "Campaign")
public interface CampaignRestApi {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a campaign (draft) that sends an approved template to customers")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created successfully"),
            @ApiResponse(responseCode = "422", description = "A validation error was observed"),
    })
    ResponseEntity<CreateCampaignResponse> create(@RequestBody @Valid CreateCampaignRequest req);

    @PostMapping(value = "{id}/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start a campaign; the dispatch runs asynchronously")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Started"),
            @ApiResponse(responseCode = "404", description = "Campaign not found"),
            @ApiResponse(responseCode = "422", description = "Invalid status transition"),
    })
    ResponseEntity<StartCampaignResponse> start(@PathVariable String id);

    @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a campaign with its dispatch stats")
    ResponseEntity<CampaignResponse> get(@PathVariable String id);
}
