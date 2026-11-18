# FeedFlow — Plano de Sprints e Checklist de Entrega

> **Projeto:** FeedFlow — *Your feedback shapes better classes.*
> **Desafio:** FIAP — Tech Challenge, Fase 4
> **Prazo informado:** 17/11/2026
> **Stack definida:** Java **17**, Quarkus 3.x compatível, Maven Wrapper, AWS (Lambda, API Gateway, DynamoDB, DynamoDB Streams, SNS, EventBridge Scheduler, CloudWatch), AWS SAM, GitHub Actions.
> **IDE:** IntelliJ IDEA | **Ambiente de desenvolvimento:** Java 17 e Maven Wrapper
> **Repositório de entrega:** https://github.com/ygorsmedeiros/FeedFlow-FIAP
> **Status inicial:** planejamento; nenhuma sprint técnica foi validada ainda.

## Status real da entrega (resumo)

| Requisito do PDF | Estado | Evidência |
|---|---|---|
| Serverless, mínimo 2 funções, responsabilidade única | Pronto | 3 Lambdas em `template.yaml` |
| `POST /avaliacao`, urgência, validação | Pronto | `feedflow-api` + testes |
| Banco e componentes de suporte | Pronto (template) | DynamoDB, SNS, SQS, Scheduler |
| Notificação crítica por e-mail | Código pronto; **falta confirmar assinatura após deploy** | parâmetro `AdminEmail` |
| Relatório semanal com média | Código pronto; **falta teste real na AWS** | `feedflow-reports` |
| Deploy automatizado | Pronto (workflow); **falta configurar Environment e rodar** | `.github/workflows/deploy.yml` |
| Monitoramento | Pronto (alarmes + dashboard no template); **falta ver no console** | `template.yaml` |
| Documentação (arquitetura, deploy, monitoramento, funções) | Pronto | `README.md` |
| Roteiro do vídeo | Pronto | `docs/demo-script.md` |
| Deploy real e teste ponta a ponta | **Pendente (depende da conta AWS)** | — |
| Vídeo | **Pendente** | — |
| Repositório público | **Pendente** | — |

## 0. Como usar este documento

- `[ ]` pendente; `[x]` concluído **com evidência**; `[-]` bloqueado ou não aplicável (explicar o motivo).
- **Não marcar como concluído sem evidência verificável.** Validar com testes, execução local, evidência na AWS ou inspeção do GitHub.
- Ao terminar cada sprint, registrar: **data, commit, testes executados, evidências, problemas e decisão de aprovação**.
- Mudanças de arquitetura devem ter impacto explicado e aprovação antes de alterar o escopo.
- Não realizar commit, push, deploy ou criar recursos pagos sem autorização explícita.
- Este arquivo deve permanecer na **raiz do repositório**, versionado junto com o código.
- **Fonte dos requisitos obrigatórios:** enunciado `ADJT - Fase 4 - Tech Challenge_rev.pdf` da FIAP. Escolhas de arquitetura, critérios de urgência, códigos HTTP e ferramentas são **decisões do projeto**, não imposições do enunciado.

## 1. Escopo e decisões de arquitetura

### Requisitos obrigatórios da FIAP

- [ ] Aplicação funcionando em **ambiente de nuvem**.
- [ ] Arquitetura **serverless** com **pelo menos duas funções** e responsabilidades distintas.
- [ ] Recebimento de avaliações de aulas: `POST /avaliação`, com `descricao` (string) e `nota` (inteiro de 0 a 10).
- [ ] Banco de dados e componentes de suporte configurados.
- [ ] **Deploy automatizado** dos componentes atualizáveis.
- [ ] Monitoramento da aplicação.
- [ ] Notificações automáticas aos administradores para feedbacks críticos.
- [ ] E-mail de urgência contendo **descrição, urgência e data de envio**.
- [ ] Relatório **semanal** com **média das avaliações**.
- [ ] Relatório contendo **descrição, urgência, data de envio, quantidade por dia e quantidade por urgência**.
- [ ] Configurações de segurança e governança de acesso demonstradas.
- [ ] Repositório **público** com código-fonte.
- [ ] Vídeo demonstrando a aplicação funcionando, as funções serverless e as configurações da nuvem.
- [ ] Documentação: arquitetura, instruções de deploy, monitoramento, funções, segurança e componentes escolhidos.

