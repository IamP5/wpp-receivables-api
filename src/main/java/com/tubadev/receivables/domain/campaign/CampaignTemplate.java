package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.ValueObject;
import com.tubadev.receivables.domain.message.MessageContent;

import java.util.List;

public record CampaignTemplate(String name, String languageCode, List<TemplateParameter> parameters) implements ValueObject {

    public CampaignTemplate {
        this.assertArgumentNotEmpty(name, "'template.name' should not be empty");
        this.assertArgumentNotEmpty(languageCode, "'template.languageCode' should not be empty");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public static CampaignTemplate of(final String name, final String languageCode, final List<String> rawParameters) {
        final var params = rawParameters == null ? List.<TemplateParameter>of()
                : rawParameters.stream().map(TemplateParameter::parse).toList();
        return new CampaignTemplate(name, languageCode, params);
    }

    public MessageContent.Template renderFor(final RecipientContext ctx) {
        return new MessageContent.Template(
                name,
                languageCode,
                parameters.stream().map(p -> p.resolve(ctx)).toList()
        );
    }

    public List<String> rawParameters() {
        return parameters.stream().map(TemplateParameter::raw).toList();
    }
}
