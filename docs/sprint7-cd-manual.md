# Sprint 7 — CI e CD manual

## Estado da entrega

A integração contínua está em `.github/workflows/ci.yml`. Ela é executada em pull requests e
pushes para `main`, usa Java 17, Maven Wrapper, cache Maven, build completo e testes de
integração com DynamoDB Local. O workflow possui apenas `contents: read`, não recebe
credenciais AWS e não realiza deploy.

O deploy automatizado está em `.github/workflows/deploy.yml` (OIDC). O caminho manual abaixo continua válido. Não há credencial
permanente no GitHub. O operador usa o perfil local `feedflow`, na região `us-east-2`, e cada
deploy exige preview, aprovação humana declarada e a confirmação interativa do change set.

A arquitetura OIDC está preparada em `infrastructure/oidc/github-actions-oidc.yaml`, mas não
foi provisionada. As consultas `iam:ListOpenIDConnectProviders` e
`iam:GetOpenIDConnectProvider` foram bloqueadas por SCP; portanto, a existência e a
propriedade do provider GitHub continuam sem confirmação. O bucket gerenciado pelo SAM usa
SSE-KMS, versionamento e Block Public Access, mas o ARN e a key policy da chave ainda precisam
ser validados antes de conceder acesso a uma futura role OIDC.

## Controles no template da aplicação

O `template.yaml` separa os controles de segurança que poderiam tornar o próximo deploy
disruptivo:

| Parâmetro | Padrão no CD manual | Efeito ao habilitar |
|---|---:|---|
| `RuntimeRoleHardeningEnabled` | `false` | Define nomes determinísticos e boundaries; substitui as quatro roles de runtime e exige o bootstrap OIDC já provisionado. |
| `DynamoDbSafetyControlsEnabled` | `false` | Habilita deletion protection e PITR na tabela existente; altera configuração e custo de backup. |

O script manual passa ambos explicitamente como `false`. Assim, o próximo change set não deve
tentar aplicar boundaries inexistentes, substituir as quatro roles ou habilitar PITR sem
aprovação. O preview OIDC futuro passa ambos como `true`, pois representa uma migração
deliberada e separada.

`DeletionPolicy: Retain` e `UpdateReplacePolicy: Retain` permanecem na tabela. Esses atributos
são políticas do CloudFormation, não habilitam PITR e não exigem substituir a tabela. Eles
reduzem o risco de perda em remoção ou substituição, mas podem deixar uma tabela órfã e com
custo ativo. A proteção não elimina a necessidade de rejeitar qualquer change set que mostre
substituição da `FeedFlowFeedback`.

## Fluxo operacional de CD manual

### 1. Pré-condições

- working tree e revisão técnica aprovados;
- CI verde no commit candidato;
- Java 17, AWS CLI e AWS SAM CLI instalados;
- perfil `feedflow` apontando para a conta aprovada e região `us-east-2`;
- nenhuma execução paralela na stack `feedflow-dev`;
- parâmetros de segurança no preview iguais a `false`.

### 2. Build e validação local

```powershell
./scripts/build-and-test.ps1 -IncludeDynamoDbLocalTests
./scripts/validate-sam.ps1
```

O primeiro comando executa `clean verify`, valida os três ZIPs e, quando Docker está
disponível, inicializa o DynamoDB Local e executa o perfil de integração. O segundo executa
`sam validate --lint` e `sam build --no-cached`, e confirma que o build usa os mesmos ZIPs.

### 3. Preview obrigatório

O comando abaixo cria um change set e encerra sem executá-lo:

```powershell
./scripts/deploy-aws.ps1 -Authorization PREVIEW -PreviewOnly
```

O modo usa `--no-execute-changeset`. Ele não usa `--no-confirm-changeset` como proteção. O
operador deve registrar o nome do change set e inspecioná-lo no console ou, somente após
autorização para consultas AWS, com:

```powershell
aws cloudformation describe-change-set `
  --stack-name feedflow-dev `
  --change-set-name '<change-set>' `
  --profile feedflow `
  --region us-east-2
```

Critérios de aprovação:

- nenhuma remoção ou substituição inesperada;
- nenhuma substituição das quatro roles de runtime;
- nenhuma alteração funcional em `GET /health` e `POST /avaliacao`;
- `FeedFlowFeedback` sem substituição;
- parâmetros `RuntimeRoleHardeningEnabled=false` e
  `DynamoDbSafetyControlsEnabled=false`;
- somente código e mudanças já aprovadas pelo Tech Lead.

### 4. Deploy com segunda aprovação

Depois da revisão do preview, o operador declara essa aprovação e inicia o fluxo normal:

```powershell
./scripts/deploy-aws.ps1 `
  -Authorization DEPLOY `
  -ChangeSetPreviewApproved
```