### Arquitetura aprovada (01/11/2026)

1. **Função 1 — API:** API Gateway → AWS Lambda com Quarkus → DynamoDB; recebe, valida e grava avaliações.
2. **Função 2 — Alertas:** DynamoDB Streams → AWS Lambda Java → Amazon SNS; notifica sobre avaliações críticas.
3. **Função 3 — Relatório:** EventBridge Scheduler → AWS Lambda Java → DynamoDB → Amazon SNS; agrega avaliações da semana anterior e envia e-mail.
4. **Infraestrutura:** AWS SAM (template versionado), IAM de menor privilégio, CloudWatch e automação de deploy via GitHub Actions com OIDC.
5. **Organização aprovada:** repositório único com módulos Maven separados para API Quarkus e duas funções Java 17; validar build e empacotamento Lambda antes de expandir.

### Decisões de negócio aprovadas e pendentes

- [x] **Classificação aprovada:** notas 0–3 = `CRITICA`; 4–6 = `MEDIA`; 7–10 = `BAIXA`. Os limites são decisão do FeedFlow, não do enunciado.
- [x] **Descrição condicional aprovada:** para notas 0–3, `descricao` obrigatória, não vazia após `trim`, de 1 a 1.000 caracteres; para notas 4–10, opcional. Quando ausente ou em branco para notas não críticas, **persistir `descricao` como `null`** e retornar **“Avaliação registrada sem comentário.”** no campo `mensagem` da resposta. No relatório, exibir **“Avaliação sem comentário”** como substituto visual, sem inventar comentário do aluno. Quando informada, respeitar o limite de 1.000 caracteres.
- [x] **Arquitetura aprovada:** três Lambdas com responsabilidades distintas; Java 17, Quarkus na API, DynamoDB, DynamoDB Streams, SNS, EventBridge Scheduler, AWS SAM e GitHub Actions.
- [x] **AWS adiada:** conta, MFA, orçamento e deploy não bloqueiam as Sprints 0–2. Preparar a conta preferencialmente ao iniciar a Sprint 3 e obrigatoriamente antes do primeiro deploy da Sprint 4.
- [x] **Região da Sprint 4:** `us-east-2` (Ohio), conforme decisão informada pelo Tech Lead em 02/11/2026.
- [ ] Definir o destinatário de notificações e relatórios via configuração, sem e-mail fixo no código.
- [x] Definir semana de referência: segunda a domingo, relatório às segundas 09h (`America/Sao_Paulo`), timestamps armazenados em UTC.
- [x] Diferenciar data de criação da avaliação e data de geração do alerta na mensagem.
- [x] Oficializar exclusivamente `POST /avaliacao`; rota Unicode, proxy e output removidos na Sprint 5 por decisão do Tech Lead.
- [x] Semana sem avaliações: enviar relatório com total 0, contagens 0 e média não aplicável.
- [ ] Documentar riscos de processamento duplicado de eventos e estratégia mínima de idempotência.

---

## Sprint 0 — Preparação, escopo e segurança inicial

**Objetivo:** deixar o ambiente e as decisões principais prontos antes de gerar código.

