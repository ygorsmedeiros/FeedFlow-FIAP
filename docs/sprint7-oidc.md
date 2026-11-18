# FeedFlow — Sprint 7, Fase 2.1: preparação OIDC endurecida

## Escopo e estado

Esta fase prepara localmente a autenticação do GitHub Actions na AWS por OIDC e corrige os
achados de segurança da Fase 2. Nenhum recurso AWS foi criado ou alterado. O workflow de CI
permanece sem acesso à AWS. A revisão de entrega inclui um workflow de deploy direto, acionado manualmente, cuja autenticação OIDC está bloqueada pela SCP da organização.

Configuração alvo:

- Conta AWS: `143506099760`.
- Região: `us-east-2`.
- Stack da aplicação: `feedflow-dev`.
- Repositório alvo do template de bootstrap original: `ygorsmedeiros/FeedFlow`.
- GitHub Environment proposto: `feedflow-dev`.
- Audience OIDC: `sts.amazonaws.com`.
- Prefixo imutável dos artefatos: `feedflow-dev/`.

Continuam pendentes de verificação externa: provider OIDC, ID físico da HTTP API, bucket de
artefatos, configuração de criptografia/KMS, claim `sub` real e protection rules do GitHub
Environment.

## Arquitetura corrigida

```text
GitHub job no Environment feedflow-dev
        │ OIDC: aud e sub exatos; credenciais temporárias
        ▼
feedflow-github-actions-deploy
        ├── publica somente em s3://<bucket-aprovado>/feedflow-dev/
        ├── cria e consulta change sets somente de feedflow-dev
        ├── nunca executa pelo script de preview
        └── PassRole somente de feedflow-cloudformation-execution ao CloudFormation
                         │
                         ▼
             feedflow-cloudformation-execution
                 ├── três Lambdas com nomes exatos
                 ├── HTTP API com ID físico aprovado
                 ├── recursos de dados/mensageria com nomes exatos
                 └── quatro roles de runtime com nomes exatos
                                  │
                                  ▼
                    permissions boundaries separadas
                    por API, notificações, relatórios
                    e Scheduler
```

O provider, a role OIDC, a execution role e as quatro boundaries pertencem a uma stack
bootstrap separada. A aplicação apenas referencia as boundaries por ARNs determinísticas.
Não existe referência da execution role para a role GitHub, portanto não há dependência
circular entre as duas roles.

## Trust policy e GitHub Environment

A role OIDC aceita somente `sts:AssumeRoleWithWebIdentity` e exige igualdade exata de:

- `token.actions.githubusercontent.com:aud = sts.amazonaws.com`;
- `token.actions.githubusercontent.com:sub = <subject aprovado>`.

O parâmetro `GitHubOidcSubject` não tem padrão e aceita exclusivamente o repositório
`ygorsmedeiros/FeedFlow`, o Environment `feedflow-dev` e, quando emitidos pelo GitHub, os IDs
imutáveis de owner e repositório.

O Environment deve ser verificado externamente com:

- deployment branch limitada a `main`;
- reviewers obrigatórios, quando suportado pelo plano;
- prevenção de autoaprovação;
- job de deployment declarando `environment: feedflow-dev`;
- nenhum segredo AWS estático.

O claim de Environment não contém a branch. A restrição à `main` depende das protection
rules do GitHub e não pode ser comprovada apenas pelos arquivos locais.

A execution role confia somente em `cloudformation.amazonaws.com`. Depois de associada à
stack, qualquer principal autorizado a operar a stack pode fazer o CloudFormation usar essa
role, mesmo sem `iam:PassRole`. Por isso, a primeira associação não será automatizada.

## Permissions boundaries das roles de runtime

Foram definidas quatro managed policies independentes, usadas exclusivamente como boundaries:

