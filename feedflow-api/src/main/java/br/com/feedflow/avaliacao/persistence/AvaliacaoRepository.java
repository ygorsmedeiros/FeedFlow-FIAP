package br.com.feedflow.avaliacao.persistence;

import br.com.feedflow.avaliacao.domain.Avaliacao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvaliacaoRepository {

    Avaliacao salvar(Avaliacao avaliacao);

    Optional<Avaliacao> buscarPorId(UUID id);

    List<Avaliacao> buscarPorSemanaEIntervalo(
            String weekKey,
            Instant inicioInclusivo,
            Instant fimExclusivo);
}