- [x] Criar pasta local `FeedFlow`.
- [x] Inicializar Git, configurar branch `main` e enviar primeiro commit ao GitHub (push confirmado no terminal).
- [ ] Confirmar `git status`, `git remote -v` e o diretório correto no IntelliJ e no terminal.
- [ ] Configurar IntelliJ Project SDK = **JDK 17** e Maven JVM = **JDK 17**.
- [ ] Confirmar no terminal `java -version`, `mvn -version` e `git status` (não presumir que o Java 17 está ativo apenas por estar instalado).
- [ ] Definir `maven.compiler.release=17` ou configuração equivalente e documentar uso do JDK 17.
- [ ] Escolher versão estável e compatível do Quarkus 3.x; validar extensões de REST, testes e integração AWS Lambda.
- [ ] Registrar decisões arquiteturais aprovadas e contrato da API antes da implementação; salvar esta versão do plano no repositório.
- [x] Regras de negócio, fuso, semana de referência e formato do relatório aprovados; a implementação e seus testes continuam pendentes.
- [ ] Planejar a região AWS, as permissões e o procedimento de criação/configuração da conta; **não é necessário ter conta ativa nesta sprint**.
- [ ] Registrar a regra: configurar MFA e AWS Budget/alertas **antes** de criar recursos da Sprint 4; alertas de custo não bloqueiam cobranças.
- [ ] Planejar IAM de menor privilégio e política de segredos (sem chaves no GitHub).
- [ ] Documentar as regras de trabalho e os critérios de revisão técnica do projeto.
- [ ] Versionar este `SPRINTS.md` e `.gitignore`.

**Aceite:** ambiente Java 17 comprovado, arquitetura e regras de negócio registradas, checklist versionado e estratégia de custo/segurança definida. **Conta AWS não é critério de aceite desta sprint.**
**Evidência:** comandos, `git status`, decisões registradas e revisão humana.
**Registro:** Data: ____ | Commit: ____ | Testes/evidências: ____ | Aprovado: ____

## Sprint 1 — Fundação da aplicação Quarkus

**Objetivo:** API mínima executável e testada localmente.

- [ ] Criar estrutura Maven na raiz do repositório, sem outra pasta `FeedFlow` aninhada.
- [ ] Adicionar Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`).
- [ ] Configurar Java 17 no `pom.xml` e no IntelliJ; impedir dependência acidental de Java 25.
- [ ] Criar aplicação Quarkus e pacote-base `br.com.feedflow`.
- [ ] Criar `GET /health` retornando `200` e `{"status":"UP","application":"FeedFlow"}`.
- [ ] Criar teste automatizado de status e JSON.
- [ ] Atualizar `.gitignore` e README inicial com comandos Windows.
- [ ] Validar `./mvnw.cmd clean test` no PowerShell (`.\mvnw.cmd clean test`).
- [ ] Executar `./mvnw.cmd quarkus:dev` e chamar `http://localhost:8080/health`.
- [ ] Revisar dependências e compatibilidade com o adaptador Quarkus/AWS Lambda antes de consolidar a estrutura.

**Aceite:** compilação e testes passando com JDK 17; endpoint respondendo localmente; estrutura compreensível.
**Evidência:** saída dos testes e resposta HTTP.
**Registro:** Data: ____ | Commit: ____ | Testes/evidências: ____ | Aprovado: ____

## Sprint 2 — Contrato e regras de avaliações

**Objetivo:** receber avaliações com validação e regras de negócio, inicialmente sem depender da AWS.

- [ ] Definir contrato HTTP e exemplos válidos/inválidos no README ou OpenAPI.
- [x] Implementar o contrato de avaliação; caminho oficial consolidado como `POST /avaliacao` por decisão do Tech Lead na Sprint 5.
- [ ] Validar `descricao` **obrigatória apenas se nota 0–3**: texto não vazio após `trim`, com até 1.000 caracteres.
- [ ] Para nota 4–10, aceitar `descricao` ausente, nula ou em branco, **persistir `null`** e retornar **`mensagem: "Avaliação registrada sem comentário."`**; no relatório, mostrar “Avaliação sem comentário” sem adulterar o registro.
- [ ] Para nota 4–10, preservar descrição informada (até 1.000 caracteres) e rejeitar texto acima do limite.
- [ ] Rejeitar nota crítica com descrição ausente, nula, em branco ou acima de 1.000 caracteres (HTTP 400).
- [ ] Validar `nota` obrigatória, **inteira**, entre 0 e 10 (inclusive).
- [ ] Retornar erro 400 claro e consistente para entradas inválidas.
- [x] Contrato de sucesso definido: HTTP 201, com ID, nota, urgência, descrição, criadoEm e mensagem; falta implementação e validação.
- [ ] Implementar classificação de urgência aprovada e testá-la nos limites (0, 3, 4, 6, 7, 10).
- [ ] Gerar ID único e timestamp UTC no servidor; não confiar em campos críticos enviados pelo cliente.
- [ ] Separar lógica de negócio do adaptador HTTP e da persistência, sem camadas excessivas.
- [ ] Criar testes de contrato, validação, classificação e falhas.