| Boundary | Role obrigatória | Limite máximo |
|---|---|---|
| `feedflow-dev-api-runtime-boundary` | `feedflow-dev-api-runtime` | Get/Put na tabela, Query em `byWeek` e logs da API |
| `feedflow-dev-notifications-runtime-boundary` | `feedflow-dev-notifications-runtime` | leitura do Stream, publicação no SNS crítico, SQS de falhas e logs |
| `feedflow-dev-reports-runtime-boundary` | `feedflow-dev-reports-runtime` | Query em `byWeek`, SNS semanal, SQS de falhas e logs |
| `feedflow-dev-reports-scheduler-boundary` | `feedflow-dev-reports-scheduler` | invocação da Lambda de relatórios e SQS de falhas |

Cada `iam:CreateRole` possui ARN exato e condição `iam:PermissionsBoundary` apontando para a
boundary correspondente. A execution role não recebe `iam:AttachRolePolicy`,
`iam:DetachRolePolicy`, `iam:PutRolePermissionsBoundary` ou
`iam:DeleteRolePermissionsBoundary`. Há `Deny` explícito para as duas últimas ações e para
alteração ou exclusão das managed policies usadas como boundaries. As quatro boundaries usam
`DeletionPolicy: Retain` e `UpdateReplacePolicy: Retain`, evitando que a remoção acidental do
bootstrap retire a proteção das roles da aplicação.

`iam:PutRolePolicy` e `iam:UpdateAssumeRolePolicy` continuam necessários porque o
`AWS::IAM::Role` da aplicação usa políticas inline e trust policies gerenciadas pelo
CloudFormation. Essas ações estão restritas às quatro roles exatas. Uma política inline
maliciosa não consegue ultrapassar a boundary, mas ainda pode redistribuir entre uma role as
permissões de aplicação permitidas pela boundary. Esse é um risco residual e exige inspeção
do change set.

`iam:PassRole` foi separado:

- as três roles Lambda podem ser passadas somente a `lambda.amazonaws.com`;
- a role do Scheduler pode ser passada somente a `scheduler.amazonaws.com`.

## Matriz da CloudFormation execution role

| Recurso | Permissões concedidas | Escopo e justificativa |
|---|---|---|
| Lambda Functions | Create/Delete/Get, código, configuração, policies, tags e EventInvokeConfig | Somente `feedflow-dev-api`, `-notifications` e `-reports` |
| EventInvokeConfig | Get/Put/Update/Delete/List | Necessário para `FeedFlowReportsEventInvokeConfig` |
| Event Source Mapping | Create/Get/Update/Delete, List e tags | Create condicionado à Lambda de notificações; ARN de mapping é dinâmico |
| HTTP API | GET/POST/PUT/PATCH/DELETE | Somente `/apis/<FeedFlowHttpApiId>` e seus subrecursos; criação de outra API não é autorizada |
| Logs | criação, retenção, tags e leituras do resource provider | Somente os três log groups; operações de listagem permanecem `Resource: "*"` |
| DynamoDB | criação/descrição/update/PITR/tags | Somente `FeedFlowFeedback` e `byWeek`; `DeleteTable` permanece negado |
| SNS | criação/update/delete/tags e leituras | Somente tópicos crítico e semanal |
| SQS | criação/update/delete/tags e leituras | Somente as duas filas de falhas |
| Scheduler | create/get/update/delete/tags | Somente `default/feedflow-dev-weekly-report` |
| IAM runtime | create/update/delete/policies inline/tags | Somente quatro roles exatas e com boundary obrigatória |
| S3 | GetObject/GetObjectVersion | Somente bucket aprovado, mesma conta e prefixo `feedflow-dev/` |

Passar no `cfn-lint` não comprova suficiência operacional das políticas. A matriz foi
comparada com os recursos declarados e com os recursos gerados pelo SAM; a validação final
continua dependendo de change set não executado e inspeção das falhas de autorização.

### Permissões `Resource: "*"` e ARNs dinâmicas residuais

