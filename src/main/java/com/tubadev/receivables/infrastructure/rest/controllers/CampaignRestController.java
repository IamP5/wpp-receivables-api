package com.tubadev.receivables.infrastructure.rest.controllers;

import com.tubadev.receivables.application.campaign.CreateCampaign;
import com.tubadev.receivables.application.campaign.GetCampaign;
import com.tubadev.receivables.application.campaign.StartCampaign;
import com.tubadev.receivables.infrastructure.rest.CampaignRestApi;
import com.tubadev.receivables.infrastructure.rest.models.req.CreateCampaignRequest;
import com.tubadev.receivables.infrastructure.rest.models.res.CampaignResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.CreateCampaignResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.StartCampaignResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
public class CampaignRestController implements CampaignRestApi {

    private final CreateCampaign createCampaign;
    private final GetCampaign getCampaign;
    private final StartCampaign startCampaign;

    public CampaignRestController(final CreateCampaign createCampaign, final GetCampaign getCampaign, final StartCampaign startCampaign) {
        this.createCampaign = Objects.requireNonNull(createCampaign);
        this.getCampaign = Objects.requireNonNull(getCampaign);
        this.startCampaign = Objects.requireNonNull(startCampaign);
    }

    @Override
    public ResponseEntity<CreateCampaignResponse> create(final CreateCampaignRequest req) {
        record Input(String name, String templateName, String templateLanguage, List<String> templateParameters, List<String> customerIds)
                implements CreateCampaign.Input {}

        final var res = this.createCampaign.execute(
                new Input(req.name(), req.templateName(), req.templateLanguage(), req.templateParameters(), req.customerIds()),
                CreateCampaignResponse::new);
        return ResponseEntity.created(URI.create("/campaigns/" + res.campaignId())).body(res);
    }

    @Override
    public ResponseEntity<StartCampaignResponse> start(final String id) {
        record Input(String campaignId) implements StartCampaign.Input {}
        return ResponseEntity.accepted().body(this.startCampaign.execute(new Input(id), StartCampaignResponse::new));
    }

    @Override
    public ResponseEntity<CampaignResponse> get(final String id) {
        record Input(String campaignId) implements GetCampaign.Input {}
        return ResponseEntity.ok(this.getCampaign.execute(new Input(id), CampaignResponse::new));
    }
}
