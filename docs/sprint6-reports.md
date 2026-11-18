# Sprint 6 — Lambda de relatório semanal

## Estado da entrega

A Lambda e a infraestrutura foram implementadas e validadas somente no ambiente local. Nenhum deploy, change set, assinatura SNS ou recurso AWS foi criado ou alterado.

Configuração prevista:

- função `${AWS::StackName}-reports`, Java 17, `x86_64`, 512 MB e timeout de 60 segundos;
- artefato `feedflow-reports/target/function.zip`;
- handler `br.com.feedflow.reports.handler.WeeklyReportHandler::handleRequest`;
- tópico `${AWS::StackName}-weekly-reports`, sem assinatura automática;
- agendamento às segundas-feiras, 09:00 em `America/Sao_Paulo`, sem janela flexível;
- fila `${AWS::StackName}-reports-failures`, criptografada e com retenção de 14 dias.

## Fluxo

```text
EventBridge Scheduler
  └─ Lambda de relatórios
       ├─ Query paginado na GSI byWeek
       ├─ agregação e formatação
       └─ SNS Weekly Reports
```

O Scheduler envia o payload estático `{"source":"feedflow.scheduler.weekly-report"}`. Ele não fornece um campo `time` inventado pelo template. O handler aceita esse mapa e usa o relógio da invocação para calcular a semana anterior completa no fuso `America/Sao_Paulo`.

A consulta usa `dynamodb:Query` somente no ARN `.../index/byWeek`, com `weekKey` no formato `SEMANA#AAAA-MM-DD` e intervalo por `createdAt`. Todas as páginas indicadas por `LastEvaluatedKey` são processadas.

512 MB fornecem CPU suficiente para inicialização do SDK Java, paginação e formatação. O timeout de 60 segundos oferece margem para múltiplas páginas e publicação SNS, sem deixar uma execução travada consumir o limite máximo da plataforma.

## IAM

A role da Lambda permite:

- `dynamodb:Query` somente na GSI `byWeek`;
- `sns:Publish` somente no tópico de relatórios;
- `logs:CreateLogStream` e `logs:PutLogEvents` somente no log group da função;
- `sqs:SendMessage` somente na fila de falhas, necessária para o destino de invocações assíncronas descartadas.

A role separada do Scheduler permite somente `lambda:InvokeFunction` na Lambda de relatórios e `sqs:SendMessage` na fila de falhas. Nenhuma política usa `Resource: "*"`.

## Retentativas e falhas

Existem duas etapas independentes:

1. O retry do Scheduler cobre falhas ao entregar a solicitação de invocação ao serviço Lambda. São duas tentativas adicionais, idade máxima de uma hora e DLQ SQS.
2. Depois que o Lambda aceita a chamada assíncrona, o `EventInvokeConfig` cobre erros do código, timeouts e falhas de runtime. São duas tentativas adicionais, idade máxima de uma hora e destino para a mesma fila SQS.

A fila pode receber formatos diferentes: evento de falha do Scheduler ou registro de invocação assíncrona do Lambda. A origem deve ser identificada antes de qualquer replay manual.

O processamento é **at least once**. Se o SNS aceitar o relatório e a resposta se perder, ou se ocorrer falha posterior, um retry pode publicar o mesmo relatório novamente. Não existe deduplicação durável nesta sprint.

## Limite SNS

O publicador calcula o tamanho real de `message` em UTF-8. O limite aceito é 262.144 bytes. Acima disso, a publicação não é chamada e a função falha com mensagem explícita, permitindo os retries e o destino de falha. O conteúdo nunca é truncado silenciosamente.

Para volumes maiores, uma evolução possível é gravar o relatório completo em S3 e publicar no SNS apenas um resumo e uma referência temporária. Isso exige nova análise de retenção, criptografia, IAM, custo e acesso e, por isso, não foi introduzido automaticamente.

## Implantação e rollback

Após aprovação, o operador deverá gerar o preview com `scripts/deploy-aws.ps1 -Authorization PREVIEW -PreviewOnly` e, somente depois da revisão, executar `scripts/deploy-aws.ps1 -Authorization DEPLOY -ChangeSetPreviewApproved`. A tabela existente, o tópico crítico e as duas Lambdas anteriores não devem ser substituídos.

Se a atualização falhar durante o deploy, o rollback padrão do CloudFormation deve restaurar a última versão estável. Depois de um deploy concluído, o rollback funcional consiste em reconstruir a revisão anterior aprovada e aplicar novo change set. A fila de falhas deve ser inspecionada antes de reprocessar ou remover qualquer mensagem.