**Aceite:** testes comprovam entradas válidas/inválidas e classificação; endpoint executa localmente com persistência simulada quando necessário.
**Evidência:** testes e chamadas HTTP reproduzíveis.
**Registro:** Data: ____ | Commit: ____ | Testes/evidências: ____ | Aprovado: ____

## Sprint 3 — Persistência no DynamoDB

**Objetivo:** armazenar avaliações com consulta eficiente para o relatório semanal.

- [x] Definir tabela, chaves, atributos, datas e padrão de consulta semanal.
- [x] Evitar depender de varredura total da tabela para o relatório, ou justificar explicitamente a limitação do MVP.
- [x] Criar repositório DynamoDB usando AWS SDK compatível com Java 17; usar mocks/DynamoDB Local enquanto a conta AWS não estiver preparada.
- [x] Salvar `id`, `descricao` (atributo ausente para nota 4–10 sem comentário), `nota`, `urgencia`, `createdAt` e `weekKey` para consulta semanal.
- [x] Garantir que a API retorne erro controlado se a persistência falhar.
- [x] Criar testes de persistência com mocks e DynamoDB Local e testes do fluxo API → repositório.
- [ ] Definir criptografia, política de retenção e acesso à tabela; não registrar descrições sensíveis nos logs.
- [x] Planejar DynamoDB Streams habilitado com `NEW_IMAGE` para eventos de inserção.

**Aceite:** avaliações persistidas e recuperáveis; consultas por período verificadas; testes passando.
**Evidência:** teste de integração e registro consultável (sem expor dados pessoais).
**Registro:** Data: 02/11/2026 | Commit: não realizado | Testes/evidências: 46 testes rápidos + 7 testes reais com DynamoDB Local; POST 201 confirmado e registro consultado após encerrar a API | Aprovado: sim, informado pelo Tech Lead no início da Sprint 4

## Sprint 4 — Primeiro deploy AWS (API + banco)

**Objetivo:** comprovar cedo que a arquitetura Java/Quarkus realmente funciona em nuvem.

- [ ] Confirmar MFA antes do deploy. Conta, região `us-east-2`, perfil temporário e orçamento de US$ 5 foram informados pelo Tech Lead; nenhum recurso foi criado nesta execução.
- [x] Verificar AWS CLI 2.39.3 e AWS SAM CLI 1.171.0; identidade temporária `AccountFullAccessRole` e região `us-east-2` confirmadas pelo perfil informado pelo Tech Lead.
- [x] Criar `template.yaml` versionado para API Gateway, Lambda API, DynamoDB, IAM e logs.
- [x] Integrar Quarkus à Lambda usando `quarkus-amazon-lambda-http`, handler JVM e artefato `function.zip`, com bytecode e runtime configurados para Java 17; execução real permanece pendente.
- [x] Configurar runtime Lambda **Java 17**, arquitetura `x86_64`, 512 MB, timeout de 15 segundos e variáveis de ambiente.
- [x] Configurar permissões mínimas para `PutItem`/`GetItem` na tabela, `Query` somente no índice `byWeek` e escrita no log group exato.
- [x] Executar localmente `sam validate --lint` e `sam build` com sucesso.
- [ ] Executar deploy parametrizado para o ambiente de desenvolvimento somente após revisão e autorização explícita.
- [ ] Testar `GET /health` e `POST /avaliacao` na URL pública do API Gateway.
- [ ] Confirmar persistência real no DynamoDB e conferir logs CloudWatch.
- [ ] Validar URL/rota com acento e documentar qualquer alternativa necessária.
- [x] Registrar custos estimados, recursos propostos, riscos, rollback e procedimento autorizado de remoção da stack.

