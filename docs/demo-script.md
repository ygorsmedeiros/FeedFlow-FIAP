# Roteiro de gravação do FeedFlow (8 minutos)

Grave a aplicação já implantada em `feedflow-dev`, em `us-east-2`. Use as capturas da execução verificada em 16/11/2026 para os `POST` e o relatório. Repetir um `POST` cria um item e a nota 2 pode enviar outro alerta. Uma nova invocação do relatório também publica outra mensagem e pode usar outra semana.

## Abas para abrir antes de gravar

1. README no GitHub e terminal PowerShell com os comandos abaixo.
2. AWS Console em Ohio com CloudFormation (`feedflow-dev`), Lambda, API Gateway e DynamoDB (`FeedFlowFeedback`, índice `byWeek`).
3. CloudWatch Logs das funções de notificações e relatórios, EventBridge Scheduler e configuração de destino de falha.
4. CI do commit de entrega, `.github/workflows/deploy.yml`, `monitoring.yaml` e PR #1.
5. Caixa de entrada com os assuntos `[FeedFlow] Avaliacao critica recebida` e `[FeedFlow] Relatorio semanal`. No alerta original, procure “Aula confusa”. No relatório mais recente de 16/11, procure o período 09 a 15/11 e o corpo com três avaliações identificadas por `[DEMO]`.

Oculte IDs e ARNs da conta, destinatários, URLs de confirmação SNS e qualquer dado de terceiros antes de gravar ou publicar. Para o trecho de e-mail, mostre o corpo da mensagem correspondente somente se ela estiver na caixa de entrada. Os logs e as assinaturas SNS confirmadas são evidências separadas.

## Sequência da gravação

| Tempo | Tela e ação | Resultado a mostrar | Fala sugerida |
| --- | --- | --- | --- |
| 0:00–0:40 | README e diagrama. Acompanhe a seta da API ao banco e os dois fluxos assíncronos. | API recebe as notas, o Stream aciona o alerta e o Scheduler aciona o relatório. | “O FeedFlow registra avaliações de aulas, avisa a equipe sobre notas críticas e resume a semana anterior.” |
| 0:40–1:30 | Lambda, API Gateway e DynamoDB. Abra as funções API, notificações e relatórios, as rotas e o índice `byWeek`. | Três funções, `GET /health`, `POST /avaliacao`, tabela e índice. | “A API valida e grava. As outras duas funções trabalham a partir dos dados persistidos.” |
| 1:30–3:00 | PowerShell. Faça o GET e abra as três respostas HTTP salvas. | `200/UP`, `201/CRITICA` para 2, `201/BAIXA` para 9 e `400` para 11. | “Notas de zero a dez são aceitas. A urgência vem da nota. A nota 11 é rejeitada mesmo com uma descrição válida.” |
| 3:00–4:00 | DynamoDB e CloudWatch Logs da função de notificações. Abra a resposta da nota 2, a leitura do item e o alerta correspondente na caixa de entrada, se encontrado. | Item persistido com `CRITICA` e log com `publicados=1`. | “A confirmação do item vem do DynamoDB. O Stream acionou a função, e o log registrou uma publicação no SNS.” |
| 4:00–5:15 | PowerShell com a consulta salva do índice e o log do relatório. Mostre o e-mail correspondente se encontrado, ou rode a demonstração local para mostrar o corpo. | Semana de 09 a 15/11 em São Paulo. A invocação inicial teve zero itens. A amostra sintética teve 3 itens, média esperada 5,67, segunda 2, quinta 1 e uma avaliação por urgência. O log AWS registrou `total=3`. | “O relatório usa a última semana completa. Este exemplo usa três avaliações sintéticas identificadas como demonstração.” |
| 5:15–6:15 | Lambda → Event source mapping e EventBridge Scheduler → destino e retry. Mostre o estado de PITR na tabela. | Stream habilitado, falha parcial e até 3 retries na notificação. Scheduler segunda às 09:00, 2 retries e fila de falhas. API com throttling 10/s e burst 20, filas com SSE e PITR desligado. | “O alerta e o agendamento têm retentativas e destino para falhas. Na conta de avaliação, o PITR estava desativado.” |
| 6:15–7:15 | GitHub Actions, workflow manual e `monitoring.yaml`. | CI verde, 72 testes do build e 7 de integração com DynamoDB Local. Sete alarmes, dashboard e tópico operacional definidos em código. | “A integração contínua passou. O monitoramento foi validado em código, e o deploy direto ficou bloqueado pela política da organização.” |
| 7:15–8:00 | README → limitações da conta AWS. Mostre o PR em rascunho e a stack ativa. | Aplicação existente demonstrada em AWS. Atualização direta pendente. | “Os testes do vídeo usam a implantação existente. Os pacotes locais e o script foram conferidos, e a liberação do deploy depende da administração da AWS.” |

