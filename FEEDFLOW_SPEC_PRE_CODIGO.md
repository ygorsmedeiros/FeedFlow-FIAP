# FeedFlow — Especificação pré-codificação (arquitetura e regras principais aprovadas)

**Projeto:** FeedFlow — *Your feedback shapes better classes.*
**Data de referência:** 01/11/2026
**Prazo informado:** 17/11/2026
**Status:** arquitetura de três Lambdas, Java 17, classificação de urgência e descrição condicional aprovadas em 01/11/2026. Demais decisões sinalizadas como pendentes exigem confirmação.
**Fonte do escopo obrigatório:** `ADJT - Fase 4 - Tech Challenge_rev.pdf` (FIAP).
**Planejamento de execução:** `SPRINTS.md`.

## 1. Objetivo e fronteira do MVP

Receber avaliações de aulas por HTTP, armazenar em nuvem, avisar automaticamente um administrador sobre avaliações críticas e enviar um relatório semanal com médias e agrupamentos. Demonstrar tudo em AWS com funções serverless, segurança, monitoramento, deploy automatizado, documentação e vídeo.

**Obrigatório segundo o enunciado:** cloud; serverless com pelo menos 2 funções e responsabilidade única; `POST /avaliação` com `descricao` e `nota` inteira de 0 a 10; armazenamento e componentes de suporte; deploy automatizado; monitoramento; aviso automático de feedback crítico; e-mail de urgência com descrição, urgência e data de envio; relatório semanal com média, descrição, urgência, data de envio, quantidades por dia e urgência; segurança/governança; repositório público; vídeo; documentação.

**Fora do MVP (não exigido):** frontend, login de alunos, painel administrativo, upload de arquivos, GraalVM/native, reatividade, Kubernetes, microserviços adicionais, GraphQL, gRPC, Kafka e RabbitMQ.

## 2. Decisões técnicas propostas

| Área | Proposta | Situação |
|---|---|---|
| Java | JDK 17 no IntelliJ, Maven, compilação e AWS Lambda | Solicitado pelo usuário; validar ambiente |
| Framework HTTP | Quarkus 3.x estável e compatível com Java 17 e Lambda HTTP | Confirmar versão/extensão antes de gerar código |
| Build | Maven Wrapper, multi-módulo leve em repositório único | Organização proposta; detalhes a validar |
| API pública | API Gateway HTTP API → Lambda HTTP com Quarkus | Arquitetura aprovada; rota oficial ASCII `/avaliacao` |
| Persistência | DynamoDB on-demand, tabela de avaliações | DynamoDB aprovado; modo de cobrança a validar |
| Alertas | DynamoDB Streams → Lambda Java → SNS (e-mail confirmado) | Aprovado |
| Relatórios | EventBridge Scheduler → Lambda Java → DynamoDB → SNS | Aprovado |
| Infraestrutura | AWS SAM/CloudFormation (`template.yaml`) | Aprovado |
| CI/CD | GitHub Actions; OIDC para AWS; Java 17; deploy aprovado | Aprovado; permissões a validar |
| Observabilidade | CloudWatch Logs, métricas e alarmes básicos | Aprovado |
| Região AWS | `us-east-2` (Ohio), associada ao AWS Free Plan da conta | Aprovada para o ambiente de desenvolvimento |
| E-mail destinatário | Parâmetro de deploy; jamais hardcoded ou exposto em README público | PENDENTE |

**Risco principal a provar cedo:** empacotamento e execução real do Quarkus 3.x em Lambda Java 17 com API Gateway HTTP API. Não assumir que um endpoint local funcionando garante deploy. Criar uma prova mínima de build e integração antes de ampliar a implementação.

## 3. Arquitetura e responsabilidades

```text
Aluno / Postman / curl
        |
        v
API Gateway HTTP API (HTTPS, throttling)
        |
        v
Lambda 1 — FeedFlow API (Quarkus/Java 17)
  - valida requisição
  - calcula urgência
  - grava avaliação
        |
        v
DynamoDB (avaliações + GSI de semana)
        |
        +-- DynamoDB Streams (INSERT) --> Lambda 2 — Alertas
        |                                     |
        |                                     +--> SNS --> E-mail administrador
        |
EventBridge Scheduler (segunda-feira) --> Lambda 3 — Relatórios
                                              |
                                              +--> consulta DynamoDB
                                              +--> calcula média e agrupamentos
                                              +--> SNS --> E-mail administrador

GitHub Actions --> testes --> AWS SAM deploy --> stack AWS
CloudWatch <-- logs/métricas das Lambdas e API
```