**Aceite:** API pública responde, grava dados no DynamoDB e é reproduzível a partir do template.
**Evidência:** URL de teste, respostas HTTP, logs e recursos AWS (sem divulgar segredos).
**Registro:** Data: 02/11/2026 | Commit: não realizado | Stack/URL: não criada; deploy proibido nesta execução | Evidências locais: 46 testes rápidos + 7 testes reais com DynamoDB Local, `sam validate --lint` e `sam build` | Aprovado: pendente de revisão do Tech Lead

## Sprint 5 — Função serverless de alertas críticos

**Objetivo:** enviar e-mail automaticamente quando chegar uma avaliação crítica.

- [x] Criar função **separada** para alertas, com responsabilidade única.
- [x] Habilitar DynamoDB Streams e configurar gatilho de eventos de inserção.
- [x] Filtrar avaliações críticas segundo a regra aprovada.
- [x] Criar tópico SNS sem endereço fictício no template.
- [ ] Criar a assinatura com endereço real aprovado e **confirmá-la** na caixa de entrada após o deploy.
- [x] Preparar mensagem SNS contendo **ID, nota, descrição, urgência, criação e geração do alerta**; envio por e-mail depende de assinatura real confirmada.
- [x] Não enviar mensagem para notas médias/baixas.
- [x] Configurar IAM mínimo para consumir stream e publicar no SNS.
- [x] Tratar falhas, retentativas e risco de notificações duplicadas; destino SQS configurado para descarte.
- [x] Criar testes para eventos críticos, não críticos e payloads inesperados.
- [ ] Fazer teste ponta a ponta na AWS com avaliação crítica e comprovar recebimento real do e-mail.

**Aceite:** uma avaliação crítica persistida dispara a função e chega ao e-mail confirmado; uma não crítica não gera alerta.
**Evidência:** evento, logs e e-mail recebido com os campos obrigatórios.
**Registro:** Data: 02/11/2026 | Commit: não realizado | Testes/evidências: Maven, testes unitários, `sam validate --lint` e `sam build`; AWS/E2E e assinatura pendentes | Aprovado: pendente de revisão do Tech Lead

## Sprint 6 — Relatório semanal automatizado

**Objetivo:** gerar e enviar relatório semanal completo, em outra função serverless.

- [ ] Criar função **separada** para relatórios, sem misturar com a API ou alertas.
- [ ] Consultar avaliações da semana anterior usando período/fuso documentados.
- [ ] Calcular **média das notas**, total geral, quantidade por dia e por urgência.
- [ ] Incluir no relatório **descrição, urgência e data de envio** dos registros, conforme referência da FIAP.
- [ ] Incluir data de geração/envio do relatório e intervalo analisado, distinguindo-os da data da avaliação.
- [x] Formato de relatório e semana vazia definidos; implementar e comprovar envio continuam pendentes.
- [ ] Criar agendamento semanal via EventBridge Scheduler e configurar permissões IAM.
- [ ] Configurar envio por SNS e confirmar destinatário.
- [ ] Testar média, contagens, limites de semana, fuso e ausência de dados.
- [ ] Invocar manualmente a função na AWS para demonstrar o relatório **sem esperar uma semana**.
- [ ] Comprovar que o agendamento semanal está habilitado.

**Aceite:** relatório correto chega por e-mail, com todos os campos, média e agregações; agendamento configurado.
**Evidência:** relatório recebido, logs, configuração do EventBridge e testes.
**Registro:** Data: ____ | Commit: ____ | Testes/evidências: ____ | Aprovado: ____

