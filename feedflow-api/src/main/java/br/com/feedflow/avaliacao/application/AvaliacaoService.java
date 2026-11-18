package br.com.feedflow.avaliacao.application;

import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.AvaliacaoInvalidaException;
import br.com.feedflow.avaliacao.domain.Urgencia;
import br.com.feedflow.avaliacao.persistence.AvaliacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AvaliacaoService {

    private static final int TAMANHO_MAXIMO_DESCRICAO = 1_000;
    private final AvaliacaoRepository repository;

    @Inject
    public AvaliacaoService(AvaliacaoRepository repository) {
        this.repository = repository;
    }

    public Avaliacao registrar(AvaliacaoCommand command) {
        return repository.salvar(preparar(command));
    }

    public Avaliacao preparar(AvaliacaoCommand command) {
        if (command == null) {
            throw new AvaliacaoInvalidaException("Os dados da avaliação são obrigatórios.");
        }

        Urgencia urgencia = Urgencia.classificar(command.nota());
        String descricao = normalizarDescricao(command.descricao(), urgencia);

        return new Avaliacao(
                UUID.randomUUID(),
                command.nota(),
                urgencia,
                descricao,
                Instant.now());
    }

    private String normalizarDescricao(String descricao, Urgencia urgencia) {
        if (descricao != null && descricao.length() > TAMANHO_MAXIMO_DESCRICAO) {
            throw new AvaliacaoInvalidaException("A descrição deve ter no máximo 1000 caracteres.");
        }

        if (descricao == null || descricao.isBlank()) {
            if (urgencia == Urgencia.CRITICA) {
                throw new AvaliacaoInvalidaException("A descrição é obrigatória para avaliações críticas.");
            }
            return null;
        }

        return descricao;
    }
}