## PowerShell para a gravação

Execute a partir da raiz do repositório. Ajuste somente o caminho local de evidências se estiver em outro computador.

```powershell
$e = Join-Path $env:LOCALAPPDATA 'FeedFlow-entrega-20261116'
$awsArgs = @('--profile','feedflow','--region','us-east-2','--no-cli-pager')
$outputs = @{}
$stack = aws cloudformation describe-stacks @awsArgs --stack-name feedflow-dev | ConvertFrom-Json
$stack.Stacks[0].Outputs | ForEach-Object { $outputs[$_.OutputKey] = $_.OutputValue }
$api = $outputs['ApiUrl'].TrimEnd('/')
curl.exe -sS -i "$api/health"
Get-Content "$e/critical.http","$e/normal.http","$e/invalid.http"

$critical = Get-Content "$e/critical-db.json" -Raw | ConvertFrom-Json
$critical.Item | Select-Object @{n='Nota';e={$_.nota.N}},@{n='Urgência';e={$_.urgencia.S}}
Get-Content "$e/NotificationsLogGroup.json" | Select-String 'publicados=1'

$sample = Get-Content "$e/demo-index.json" -Raw | ConvertFrom-Json
$sample.Items | Select-Object @{n='UTC';e={$_.createdAt.S}},@{n='Nota';e={$_.nota.N}},@{n='Urgência';e={$_.urgencia.S}}
Get-Content "$e/demo-report-tail.log" | Select-String 'weekKey='
```

### Calcular a janela corrente sem invocar a função

```powershell
$zone = [TimeZoneInfo]::FindSystemTimeZoneById('America/Sao_Paulo')
$today = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow, $zone).Date
$monday = $today.AddDays(-(([int]$today.DayOfWeek + 6) % 7))
$start = $monday.AddDays(-7)
$end = $monday.AddDays(-1)
'{0:dd/MM/yyyy} a {1:dd/MM/yyyy} (São Paulo)' -f $start,$end
```

A Lambda usa o instante da execução. Ela calcula a segunda-feira anterior às 00:00 no fuso de São Paulo e usa a segunda-feira corrente às 00:00 como limite exclusivo. O índice `byWeek` tem chave de partição `weekKey` (`SEMANA#aaaa-mm-dd`) e chave de ordenação `createdAt` (UTC com nove casas decimais). A consulta paginada lê `id`, `nota`, `urgencia`, `descricao` e `createdAt`. Sem avaliações, o total é zero e a média aparece como `N/A`.

### Reproduzir o cálculo no DynamoDB Local

Com JDK 17 e DynamoDB Local disponível em `127.0.0.1:8000` (por exemplo, `docker compose up -d dynamodb-local`), rode:

```powershell
.\mvnw.cmd -B -ntp -pl feedflow-reports package
java '-Dfile.encoding=UTF-8' -cp feedflow-reports/target/feedflow-reports-1.0.0-SNAPSHOT.jar scripts/WeeklyReportDemo.java 8000
```

O exemplo usa a janela fixa de 09 a 15/11/2026. Cria e remove uma tabela temporária apenas no DynamoDB Local, consulta o índice com o repositório Java da aplicação e imprime o corpo do relatório no terminal.

## Após a entrega

Combine a remoção dos três itens sintéticos antes de executar a limpeza registrada na pasta privada. A remoção usa somente os três IDs com condição sobre descrição, semana e horário. Se a stack de monitoramento vier a ser implantada, o comando de limpeza exclusivo dessa stack, após autorização, é:

```powershell
aws cloudformation delete-stack --stack-name feedflow-dev-monitoring --profile feedflow --region us-east-2
```