- `lambda:CreateEventSourceMapping`, condicionado pelo ARN exato da Lambda de notificações;
- `lambda:ListEventSourceMappings`, ação de listagem;
- `logs:DescribeLogGroups` e `logs:DescribeResourcePolicies`, leituras regionais;
- `cloudformation:ValidateTemplate` e `GetTemplateSummary`, usadas pelo SAM;
- `dynamodb:ListStreams` na boundary e na role de notificações;
- ARN curinga de Event Source Mapping para leitura e tags, pois o UUID só existe após criação.

As ações de tag em Event Source Mappings exigem a tag `Application=FeedFlow` na requisição ou
no recurso. `PropagateTags: true` foi habilitado na função SAM de notificações. O acesso de
listagem ainda pode revelar metadados de outros recursos da conta e é um risco residual de
baixo impacto.

## API Gateway existente

O bootstrap exige o parâmetro `FeedFlowHttpApiId`. O valor não possui default e deve ser
obtido por consulta somente de leitura à stack `feedflow-dev` antes do bootstrap. A execution
role não possui `POST /apis`, portanto não consegue criar uma API substituta. Uma alteração
que exija substituição da HTTP API deverá falhar e seguir procedimento manual de break-glass,
com nova revisão de segurança.

Essa limitação é intencional: o deployment cotidiano pode atualizar somente a API já
autorizada e não pode modificar outras APIs da região.

## DynamoDB e retenção

A tabela declara agora:

- `DeletionPolicy: Retain`;
- `UpdateReplacePolicy: Retain`;
- `DeletionProtectionEnabled` controlado por `DynamoDbSafetyControlsEnabled`;
- point-in-time recovery controlado pelo mesmo parâmetro;
- `Deny` explícito de `dynamodb:DeleteTable` na execution role.

O `Deny` não é proteção completa: aplica-se somente às chamadas feitas com essa role, não
impede modificações permitidas por `UpdateTable` e pode fazer rollback ou substituição parar
com a tabela antiga retida. `Retain` e a proteção de exclusão reduzem perda acidental, mas
podem deixar tabelas órfãs que continuam gerando cobrança.

PITR possui custo de backup contínuo conforme volume da tabela. Deletion protection não tem
custo direto. O parâmetro tem padrão `false`, compatível com o estado real observado, e o
deploy manual o fixa em `false`. A habilitação futura exige change set isolado e aprovação.

## Provider OIDC, bucket e KMS

O provider condicional possui `DeletionPolicy: Retain` e `UpdateReplacePolicy: Retain`. Se a
conta já possuir o provider, `CreateGitHubOidcProvider` deve permanecer `false`. Se estiver
ausente, a criação só pode ser aprovada após confirmar que a stack bootstrap será sua dona.

O parâmetro livre de prefixo S3 foi removido. Todas as políticas usam o valor fixo
`feedflow-dev/`, sem `*` ou `?` provenientes de parâmetro. `s3:ResourceAccount` exige que o
bucket pertença à mesma conta.

Antes do provisionamento, validar externamente:

- existência e owner do bucket;
- Block Public Access;
- bucket policy exigindo TLS;
- Object Ownership e ACLs desabilitadas;
- versionamento e lifecycle;
- criptografia SSE-S3 ou SSE-KMS;
- ausência de acesso público ou cross-account não aprovado;
- provider OIDC, audiences e thumbprints atuais.

O bucket SAM foi identificado com criptografia `aws:kms`, versionamento e Block Public Access.
O ARN e a key policy da chave ainda não foram confirmados. A role GitHub precisará, no mínimo,
das operações KMS exigidas para upload no ARN exato da chave; download e multipart upload
podem exigir `kms:Decrypt`. A execution role e os serviços consumidores também devem ser
avaliados contra a key policy. Essas permissões não foram adicionadas sem conhecer a chave.

As consultas `iam:ListOpenIDConnectProviders` e `iam:GetOpenIDConnectProvider` foram
bloqueadas por SCP. A existência e a propriedade do provider continuam pendentes de evidência
fornecida por um principal autorizado; o bootstrap não pode presumir que ele está ausente.

## Fluxos de deployment preparados

### Operação manual existente