- **Lambda 1:** somente API, validação, classificação e persistência.
- **Lambda 2:** somente detecção de evento crítico e publicação de alerta.
- **Lambda 3:** somente geração e publicação de relatório semanal.
- **SNS:** meio de entrega; não substitui a lógica das funções.
- **DynamoDB Streams:** entrega pelo menos uma vez; duplicatas são possíveis e precisam ser tratadas/documentadas.

## 4. Estrutura de repositório proposta

```text
FeedFlow/
  pom.xml                    # Maven aggregator/parent
  mvnw / mvnw.cmd / .mvn/
  api/                       # Quarkus REST + persistência
    pom.xml
    src/main/java/br/com/feedflow/...
    src/test/java/...
  alerts/                    # Lambda Java para eventos de stream
    pom.xml
    src/main/java/...
    src/test/java/...
  reports/                   # Lambda Java para relatório
    pom.xml
    src/main/java/...
    src/test/java/...
  infra/
    template.yaml            # AWS SAM
  .github/workflows/
    ci.yml
    deploy.yml
  docs/
    architecture.md
    functions.md
    deploy.md
    monitoring.md
    security.md
    test-evidence.md
    demo-script.md
  SPRINTS.md
  FEEDFLOW_SPEC_PRE_CODIGO.md
  README.md
  .gitignore
```

Um módulo `shared/` pode ser adicionado **somente se** compartilhar tipos de domínio realmente reduzir duplicação sem complicar o build. O projeto Maven é criado na raiz (não criar uma pasta `FeedFlow` dentro da existente). Não misturar Quarkus/Panache relacional com DynamoDB.

## 5. Contrato HTTP proposto

### `GET /health`

- **Resposta 200:** `{"status":"UP","application":"FeedFlow"}`.
- Verifica disponibilidade da aplicação; não comprova sozinho a conectividade com o DynamoDB.

### `POST /avaliacao` — rota oficial do projeto

**Decisão registrada na Sprint 5:** o enunciado original usa `/avaliação`, mas o Tech Lead oficializou exclusivamente o caminho ASCII `/avaliacao`. A rota Unicode e o proxy foram removidos do código e do SAM, mantendo esta observação para rastreabilidade da divergência.

**Request JSON:**

```json
{"descricao":"O conteúdo foi explicado muito rapidamente.","nota":2}
```

**Regras aprovadas:**

- `descricao`: **obrigatória somente para notas de 0 a 3** (`CRITICA`), não vazia após `trim`, de 1 a **1.000 caracteres**.
- Para notas **4 a 10** (`MEDIA` e `BAIXA`), `descricao` é opcional. Se ausente, nula ou composta apenas por espaços, **não inventar comentário do aluno**: armazenar `descricao` como ausente/`null` e retornar a mensagem **“Avaliação registrada sem comentário.”** no campo `mensagem` da resposta. No relatório, exibir **“Avaliação sem comentário”** como texto de apresentação quando a descrição for nula.
- Se uma avaliação não crítica incluir descrição, preservar o texto informado, com limite de **1.000 caracteres**.
- Rejeitar descrição acima de 1.000 caracteres para qualquer nota; rejeitar descrição ausente, nula ou vazia para nota crítica.
- `nota`: obrigatória, tipo inteiro JSON, de **0 a 10**, inclusive; rejeitar `2.5`, `"2"`, `null` e ausente.
- ID: UUID gerado pelo servidor.
- `createdAt`: instante UTC gerado pelo servidor.
- `urgencia`: calculada no servidor, não recebida do cliente.
- `Content-Type: application/json`.

**Resposta proposta 201:**

```json
{
  "id":"c0d271b2-8f27-4c47-9c88-cde15fb3fb2d",
  "descricao":"O conteúdo foi explicado muito rapidamente.",
  "nota":2,
  "urgencia":"CRITICA",
  "createdAt":"2026-11-01T21:00:00Z"
}
```


**Exemplo sem descrição (nota não crítica):**

**Requisição:**

```json
{"nota":8}
```

**Campos ilustrativos da resposta HTTP 201:**

