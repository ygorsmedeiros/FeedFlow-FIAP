# Sprint 5 — Lambda de notificações críticas

## Estado desta entrega

A Lambda e a infraestrutura foram implementadas e validadas somente no ambiente local em 02/11/2026. Nenhum deploy, change set, assinatura SNS ou recurso AWS foi criado ou alterado.

Configuração prevista:

- stack `feedflow-dev`;
- região `us-east-2` (Ohio);
- perfil AWS CLI `feedflow`;
- tabela existente no template `FeedFlowFeedback`, com Stream `NEW_IMAGE`;
- função `${AWS::StackName}-notifications`, Java 17, `x86_64`, 256 MB e timeout de 20 segundos;
- artefato `feedflow-notifications/target/function.zip`, contendo o JAR sombreado em `lib/`;
- tópico `${AWS::StackName}-critical-alerts`, sem assinatura automática;
- fila `${AWS::StackName}-notifications-failures`, criptografia SQS gerenciada e retenção de 14 dias.

## Arquitetura e responsabilidade

```text
FeedFlowFeedback
  └─ DynamoDB Stream (NEW_IMAGE)
       └─ Event Source Mapping (lote até 10)
            └─ CriticalNotificationHandler
                 ├─ CriticalRecordSelector
                 ├─ CriticalFeedbackMapper
                 └─ SnsAlertPublisher
                      └─ SNS Critical Alerts Topic
```

O handler processa cada item independentemente. Somente eventos `INSERT` com `urgencia = CRITICA` são mapeados e publicados. `MODIFY`, `REMOVE`, `MEDIA` e `BAIXA` são ignorados. Um alerta contém ID, nota, urgência, descrição, instante de criação da avaliação e instante de geração do alerta.

O `NewImage` crítico precisa conter `id` UUID, `nota` inteira entre 0 e 3, `urgencia` igual a `CRITICA`, `descricao` não vazia com até 1.000 caracteres e `createdAt` em ISO-8601. Dados permanentes inválidos geram log apenas com o identificador técnico do registro; o payload e a descrição não são escritos no log.

## Falhas, retries e descarte

O Event Source Mapping está configurado com:

| Propriedade | Valor |
|---|---:|
| `StartingPosition` | `LATEST` |
| `BatchSize` | 10 |
| `MaximumBatchingWindowInSeconds` | 1 |
| `FunctionResponseTypes` | `ReportBatchItemFailures` |
| `BisectBatchOnFunctionError` | `true` |
| `MaximumRetryAttempts` | 3 |
| `MaximumRecordAgeInSeconds` | 3600 |
| destino após descarte | SQS |

Falhas de SNS e erros técnicos inesperados entram em `batchItemFailures` pelo `sequenceNumber`, enquanto os demais registros do lote continuam. Se um evento técnico anômalo não possuir `sequenceNumber`, o handler falha o lote inteiro, pois não existe identificador válido para resposta parcial.

Campos ausentes ou inválidos não são reenviados: são falhas permanentes e seriam poison pills se fossem repetidos. Após os retries/idade máxima, o destino SQS guarda os metadados de falha fornecidos pelo Lambda. Ele não é uma cópia garantida do `NewImage`; depois da retenção do Stream pode não ser possível reconstruir o alerta apenas pela fila.

## Idempotência

O fluxo oferece entrega **at least once**, não exactly once. Não existe tabela ou chave de deduplicação adicional nesta sprint. O mesmo evento processado duas vezes publica duas mensagens, comportamento coberto por teste automatizado.

Também pode ocorrer duplicidade quando o SNS aceita a publicação, mas a função não recebe a resposta, ou quando um registro posterior ao menor `sequenceNumber` com falha é reprocessado. Uma deduplicação durável exigiria estado adicional e política de expiração; essa decisão foi deixada para aprovação do Tech Lead para não alterar a tabela existente nem introduzir custo/complexidade sem autorização.

## IAM mínimo

A role de notificações é separada da API e permite:

- `dynamodb:DescribeStream`, `GetRecords` e `GetShardIterator` somente no ARN do Stream;
- `dynamodb:ListStreams` em `*`, porque essa ação não oferece escopo útil por Stream;
- `sns:Publish` somente no tópico de alertas;
- `sqs:SendMessage` somente na fila de falhas;
- `logs:CreateLogStream` e `logs:PutLogEvents` somente no log group da função.

A role não pode escrever na tabela, criar tópicos/filas, gerenciar assinaturas ou ler outros dados da conta.

## Assinatura e teste AWS pendentes

O template cria o tópico, mas não cria assinatura de e-mail sem endereço real. Após aprovação e deploy, o operador deverá:

1. criar uma assinatura de e-mail usando um endereço aprovado;
2. confirmar a assinatura na caixa de entrada;
3. enviar uma avaliação crítica sintética a `POST /avaliacao`;
4. comprovar Lambda, logs sem payload sensível, mensagem SNS e e-mail recebido;
5. enviar avaliações média e baixa e comprovar que não há alerta;
6. simular/controlar uma falha para validar retry e destino SQS sem perder mensagens reais.

Nada dessa lista foi executado nesta entrega local.