`scripts/deploy-aws.ps1` continua usando o perfil local `feedflow`, `--resolve-s3`, autorização
explícita e confirmação manual. Ele não associa a execution role. O modo preview exige
`-Authorization PREVIEW`; o deploy exige `-Authorization DEPLOY`, a declaração
`-ChangeSetPreviewApproved` e ainda mantém `--confirm-changeset`.

O fluxo manual fixa `RuntimeRoleHardeningEnabled=false` e
`DynamoDbSafetyControlsEnabled=false`, evitando a migração acidental. A migração inicial das
roles ocorrerá somente após o bootstrap, em change set separado e opt-in. O script manual não
deve ser chamado pelo GitHub Actions.

### Preview futuro com OIDC

`scripts/preview-oidc-change-set.ps1` é separado do deploy manual e:

- exige `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` temporários;
- não aceita nem envia `--profile`;
- exige bucket, prefixo fixo e execution role explícitos;
- usa `CAPABILITY_NAMED_IAM`;
- usa `--no-execute-changeset`;
- não usa `--no-confirm-changeset` como proteção;
- nunca executa o change set.

O script pressupõe que os testes e o empacotamento Maven já tenham produzido os três
`function.zip`; ele apenas confirma a presença dos artefatos e valida o template antes do
preview. A presença de `AWS_SESSION_TOKEN` comprova uma sessão temporária, mas não comprova
sozinha que a origem foi OIDC. O futuro workflow deverá usar
`aws-actions/configure-aws-credentials`, não armazenar chaves-base e conferir a identidade da
role assumida antes do preview.

Exemplo futuro, não executado nesta fase:

```powershell
./scripts/preview-oidc-change-set.ps1 `
  -Authorization PREVIEW `
  -ArtifactBucket '<bucket-aprovado>' `
  -CloudFormationExecutionRoleArn `
    'arn:aws:iam::143506099760:role/feedflow-cloudformation-execution'
```

A execução deverá ser um job ou procedimento separado, protegido por aprovação do GitHub
Environment e validação do ARN/estado/conteúdo do change set. A Sprint 7 encerra com CD
manual. O workflow de deploy direto foi preparado para acionamento manual e segue bloqueado pela autenticação OIDC. A role OIDC preparada não possui
`cloudformation:ExecuteChangeSet`; portanto, hoje a execução só pode ser feita por um
principal humano já autorizado. Uma futura role executora exigirá trust policy e Environment
de aprovação próprios e nova revisão de segurança.

## Ajustes para o FeedFlow-FIAP

O template de bootstrap original conserva a restrição ao repositório
`ygorsmedeiros/FeedFlow`. Para uma futura implantação autorizada pelo
`ygorsmedeiros/FeedFlow-FIAP`, revisar o padrão de validação
`AllowedPattern` do parâmetro `GitHubOidcSubject` e configurar a trust
policy com igualdade exata ao claim `sub` emitido para o novo repositório
e o Environment `feedflow-dev`. Confirmar o formato efetivo e os IDs do
novo repositório, caso façam parte do claim. Manter a audience
`sts.amazonaws.com`.

No novo repositório, configurar o Environment `feedflow-dev` para
`main`, com as proteções disponíveis, a variável `AWS_DEPLOY_ROLE_ARN`
com a role aprovada para o deploy direto e o secret `ADMIN_EMAIL`.
Validar as permissões dessa role para o fluxo de deploy direto. A role
documentada para preview tem escopo diferente.

A liberação do provider OIDC pela organização segue pendente. A
publicação do código aciona somente o CI em `main`. O deploy direto
requer acionamento manual.

## Ordem segura de provisionamento

1. Confirmar conta e região do perfil humano autorizado.
2. Consultar provider OIDC e validar audience/thumbprints.
3. Obter o ID físico da HTTP API pertencente a `feedflow-dev`.
4. Identificar bucket, owner, prefixo, criptografia e eventual chave KMS.
5. Confirmar o claim `sub` real e proteger o Environment `feedflow-dev` para `main`.
6. Criar e revisar um change set da stack bootstrap, sem executá-lo automaticamente.
7. Após aprovação, executar o bootstrap.
8. Com credenciais humanas e sem `--role-arn`, criar o change set da aplicação que migra as
   roles legadas para nomes determinísticos e boundaries; revisar substituições e executar.