```json
{"descricao":null,"nota":8,"urgencia":"BAIXA","mensagem":"Avaliação registrada sem comentário."}
```

> Os exemplos acima mostram somente os campos relevantes. A resposta final também incluirá `id` e `createdAt`. O texto padrão é uma confirmação do sistema, não uma opinião atribuída ao aluno.

**Erros propostos:** 400 para validação (mensagem sem dados sensíveis); 415 para tipo de mídia incorreto; 503 ou 500 controlado para indisponibilidade de persistência. Documentar os códigos efetivos e testar.

## 6. Classificação de urgência proposta

| Nota | Urgência | Gera e-mail crítico? |
|---|---|---|
| 0–3 | `CRITICA` | Sim |
| 4–6 | `MEDIA` | Não |
| 7–10 | `BAIXA` | Não |

A FIAP **não determina** faixas de urgência; esta é uma regra de negócio **aprovada para o FeedFlow**. Testar as fronteiras 0, 3, 4, 6, 7 e 10. Não receber urgência do cliente.

## 7. Modelo de dados DynamoDB proposto

Tabela `FeedFlowFeedback` (nome parametrizado por ambiente):

| Atributo | Exemplo | Finalidade |
|---|---|---|
| `id` (PK) | UUID | Identificador da avaliação |
| `descricao` | texto opcional (`null` para nota 4–10 sem comentário) | Conteúdo realmente informado pelo aluno; não persistir texto padrão como se fosse opinião |
| `nota` | 2 | Nota inteira |
| `urgencia` | `CRITICA` | Classificação derivada |
| `createdAt` | `2026-11-01T21:00:00Z` | Instante UTC da avaliação |
| `weekKey` | `SEMANA#2026-10-26` | Segunda-feira local da semana de referência |

**GSI proposta:** índice `byWeek`, partition key `weekKey`, sort key `createdAt`. A Lambda de relatório consulta somente a semana anterior, em vez de varrer a tabela inteira. `weekKey` deve ser calculada em `America/Sao_Paulo`, mas `createdAt` deve permanecer em UTC. Avaliar consistência eventual do GSI em testes imediatos após gravação.

- DynamoDB Streams habilitado com imagem `NEW_IMAGE` para identificar `INSERT`.
- Criptografia em repouso, IAM mínimo e nenhuma descrição sensível em logs.
- Política de retenção e exclusão de dados: **PENDENTE** (MVP usa apenas dados sintéticos).
- Para grande volume, revisar particionamento, limites de consulta e formato de relatório; o desenho atende inicialmente a um MVP de baixo volume.

## 8. E-mail de urgência

**Gatilho:** registro `INSERT` no DynamoDB Stream com `urgencia=CRITICA`.

**Assunto proposto:** `[FeedFlow] Avaliação crítica recebida`.

**Corpo mínimo:** descrição, urgência e **data/hora de envio do alerta**. Pode incluir também ID, nota e data/hora da avaliação para auditoria.

- A assinatura SNS por e-mail exige confirmação do destinatário.
- Não enviar alertas para `MEDIA` e `BAIXA`.
- Eventos podem ser entregues novamente; **exactly once não é garantido**. MVP: registrar risco, evitar efeitos colaterais antes de validar o evento e planejar idempotência por identificador de evento ou avaliação caso haja tempo. Não prometer ausência absoluta de e-mails duplicados.
- Revisar retentativas, destino de falhas e visibilidade dos erros no CloudWatch.

## 9. Relatório semanal

**Agendamento aprovado:** segunda-feira às 09:00 em `America/Sao_Paulo`, referente ao período da segunda anterior 00:00 (inclusive) até a segunda atual 00:00 (exclusive), no fuso local.

**Conteúdo mínimo:**

1. Intervalo analisado e data/hora de geração/envio.
2. Total de avaliações e **média aritmética das notas** (duas casas decimais).
3. Quantidade por dia da semana (incluindo dias com zero).
4. Quantidade por urgência (`CRITICA`, `MEDIA`, `BAIXA`).
5. Relação de avaliações com **descrição (ou “Avaliação sem comentário” quando ausente), urgência e data de envio da avaliação** (`createdAt` apresentado no fuso local). Se o termo 'data de envio' for interpretado como o envio do relatório, mostrar também esse timestamp separadamente.

**Semana vazia:** enviar relatório com total 0, contagens 0 e média `N/A` (não dividir por zero).

