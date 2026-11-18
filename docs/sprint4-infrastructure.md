# Sprint 4 — Infraestrutura AWS preparada para revisão

## Estado desta entrega

O template e os scripts foram preparados e validados localmente em 02/11/2026. Nenhum deploy, change set, stack ou recurso AWS foi criado, alterado ou removido nesta execução.

Configuração operacional prevista:

- região `us-east-2` (Ohio);
- perfil AWS CLI `feedflow`, usando credenciais temporárias da função `AccountFullAccessRole`;
- stack `feedflow-dev`;
- runtime Lambda `java17` e arquitetura `x86_64`;
- artefato `feedflow-api/target/function.zip` gerado pelo Quarkus;
- handler `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.

O arquivo auxiliar `feedflow-api/target/sam.jvm.yaml` é gerado pelo Quarkus com `java21` e não deve ser usado para o FeedFlow. A infraestrutura definitiva desta sprint é o `template.yaml` da raiz.

## Recursos propostos

O `template.yaml` declara:

| Recurso | Configuração |
|---|---|
| HTTP API | API Gateway HTTP API v2, stage `$default`, throttle de 10 requisições/s e burst 20 |
| Rotas | `GET /health` e `POST /avaliacao` |
| Lambda API | Java 17, `x86_64`, 512 MB, timeout de 15 s, sem VPC |
| DynamoDB | `FeedFlowFeedback`, `id` String, on-demand, GSI `byWeek`, Streams `NEW_IMAGE` |
| Log group | `/aws/lambda/<stack>-api`, retenção de 14 dias |
| IAM | função exclusiva da Lambda API, sem políticas gerenciadas amplas |

Não há CORS porque a Sprint 4 não inclui frontend nem cliente executado em navegador. Se isso mudar, origens e métodos deverão ser liberados explicitamente; CORS não deve ser tratado como autenticação.

`x86_64` foi mantida por ser a arquitetura solicitada e ainda não haver evidência do pacote em `arm64`. A aplicação é bytecode Java, mas qualquer troca de arquitetura deve ser medida no runtime real antes de ser consolidada.

### Rota oficial

Na Sprint 5, o Tech Lead oficializou exclusivamente `POST /avaliacao`. A rota Unicode, o proxy `POST /{proxy+}` e o output Unicode foram removidos do template; a aplicação Quarkus também não declara mais o caminho com acento. Qualquer outro caminho deve retornar 404.

Referências: [rotas de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-routes.html) e [limitações conhecidas do API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-known-issues.html).

## IAM de menor privilégio

A execution role permite somente:

| Ação | Recurso |
|---|---|
| `dynamodb:PutItem` | ARN exato da tabela |
| `dynamodb:GetItem` | ARN exato da tabela |
| `dynamodb:Query` | ARN exato `.../index/byWeek` |
| `logs:CreateLogStream` | streams do log group exato da Lambda |
| `logs:PutLogEvents` | streams do log group exato da Lambda |

Não são concedidos `Scan`, exclusão ou alteração da tabela, curingas sobre tabelas ou `AdministratorAccess`. O acesso ao stream pertence somente à role separada da função de notificações adicionada na Sprint 5; a role da API continua sem essa permissão.

Referência: [políticas IAM para tabela e índices DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/iam-policy-specific-table-indexes.html).

## Configuração local e AWS

Em `dev` e `test`, a propriedade `FEEDFLOW_DYNAMODB_ENDPOINT` aponta para o DynamoDB Local e ativa credenciais estáticas fictícias. Na AWS, essa variável não é definida. O cliente usa o endpoint regional padrão, `AWS_REGION` fornecida automaticamente pela Lambda e a cadeia de credenciais temporárias da execution role.

O nome da tabela é recebido por `FEEDFLOW_DYNAMODB_TABLE_NAME`, preenchido pelo `Ref` da tabela. Nenhuma chave, token ou credencial permanente é armazenada no template ou nos scripts.

## Criptografia, retenção e proteção

- DynamoDB sempre cifra dados em repouso. O template mantém a chave pertencente à AWS, que não gera cobrança de AWS KMS. Uma chave gerenciada pelo cliente aumentaria governança, permissões e custo e não foi introduzida no MVP.
- PITR está desabilitado no ambiente `dev` para evitar cobrança de backup contínuo.
- A proteção contra exclusão está desabilitada e as políticas de deleção são `Delete`, permitindo remover integralmente os dados sintéticos junto com a stack.
- Os logs são excluídos com a stack e retidos por 14 dias enquanto ela existir.
- Não há TTL automático para avaliações. Os dados permanecem até a exclusão da stack.

Essas escolhas são adequadas apenas ao ambiente de demonstração com dados sintéticos. Antes de produção, o Tech Lead deve decidir PITR, `DeletionProtectionEnabled: true`, `DeletionPolicy: Retain`, prazo de retenção/TTL e eventual chave KMS gerenciada pelo cliente.

Referência: [criptografia em repouso do DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/encryption.usagenotes.html).

## Build e validação local

No PowerShell, a partir da raiz:

```powershell
.\scripts\build-and-test.ps1
.\scripts\validate-sam.ps1
```

Para incluir o banco real local:

```powershell
.\scripts\build-and-test.ps1 -IncludeDynamoDbLocalTests
```

O primeiro script força o JDK 17 apenas na sessão, executa `mvnw.cmd clean verify` e confirma o `function.zip` da API, o JAR sombreado e o `function.zip` de notificações. O segundo executa `sam validate --lint` e `sam build`; ele não acessa nem altera a AWS.

## Deploy manual — não executado

Pré-requisitos humanos:

1. revisar `template.yaml`, IAM, retenção e custos;
2. confirmar MFA, sessão temporária do perfil e orçamento;
3. garantir que não exista uma tabela `FeedFlowFeedback` fora da stack;
4. obter autorização explícita do Tech Lead.

Com a autorização, o operador executará manualmente:

```powershell
.\scripts\deploy-aws.ps1 -Authorization PREVIEW -PreviewOnly
.\scripts\deploy-aws.ps1 -Authorization DEPLOY -ChangeSetPreviewApproved
```

O script recompila, testa e valida antes de chamar `sam deploy` com perfil `feedflow`, região `us-east-2` (Ohio), stack `feedflow-dev` e `CAPABILITY_NAMED_IAM`. O primeiro comando usa `--no-execute-changeset`; o segundo exige a declaração da revisão e preserva `--confirm-changeset`. `--resolve-s3` reutiliza o bucket gerenciado pelo SAM e pode criar essa infraestrutura auxiliar somente se ela ainda não existir.

Para consultar outputs e recursos após um deploy autorizado:

```powershell
.\scripts\show-aws-stack.ps1
```

Depois, deverão ser testados `GET /health`, `POST /avaliacao`, a persistência, o GSI, o stream, as duas Lambdas, o SNS e os log groups. Somente dados sintéticos devem ser enviados.

## Rollback e exclusão

O deploy mantém o rollback padrão do CloudFormation. Se uma criação ou atualização falhar, o CloudFormation retorna ao último estado estável. Para rollback funcional após um deploy bem-sucedido, deve-se reconstruir uma revisão anterior aprovada e executar novamente o deploy com inspeção do change set.

Para remover a stack, somente após autorização explícita:

```powershell
.\scripts\remove-aws-stack.ps1 -Authorization DELETE
```

O script ainda exige confirmação interativa do SAM. A remoção apaga a tabela, todos os registros de demonstração, a Lambda, a API, a role e o log group. Depois, consultar Billing/Cost Explorer e verificar o bucket de artefatos gerenciado pelo SAM; ele pode existir fora da stack `feedflow-dev` e não deve ser excluído se for compartilhado por outros projetos.

## Custos estimados e riscos

Serviços com possível cobrança:

- API Gateway HTTP API por requisição e transferência de dados;
- Lambda por invocação e duração em GB-segundo;
- DynamoDB por leituras, escritas, armazenamento, índice e leituras futuras do stream;
- CloudWatch Logs por ingestão, armazenamento e consultas;
- S3 para o artefato usado pelo SAM;
- transferência de dados de saída.

Exemplo conservador, antes de Free Tier e créditos: 10.000 avaliações/mês, itens abaixo de 1 KB, uma gravação na tabela e outra no GSI, Lambda de 512 MB com média de 1 segundo e 100 MB de logs resultam em aproximadamente US$ 0,01 de HTTP API, US$ 0,09 de Lambda, US$ 0,02 de DynamoDB e cerca de US$ 0,05 de ingestão de logs. O total ilustrativo fica abaixo de US$ 0,20/mês, sem contar transferência, consultas e armazenamento. Se cada invocação consumir o timeout integral de 15 segundos, somente a duração da Lambda se aproxima de US$ 1,25 para 10.000 chamadas.

Os valores são aproximações de baixa escala e podem mudar. O Free Plan e os US$ 100 de créditos podem absorver parte do uso, mas não devem ser tratados como garantia. Conferir [preços do API Gateway](https://aws.amazon.com/api-gateway/pricing/), [Lambda](https://aws.amazon.com/lambda/pricing/), [DynamoDB](https://aws.amazon.com/dynamodb/pricing/) e [CloudWatch](https://aws.amazon.com/cloudwatch/pricing/) antes do deploy.

O orçamento mensal de US$ 5 envia alertas, mas não bloqueia consumo. Durante os testes, acompanhar Billing > Bills, Cost Explorer, Free Tier e Credits. Interromper testes anormais e remover a stack quando a demonstração não precisar permanecer online.

## Pendências para a AWS e impacto nas próximas sprints

- Provar cold start, handler e compatibilidade do `function.zip` no runtime Java 17 real.
- Revisar futuramente a migração de `java17` (Amazon Linux 2), exigido nesta sprint, para `java17.al2023`; a AWS publica suporte mais longo para o runtime AL2023. Não alterar antes da decisão do Tech Lead. Consulte [runtimes Java do Lambda](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html).
- Confirmar que somente a rota ASCII `POST /avaliacao` está exposta e que o caminho Unicode retorna 404.
- Confirmar respostas 200/201/400/500, persistência, GSI, stream e logs em `us-east-2`.
- Verificar no IAM Access Analyzer e no CloudTrail que a role não recebeu permissões além das declaradas.
- A Sprint 5 adicionou role e função próprias para o Stream; a API permanece sem acesso de leitura do Stream. Os detalhes estão em [sprint5-notifications.md](sprint5-notifications.md).
- Sprint 6 reutilizará o GSI `byWeek` com uma role própria de leitura.
- Sprint 7 deverá automatizar o deploy com OIDC, adicionar alarmes e revisar proteção de custo/segurança.
- Sprints 8 e 9 deverão registrar evidências reais, arquitetura final, contrato ASCII, rollback e remoção.
