package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.AvaliacaoRequest;
import br.com.feedflow.avaliacao.application.AvaliacaoCommand;
import br.com.feedflow.avaliacao.domain.AvaliacaoInvalidaException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AvaliacaoRequestMapper {

    public AvaliacaoCommand mapear(AvaliacaoRequest request) {
        if (request == null) {
            throw new AvaliacaoInvalidaException("O corpo da requisição é obrigatório.");
        }

        int nota = mapearNota(request.nota());
        String descricao = mapearDescricao(request.descricao());
        return new AvaliacaoCommand(nota, descricao);
    }

    private int mapearNota(JsonNode nota) {
        if (nota == null || nota.isNull()) {
            throw new AvaliacaoInvalidaException("O campo nota é obrigatório.");
        }
        if (!nota.isIntegralNumber() || !nota.canConvertToInt()) {
            throw new AvaliacaoInvalidaException("A nota deve ser um número inteiro entre 0 e 10.");
        }
        return nota.intValue();
    }

    private String mapearDescricao(JsonNode descricao) {
        if (descricao == null || descricao.isNull()) {
            return null;
        }
        if (!descricao.isTextual()) {
            throw new AvaliacaoInvalidaException("A descrição deve ser um texto.");
        }
        return descricao.textValue();
    }
}