**Demonstração:** permitir invocação manual da Lambda na AWS sem esperar o próximo agendamento. O agendamento semanal deve permanecer ativo e comprovado.

**Limitação a documentar:** mensagens SNS têm limite de tamanho; uma lista integral de milhares de descrições não cabe em um e-mail SNS. Para a entrega, usar poucos dados de demonstração e verificar o tamanho do corpo; evolução futura pode gerar arquivo em S3 e enviar link ou usar outro canal.

## 10. Segurança, custo e operação

**Momento de preparar a AWS:** a conta AWS **não é necessária para as Sprints 0, 1 e 2**, que serão locais. Durante a Sprint 3, preparar acesso, região e orçamento; **antes da Sprint 4/deploy real**, a conta, MFA, permissões e alertas de custo são pré-requisitos obrigatórios. Os testes locais e a elaboração do template SAM podem começar sem uma conta AWS.

**Antes de criar recursos AWS:**

- Escolher conta e região; confirmar permissões e MFA.
- Criar AWS Budget e alertas; **alerta não é bloqueio automático de gastos**.
- Utilizar credenciais temporárias/perfis adequados para acesso local; não colocar access keys em Git, arquivos Markdown ou mensagens para agentes.
- Configurar GitHub Actions com OIDC e política de confiança restrita ao repositório/branch/ambiente.
- IAM por função: API escreve na tabela; Alertas lê stream e publica no SNS; Relatórios consulta tabela/GSI e publica no SNS; Scheduler apenas invoca a Lambda correta.
- API pública somente para demonstração, com HTTPS e limitação de taxa quando possível; não assumir que CORS é autenticação.
- Usar dados fictícios; evitar PII e descrições completas nos logs.
- Definir retenção de logs e alarmes de erro; revisar custo de CloudWatch, SNS, API Gateway, DynamoDB e EventBridge.
- Versionar infraestrutura no SAM; documentar deploy, rollback e exclusão de recursos.
- Não criar recursos pagos sem aprovação explícita.

## 11. Testes e evidências mínimas

| Cenário | Resultado esperado |
|---|---|
| `GET /health` | 200 + JSON correto |
| Nota 0, 3 | 201 + `CRITICA` |
| Nota 4, 6 | 201 + `MEDIA` |
| Nota 7, 10 | 201 + `BAIXA` |
| Nota -1, 11, decimal, texto, nula, ausente | 400 |
| Nota 0–3 com descrição vazia, ausente, nula ou acima de 1000 | 400 |
| Nota 4–10 com descrição ausente, nula ou em branco | 201; `descricao: null` e `mensagem: "Avaliação registrada sem comentário."`; relatório exibe “Avaliação sem comentário” |
| Nota 4–10 com descrição voluntária válida | 201; preservar descrição |
| Nota 4–10 com descrição acima de 1000 | 400 |
| Avaliação válida | Persistência DynamoDB e ID/timestamp coerentes |
| Avaliação crítica | SNS entrega e-mail com descrição, urgência e envio |
| Avaliação não crítica | Nenhum e-mail crítico |
| Relatório com notas 2, 5, 9 | Média 5,33; contagens corretas |
| Relatório sem avaliações | Total 0; média N/A |
| Fronteiras de semana e fuso | Sem avaliações duplicadas ou fora do intervalo |
| Falha de banco/SNS | Erro/log controlado e política de retry definida |
| Deploy | Build/testes + atualização reproduzível por GitHub Actions/SAM |
| Observabilidade | Logs, métricas, alarmes verificáveis |
| Entrega | Repositório público, README completo, vídeo e evidências |

**Evidência por sprint:** comando executado, resultado real, captura ou log sem segredos, commit associado, pendências e aprovação do Tech Lead. Não considerar testes 'passados' sem evidência verificável.

## 12. Riscos e decisões preventivas

1. **Quarkus/Lambda/Java 17:** validar empacotamento e API Gateway cedo, não apenas `quarkus:dev`.
2. **Rota HTTP:** provar `POST /avaliacao` na AWS e confirmar 404 para o caminho Unicode removido.
3. **DynamoDB GSI:** modelar antes do código; observar consistência eventual e fronteira de semana.
4. **E-mails SNS:** confirmar assinatura antes do teste; cuidar de tamanho e duplicatas.
5. **GitHub Actions OIDC:** configurar com antecedência; o deploy automatizado é requisito, não bônus.
6. **Custos AWS:** orçamento e aprovação antes do deploy; limpar stack ao final se apropriado.
7. **Prazo:** primeiro deploy cedo; não deixar CI/CD, documentação e vídeo para a véspera.
8. **Segurança:** endpoint público com proteção operacional mínima; somente dados sintéticos.