## Sprint 7 — Deploy automatizado, segurança e observabilidade

**Objetivo:** cumprir requisitos operacionais e tornar o deploy repetível.

- [ ] Configurar pipeline de **CI** no GitHub Actions para compilar e executar testes em Java 17.
- [ ] Configurar pipeline de **deploy automatizado** dos componentes atualizáveis via SAM.
- [ ] Preferir GitHub Actions → AWS via **OIDC**, sem access keys de longa duração no repositório.
- [ ] Proteger deploy com branch/ambiente de aprovação e documentar como acioná-lo.
- [ ] Revisar IAM de cada Lambda, do pipeline e dos serviços envolvidos.
- [ ] Validar MFA, criptografia em repouso, uso de HTTPS, variáveis de ambiente e ausência de segredos no Git.
- [ ] Configurar logs estruturados e métricas CloudWatch para erros, invocações e duração.
- [ ] Configurar pelo menos alarmes úteis para falhas das funções e, quando aplicável, erros da API.
- [ ] Definir retenção de logs e verificar impacto de custo.
- [ ] Testar alteração pequena → pipeline → deploy → smoke test.
- [ ] Documentar procedimentos de rollback, redeploy e remoção da stack.

**Aceite:** pipeline executa testes e deploy de modo reproduzível; logs, métricas e alertas demonstráveis; controles de segurança documentados.
**Evidência:** execução do GitHub Actions, CloudWatch, IAM e histórico do deploy.
**Registro:** Data: ____ | Commit: ____ | Execução CI/CD: ____ | Aprovado: ____

## Sprint 8 — Testes finais, integração e qualidade

**Objetivo:** validar os três fluxos completos e preparar uma demonstração confiável.

- [ ] Fluxo A: avaliação válida → API Gateway → Lambda API → DynamoDB.
- [ ] Fluxo B: avaliação crítica → DynamoDB Streams → Lambda de alerta → e-mail.
- [ ] Fluxo C: execução do relatório → consulta semanal → média/contagens → e-mail.
- [ ] Testar nota -1, 0, 3, 4, 6, 7, 10, 11, decimal, nula e texto inválido.
- [ ] Testar descrição crítica ausente, nula, em branco e acima de 1.000 caracteres (400).
- [ ] Testar nota não crítica com descrição ausente, nula e em branco (201 + mensagem padrão + `descricao: null` persistida), descrição voluntária válida (201) e acima de 1.000 (400).
- [ ] Testar fronteiras da semana, datas UTC/fuso e relatório sem avaliações.
- [ ] Verificar falhas de dependências, tratamento de exceções e logs sem dados sensíveis.
- [ ] Revisar tratamento de eventos repetidos e possíveis e-mails duplicados.
- [ ] Executar suíte completa com Java 17 e revisar qualidade do código.
- [ ] Executar `git status`, conferir arquivos versionados e procurar credenciais expostas.
- [ ] Registrar evidências dos testes em `docs/test-evidence.md`.

**Aceite:** todos os fluxos obrigatórios testados na AWS, com resultados reproduzíveis e pendências críticas resolvidas.
**Evidência:** testes, respostas HTTP, logs e e-mails (dados de demonstração).
**Registro:** Data: ____ | Commit: ____ | Testes/evidências: ____ | Aprovado: ____

## Sprint 9 — Documentação, vídeo e entrega

**Objetivo:** entregar um repositório público e uma demonstração que comprovem cada requisito.

