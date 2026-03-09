package com.invoiceautomation.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoiceautomation.backend.config.InvoiceLlmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiCompatibleInvoiceLlmClient implements InvoiceLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InvoiceLlmProperties properties;

    public OpenAiCompatibleInvoiceLlmClient(
            RestClient invoiceLlmRestClient,
            ObjectMapper objectMapper,
            InvoiceLlmProperties properties) {
        this.restClient = invoiceLlmRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String extractStructuredInvoiceJson(String prompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("LLM extraction is disabled.");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("LLM API key is not configured.");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new IllegalStateException("LLM model is not configured.");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", properties.getModel());
        requestBody.put("temperature", properties.getTemperature());
        requestBody.set("response_format", objectMapper.createObjectNode().put("type", "json_object"));

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", "You are an invoice extraction system."));
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", prompt));
        requestBody.set("messages", messages);

        JsonNode responseBody = restClient.post()
                .uri(properties.getChatCompletionsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        String content = responseBody == null
                ? null
                : responseBody.path("choices").path(0).path("message").path("content").asText(null);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM response did not contain extractable JSON content.");
        }

        return content;
    }
}