## 13. Sequência recomendada de planejamento e implementação

**Planejamento e preparação:**

- [x] Arquitetura, contrato HTTP, segurança, estrutura modular e estratégia de testes aprovados pelo Tech Lead.
- [x] Urgência, descrição condicional, período semanal, fuso e formato do relatório aprovados.
- [x] Região AWS definida como `us-east-2` (Ohio); destinatário permanece pendente e não deve ser registrado no repositório público.
- [ ] Confirmar que o enunciado permite backend sem frontend (não há frontend obrigatório no texto).
- [ ] Definir como demonstrar a rota `/avaliação` e seu eventual alias.
- [ ] Verificar manualmente `java -version`, `mvn -version`, `git status` e SDK do IntelliJ (JDK 17).
- [ ] Planejar AWS agora; criar/configurar conta, MFA e orçamento na Sprint 3 e obrigatoriamente antes do deploy da Sprint 4.
- [ ] Salvar esta especificação e `SPRINTS.md` na raiz do repositório.

**Implementação após aprovação:**

1. Gerar somente estrutura Maven multi-módulo, Quarkus API mínima, `GET /health`, testes e documentação inicial.
2. Executar testes e demonstrar build com Java 17.
3. Provar caminho de empacotamento Lambda/API Gateway antes de implementar muitas funcionalidades.
4. Implementar `POST /avaliação`, classificação e testes.
5. Implementar DynamoDB e infraestrutura SAM, seguido do primeiro deploy AWS.
6. Implementar alertas, relatórios, CI/CD, observabilidade e segurança.
7. Executar testes ponta a ponta, documentar, gravar vídeo e publicar.

**Regra:** cada sprint precisa de evidências e aprovação humana antes de ser marcada concluída.

## 14. Pendências de aprovação explícita

- [x] Java 17 aprovado para todo o projeto; [ ] confirmar JDK ativo no terminal, Maven e IntelliJ.
- [x] Arquitetura com três Lambdas, Quarkus na API, Java 17 e DynamoDB.
- [x] Repositório único e módulos Maven separados aprovados; [ ] validar empacotamento e build reais.
- [x] Urgência 0–3/4–6/7–10.
- [x] Descrição obrigatória de 1 a 1.000 caracteres somente para notas críticas; opcional nas demais, com mensagem padrão.
- [x] Período semanal segunda–domingo, fuso `America/Sao_Paulo` e relatório segunda às 09:00.
- [x] AWS região `us-east-2` (Ohio) para o ambiente de desenvolvimento.
- [ ] Destinatário configurável, com assinatura SNS confirmada.
- [x] GSI `byWeek` e formato do relatório detalhado aprovados; [ ] implementar e verificar consulta.
- [ ] Estratégia de rota `/avaliação` e alias `/avaliacao` se necessário.
- [ ] Acesso AWS, MFA, orçamento e autorização antes do primeiro deploy; não bloqueia desenvolvimento local.

**Nenhum item pendente deve ser marcado como aprovado automaticamente.**


## Registro de aprovação — 01/11/2026

Aprovados pelo Tech Lead: Java 17; repositório único com módulos Maven separados; Quarkus para API; três Lambdas; DynamoDB com índice semanal; urgência 0–3/4–6/7–10; descrição obrigatória apenas em notas críticas (máximo 1.000 caracteres); contrato HTTP com `201`/`400`/`500`; relatório da semana anterior enviado segunda-feira às 09h em `America/Sao_Paulo`; exibição **"Avaliação sem comentário"** quando a descrição é nula; IAM mínimo, sem credenciais versionadas, AWS SAM, GitHub Actions com OIDC, CloudWatch e testes automatizados.

**Não confundir decisão aprovada com trabalho implementado.** Permanecem pendentes: verificação efetiva do JDK 17, versão/extensões do Quarkus, teste da rota acentuada na AWS, destinatário, configuração final da conta AWS e toda execução de código/deploy. Nenhum commit ou push foi realizado nesta atualização.