O script repete build e validação, exibe stack, perfil, região, tabela e hashes dos três ZIPs,
cria o change set e mantém `--confirm-changeset`. O operador deve comparar o novo resumo com o
preview e responder `Y` somente se não houver divergência. Falha do Maven, da validação SAM ou
de qualquer script filho interrompe o processo mesmo quando o filho apenas retorna exit code
não zero.

### 5. Validação pós-deploy

Após o CloudFormation terminar, executar o script somente de leitura:

```powershell
./scripts/validate-aws-deployment.ps1 -Authorization VALIDATE
```

Ele exige e verifica:

- stack em `UPDATE_COMPLETE`;
- `GET /health` com HTTP 200, `status=UP` e `application=FeedFlow`;
- três Lambdas `Active` e com `LastUpdateStatus=Successful`;
- tabela, GSI `byWeek` e DynamoDB Stream ativos;
- PITR no estado esperado (`DISABLED` por padrão);
- dois tópicos SNS e duas filas SQS acessíveis;
- Scheduler habilitado e apontando para a Lambda de relatórios.

Se a futura mudança de segurança habilitar PITR, usar explicitamente:

```powershell
./scripts/validate-aws-deployment.ps1 `
  -Authorization VALIDATE `
  -ExpectedPitrStatus ENABLED
```

O script não publica SNS, não envia mensagens SQS, não invoca Lambdas e não altera recursos.

## Rollback e recuperação

Os comandos abaixo são procedimentos documentados; não foram executados nesta fase.

Antes de agir, capturar eventos e identificar o primeiro erro:

```powershell
aws cloudformation describe-stack-events `
  --stack-name feedflow-dev `
  --profile feedflow `
  --region us-east-2
```

Se a stack ainda estiver em `UPDATE_IN_PROGRESS`, uma pessoa autorizada pode cancelar o update:

```powershell
aws cloudformation cancel-update-stack `
  --stack-name feedflow-dev `
  --profile feedflow `
  --region us-east-2
```

O cancelamento inicia rollback. Pode haver indisponibilidade e recursos recém-criados podem
ser removidos. Não cancelar depois de `UPDATE_COMPLETE`; nesse caso, preparar um novo preview
com a versão anterior conhecida e executar outro update aprovado.

Se o rollback automático parar em `UPDATE_ROLLBACK_FAILED`, corrigir primeiro a causa e então:

```powershell
aws cloudformation continue-update-rollback `
  --stack-name feedflow-dev `
  --profile feedflow `
  --region us-east-2
```

Não usar `--resources-to-skip` ou `--force-rollback` sem análise por recurso. Itens ignorados
podem ficar divergentes do template e bloquear updates futuros. `rollback-stack` só deve ser
considerado em estados de falha compatíveis e com aprovação explícita; sem um estado estável
anterior, a operação pode excluir a stack.

Nunca usar `delete-stack` como rollback. `Retain` preserva a tabela, mas os demais recursos não
possuem a mesma garantia. Para código Lambda, o rollback recomendado é reconstruir uma versão
anterior imutável, gerar novo preview e executar um novo change set aprovado.

## OIDC e KMS: pendências externas

O bloqueio SCP impede concluir localmente a descoberta do provider. O bootstrap não deve ser
executado até que uma equipe com permissão confirme provider, owner e audience sem criar um
duplicado. A futura role OIDC também precisa de permissões na chave KMS específica e de uma
key policy compatível. Para upload SSE-KMS, a necessidade mínima típica inclui
`kms:GenerateDataKey`; downloads e multipart uploads podem exigir `kms:Decrypt`. Nenhuma
permissão `kms:*` genérica deve ser adicionada.

Não associar a CloudFormation execution role à stack existente antes de:

1. confirmar provider, `sub`, Environment e proteção da branch `main`;
2. identificar o ARN e a política da chave KMS;
3. provisionar e validar as boundaries;
4. revisar separadamente o change set que substitui as quatro roles;
5. habilitar PITR/proteção de exclusão em change set independente, com custo aprovado;
6. validar rollback e inventariar todos os principals capazes de operar a stack.

Referências AWS: [preview do SAM](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/sam-cli-command-reference-sam-deploy.html),
[cancelamento de update](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-stack-update-cancel.html),
[continuação de rollback](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-continueupdaterollback.html)
e [SSE-KMS no S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingKMSEncryption.html).

## Aprovações ainda necessárias

- Tech Lead aprovar os parâmetros opt-in e a manutenção das políticas `Retain`.
- Segurança/AWS confirmar provider OIDC e desbloquear consultas somente de leitura ou fornecer
  evidência equivalente.
- Dono da chave KMS aprovar policy e permissões mínimas por ARN.
- Tech Lead aprovar separadamente a migração das roles e a ativação de PITR.
- Operador humano aprovar cada preview e a confirmação interativa de cada deploy.