- [ ] Finalizar `README.md` com objetivo, tecnologias, requisitos e visão geral.
- [ ] Criar `docs/architecture.md` com diagrama, fluxo, decisões e justificativa do modelo cloud.
- [ ] Criar `docs/functions.md` com responsabilidade, evento de entrada, saída, IAM e erros de cada Lambda.
- [ ] Criar `docs/deploy.md` com pré-requisitos, parâmetros, CI/CD, deploy e rollback.
- [ ] Criar `docs/monitoring.md` com logs, métricas, alarmes e como verificar funcionamento.
- [ ] Criar `docs/security.md` com IAM, governança, proteção dos dados e gestão de custos.
- [ ] Documentar contrato oficial `POST /avaliacao`, payloads e exemplos de erro.
- [ ] Documentar classificação de urgência, datas/fuso e cálculo da média semanal.
- [ ] Criar `docs/demo-script.md` com roteiro de vídeo e sequência de demonstração.
- [ ] Gravar vídeo mostrando API funcionando, avaliação crítica, e-mail, relatório, funções, monitoramento, configuração de cloud e segurança.
- [ ] Conferir áudio, legibilidade, duração exigida pela plataforma e acessibilidade do vídeo.
- [ ] Tornar o repositório **público** e verificar acesso em janela anônima.
- [ ] Conferir que não há segredos, dados pessoais, arquivos de build ou materiais FIAP protegidos no Git.
- [ ] Confirmar instruções específicas de submissão na plataforma FIAP e entregar os links exigidos.
- [ ] Fazer auditoria final da matriz de rastreabilidade abaixo; nenhuma obrigação pode ficar sem evidência.

**Aceite:** repositório público completo, vídeo acessível e todos os requisitos FIAP demonstrados.
**Evidência:** URL pública do GitHub, URL do vídeo e checklist de entrega.
**Registro:** Data: ____ | Commit final: ____ | Vídeo: ____ | Entregue: ____

---

## 2. Matriz de rastreabilidade — nenhum requisito esquecido

| Requisito do enunciado | Sprint(s) | Evidência obrigatória |
|---|---|---|
| Cloud configurada e funcional | 0, 4 | Stack AWS e API pública funcionando |
| Serverless com no mínimo 2 funções e responsabilidade única | 4, 5, 6 | Três Lambdas separadas e arquitetura documentada |
| `POST /avaliacao` e nota 0–10 | 2, 4, 8 | Testes de contrato e chamada HTTP na AWS |
| Banco de dados / suporte | 3, 4 | DynamoDB com avaliações persistidas |
| Deploy automatizado | 4, 7 | SAM e execução do GitHub Actions |
| Monitoramento | 4, 7 | CloudWatch Logs, métricas e alarmes |
| Notificação de problema crítico | 5, 8 | E-mail real e logs do processamento |
| E-mail: descrição, urgência e data de envio | 5 | Conteúdo do e-mail recebido |
| Relatório semanal com média | 6, 8 | Cálculo testado, e-mail e agenda ativa |
| Relatório: descrição, urgência, data, quantidade/dia e quantidade/urgência | 6, 8 | Conteúdo do relatório recebido |
| Segurança dos dados e governança | 0, 3, 4, 7 | IAM, MFA, criptografia, proteção de segredos e documentação |
| Documentação da arquitetura, deploy, monitoramento e funções | 9 | README e `docs/*.md` |
| Repositório aberto | 9 | GitHub público e acessível |
| Vídeo mostrando sistema, funções e configurações | 9 | Link do vídeo e roteiro completo |

## 3. Planejamento por prioridade

### Primeiros quatro dias — foco em um MVP verificável

- **Dia 1:** Sprint 0 e Sprint 1; Java 17, decisões e API mínima.
- **Dia 2:** Sprint 2; contrato, regras e testes de avaliação.
- **Dia 3:** Sprint 3; persistência DynamoDB e preparação do template SAM. **Iniciar a preparação da conta AWS neste ponto**, sem criar recursos pagos sem autorização.
- **Dia 4:** Sprint 4; **primeiro deploy real na AWS**, condicionado à conta, MFA, orçamento e permissões preparados. Se houver bloqueio, priorizar resolver a integração Quarkus/Lambda antes de ampliar funcionalidades.

