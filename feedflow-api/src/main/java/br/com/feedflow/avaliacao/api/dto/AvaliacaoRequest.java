package br.com.feedflow.avaliacao.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AvaliacaoRequest(JsonNode nota, JsonNode descricao) {
}
