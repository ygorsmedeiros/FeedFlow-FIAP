package br.com.feedflow.avaliacao.application;

import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.AvaliacaoInvalidaException;
import br.com.feedflow.avaliacao.domain.Urgencia;
import br.com.feedflow.avaliacao.persistence.AvaliacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvaliacaoServiceTest {

    private AvaliacaoRepository repository;
    private AvaliacaoService service;

    @BeforeEach
    void setUp() {
        repository = mock(AvaliacaoRepository.class);
        when(repository.salvar(any(Avaliacao.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AvaliacaoService(repository);
    }

    @ParameterizedTest
    @CsvSource({
            "0, CRITICA",
            "3, CRITICA",
            "4, MEDIA",
            "6, MEDIA",
            "7, BAIXA",
            "10, BAIXA"
    })
    void deveClassificarNotasNosLimites(int nota, Urgencia urgenciaEsperada) {
        String descricao = nota <= 3 ? "Comentário crítico" : null;

        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(nota, descricao));

        assertEquals(urgenciaEsperada, avaliacao.urgencia());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void deveRejeitarNotaForaDoIntervalo(int nota) {
        assertThrows(AvaliacaoInvalidaException.class,
                () -> service.preparar(new AvaliacaoCommand(nota, "Descrição")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void deveRejeitarAvaliacaoCriticaSemDescricaoUtil(String descricao) {
        assertThrows(AvaliacaoInvalidaException.class,
                () -> service.preparar(new AvaliacaoCommand(3, descricao)));
    }

    @Test
    void deveAceitarDescricaoComMilCaracteres() {
        String descricao = "a".repeat(1_000);

        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(0, descricao));

        assertEquals(descricao, avaliacao.descricao());
    }

    @Test
    void deveRejeitarDescricaoComMaisDeMilCaracteres() {
        String descricao = "a".repeat(1_001);

        assertThrows(AvaliacaoInvalidaException.class,
                () -> service.preparar(new AvaliacaoCommand(10, descricao)));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 7})
    void deveAceitarAvaliacaoNaoCriticaSemDescricao(int nota) {
        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(nota, null));

        assertNull(avaliacao.descricao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t  "})
    void deveNormalizarDescricaoEmBrancoNaoCriticaParaNulo(String descricao) {
        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(6, descricao));

        assertNull(avaliacao.descricao());
    }

    @Test
    void devePreservarDescricaoInformadaSemRemoverEspacos() {
        String descricao = "  Comentário preservado  ";

        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(8, descricao));

        assertEquals(descricao, avaliacao.descricao());
    }

    @Test
    void deveGerarIdETimestampUtcNoServidor() {
        Instant antes = Instant.now();

        Avaliacao avaliacao = service.preparar(new AvaliacaoCommand(5, null));

        assertNotNull(avaliacao.id());
        assertNotNull(avaliacao.criadoEm());
        assertTrue(!avaliacao.criadoEm().isBefore(antes));
        assertTrue(avaliacao.criadoEm().toString().endsWith("Z"));
    }

    @Test
    void deveRetornarSomenteAvaliacaoConfirmadaPeloRepositorio() {
        Avaliacao persistida = service.registrar(new AvaliacaoCommand(5, null));

        verify(repository).salvar(persistida);
        assertEquals(Urgencia.MEDIA, persistida.urgencia());
    }
}