9. Verificar que as quatro roles possuem a boundary correta e que as Lambdas/Scheduler usam
   essas roles.
10. Gerar manualmente o primeiro change set da aplicação informando a execution role, ainda
    sem execução automática.
11. Auditar todos os principals que podem operar a stack e revisar o change set.
12. Executar somente após aprovação explícita do Tech Lead.
13. Somente depois da associação bem-sucedida avaliar uma futura automação de CD OIDC.

Essa ordem evita conceder à execution role restrita acesso temporário às roles legadas de
nomes aleatórios. A service role não participa da migração que substitui essas roles.

## Rollback

### Bootstrap ainda não executado

Excluir apenas o change set não executado. Nenhuma mudança AWS terá ocorrido.

### Bootstrap executado, aplicação ainda não migrada

Não associar a execution role. Se for necessário remover a stack bootstrap, preservar o
provider retido e confirmar que nenhuma role utiliza as boundaries antes de excluir as
managed policies.

### Migração das roles em andamento

Permitir o rollback automático do CloudFormation. Não apagar manualmente roles antigas ou
novas. Como `RoleName` exige substituição, o change set deve ser rejeitado se indicar impacto
não compreendido nas funções. Depois de uma migração concluída, voltar às roles geradas exige
novo change set e nova substituição; não é rollback instantâneo.

### Tabela DynamoDB

`Retain` e deletion protection podem deixar a tabela anterior ou uma substituta preservada
quando um update falha. Reconciliar referências e dados manualmente; nunca remover a tabela
durante um rollback automático.

### Service role já associada

Não é possível simplesmente desassociá-la da stack. O rollback consiste em corrigir ou
reduzir suas policies na stack bootstrap e gerar novo change set da aplicação. Por isso, a
associação só ocorre depois da migração e das verificações anteriores.

## Checklist de aprovação antes da associação

- [ ] Provider OIDC existente ou criação aprovada, sem duplicidade.
- [ ] Subject real confirmado; preferir IDs imutáveis quando emitidos.
- [ ] Environment restrito a `main`, com reviewer e sem autoaprovação.
- [ ] Sessão temporária confirma a role OIDC esperada, sem chave-base armazenada.
- [ ] Bucket pertence à conta `143506099760` e não é público.
- [ ] Criptografia e eventual CMK validadas; policies KMS revisadas.
- [ ] Prefixo exato `feedflow-dev/`.
- [ ] ID da HTTP API pertence à stack `feedflow-dev`.
- [ ] Bootstrap validado e change set revisado.
- [ ] Roles migradas para nomes determinísticos com boundaries corretas.
- [ ] Tabela mostra `Retain`; deletion protection e PITR aparecem somente em change set
      isolado com `DynamoDbSafetyControlsEnabled=true`.
- [ ] Nenhuma substituição inesperada de tabela, API, tópicos ou filas.
- [ ] Principals com acesso à stack inventariados.
- [ ] Preview OIDC criou change set sem executá-lo.
- [ ] Tech Lead aprovou explicitamente a primeira associação.

## Riscos residuais

1. `PutRolePolicy` e `UpdateAssumeRolePolicy` continuam sendo ações sensíveis, embora limitadas
   por role exata e boundary.
2. Algumas APIs de listagem e Event Source Mapping exigem curingas técnicos e podem expor
   metadados da conta.
3. A execution role não consegue substituir a HTTP API; esse cenário exige break-glass.
4. Uma service role associada à stack é permanente para operações futuras.
5. `Retain`, PITR e artefatos versionados podem aumentar custos de armazenamento.
6. O bootstrap depende de fatos externos que somente consultas AWS/GitHub podem confirmar.
7. Uma permissions boundary limita permissões efetivas, mas não substitui revisão de template,
   change set, trust policies e proteção do Environment.