> Esta é uma **meta de execução**, não uma garantia de conclusão. O ritmo será ajustado pelos testes e pelos obstáculos reais.

### Depois do MVP e antes de 17/11/2026

- Implementar alertas e relatório (Sprints 5–6).
- Concluir CI/CD, segurança e monitoramento (Sprint 7).
- Executar testes ponta a ponta e corrigir problemas (Sprint 8).
- Documentar, gravar vídeo, publicar repositório e entregar (Sprint 9).
- Reservar os últimos dias para correções e contingências; não deixar o primeiro deploy nem a gravação para a véspera.

## 4. Fora do escopo obrigatório (não bloquear a entrega)

- [ ] Interface web/frontend — **opcional**; o backend pode ser demonstrado por HTTP/Postman/cURL.
- [ ] Login de alunos e administradores — **opcional**, desde que os controles de segurança da solução estejam adequadamente documentados.
- [ ] GraalVM/native image — **opcional**; JVM Java 17 é suficiente se atender ao deploy e aos testes.
- [ ] Arquitetura reativa, Kafka, RabbitMQ, GraphQL, gRPC e microsserviços adicionais — **não exigidos** neste enunciado.
- [ ] Dashboard analítico, filtros avançados, paginação e métricas sofisticadas — **melhorias futuras**.

## 5. Padrão de execução e revisão de cada sprint

1. Ler este `SPRINTS.md` e identificar apenas os itens da sprint atual.
2. Propor implementação mínima e critérios de aceite; não ampliar escopo sem aprovação.
3. Implementar e adicionar testes.
4. Executar testes e apresentar **saídas reais**, sem alegar sucesso sem execução.
5. Listar arquivos modificados, decisões, riscos e pendências.
6. Revisão do Tech Lead: explicar o código, testar localmente/na AWS e aprovar.
7. Só então marcar os itens comprovados `[x]`, registrar evidências e solicitar autorização para commit/push/deploy.
8. Avançar à próxima sprint somente com a anterior aprovada ou com pendência explicitamente aceita.

## 6. Painel de status

| Sprint | Situação inicial | Aprovação |
|---|---|---|
| 0 — Preparação | Concluída | Aprovada |
| 1 — Fundação Quarkus | Concluída | Aprovada |
| 2 — Avaliações | Concluída | Aprovada |
| 3 — DynamoDB | Concluída | Aprovada |
| 4 — Primeiro deploy AWS | Infraestrutura preparada e validada localmente; deploy pendente | Pendente |
| 5 — Alertas críticos | Pendente | Pendente |
| 6 — Relatório semanal | Pendente | Pendente |
| 7 — CI/CD, segurança e monitoramento | Pendente | Pendente |
| 8 — Testes finais | Pendente | Pendente |
| 9 — Documentação e entrega | Pendente | Pendente |

**Regra de encerramento:** o FeedFlow só está pronto quando todos os itens obrigatórios da matriz de rastreabilidade possuem evidência e a entrega foi confirmada na plataforma FIAP.


## Registro de aprovação — 01/11/2026

Aprovados pelo Tech Lead: Java 17; repositório único com módulos Maven separados; Quarkus para API; três Lambdas; DynamoDB com índice semanal; urgência 0–3/4–6/7–10; descrição obrigatória apenas em notas críticas (máximo 1.000 caracteres); contrato HTTP com `201`/`400`/`500`; relatório da semana anterior enviado segunda-feira às 09h em `America/Sao_Paulo`; exibição **"Avaliação sem comentário"** quando a descrição é nula; IAM mínimo, sem credenciais versionadas, AWS SAM, GitHub Actions com OIDC, CloudWatch e testes automatizados.

**Não confundir decisão aprovada com trabalho implementado.** Permanecem pendentes: verificação efetiva do JDK 17, versão/extensões do Quarkus, teste da rota acentuada na AWS, escolha da região e destinatário, configuração da conta AWS e toda execução de código/deploy. Nenhum commit ou push foi realizado nesta atualização.
