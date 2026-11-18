[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('VALIDATE')]
    [string]$Authorization,

    [ValidateSet('feedflow-dev')]
    [string]$StackName = 'feedflow-dev',

    [ValidateSet('feedflow')]
    [string]$Profile = 'feedflow',

    [ValidateSet('us-east-2')]
    [string]$Region = 'us-east-2',

    [ValidateSet('DISABLED', 'ENABLED')]
    [string]$ExpectedPitrStatus = 'DISABLED'
)

$ErrorActionPreference = 'Stop'

function Resolve-AwsCli {
    $command = Get-Command aws -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $knownPath = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe'
    if (Test-Path -LiteralPath $knownPath) {
        return $knownPath
    }

    throw 'AWS CLI não encontrada no PATH nem no caminho padrão do Windows.'
}

function Invoke-AwsJson {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Operation
    )

    $global:LASTEXITCODE = 0
    $rawOutput = & $script:AwsCli @Arguments 2>&1
    $commandSucceeded = $?
    $commandExitCode = $LASTEXITCODE

    if (-not $commandSucceeded -or $commandExitCode -ne 0) {
        $diagnostic = ($rawOutput | Out-String).Trim()
        throw "$Operation falhou. Exit code: $commandExitCode. $diagnostic"
    }

    try {
        return ($rawOutput | Out-String | ConvertFrom-Json)
    }
    catch {
        throw "$Operation retornou JSON inválido. $($_.Exception.Message)"
    }
}

function Get-RequiredStackOutput {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Outputs,

        [Parameter(Mandatory = $true)]
        [string]$Key
    )

    $output = $Outputs | Where-Object { $_.OutputKey -eq $Key } | Select-Object -First 1
    if (-not $output -or [string]::IsNullOrWhiteSpace($output.OutputValue)) {
        throw "Output obrigatório ausente na stack: $Key"
    }

    return $output.OutputValue
}

function Write-ValidationSuccess {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

if ($Authorization -ne 'VALIDATE') {
    throw 'A validação pós-deploy exige -Authorization VALIDATE.'
}

$script:AwsCli = Resolve-AwsCli
$commonArguments = @('--profile', $Profile, '--region', $Region, '--output', 'json')

Write-Host 'Validação pós-deploy somente de leitura:'
Write-Host "Stack: $StackName"
Write-Host "Perfil: $Profile"
Write-Host "Região: $Region"
Write-Host "PITR esperado: $ExpectedPitrStatus"

$stackResponse = Invoke-AwsJson `
    -Arguments (@('cloudformation', 'describe-stacks', '--stack-name', $StackName) + $commonArguments) `
    -Operation 'Consulta da stack CloudFormation'
$stack = $stackResponse.Stacks | Select-Object -First 1
if (-not $stack -or $stack.StackStatus -ne 'UPDATE_COMPLETE') {
    $actualStatus = if ($stack) { $stack.StackStatus } else { '<ausente>' }
    throw "A stack não está em UPDATE_COMPLETE. Estado atual: $actualStatus"
}
Write-ValidationSuccess 'CloudFormation em UPDATE_COMPLETE.'

$healthUrl = Get-RequiredStackOutput -Outputs $stack.Outputs -Key 'HealthUrl'
$healthResponse = Invoke-WebRequest -Method Get -Uri $healthUrl -TimeoutSec 30 -UseBasicParsing
if ($healthResponse.StatusCode -ne 200) {
    throw "GET /health retornou HTTP $($healthResponse.StatusCode)."
}
$healthBody = $healthResponse.Content | ConvertFrom-Json
if ($healthBody.status -ne 'UP' -or $healthBody.application -ne 'FeedFlow') {
    throw 'GET /health retornou um payload inesperado.'
}
Write-ValidationSuccess 'GET /health retornou HTTP 200 e o payload esperado.'

$functionOutputKeys = @(
    'ApiFunctionName'
    'NotificationsFunctionName'
    'ReportsFunctionName'
)
$functionConfigurations = @{}
foreach ($outputKey in $functionOutputKeys) {
    $functionName = Get-RequiredStackOutput -Outputs $stack.Outputs -Key $outputKey
    $configuration = Invoke-AwsJson `
        -Arguments (@('lambda', 'get-function-configuration', '--function-name', $functionName) + $commonArguments) `
        -Operation "Consulta da Lambda $functionName"
    if ($configuration.State -ne 'Active' -or $configuration.LastUpdateStatus -ne 'Successful') {
        throw "Lambda $functionName não está pronta. State=$($configuration.State), LastUpdateStatus=$($configuration.LastUpdateStatus)."
    }
    $functionConfigurations[$outputKey] = $configuration
    Write-ValidationSuccess "Lambda $functionName ativa e com último update bem-sucedido."
}

$tableName = Get-RequiredStackOutput -Outputs $stack.Outputs -Key 'FeedbackTable'
$tableResponse = Invoke-AwsJson `
    -Arguments (@('dynamodb', 'describe-table', '--table-name', $tableName) + $commonArguments) `
    -Operation "Consulta da tabela $tableName"
if ($tableResponse.Table.TableStatus -ne 'ACTIVE') {
    throw "Tabela $tableName não está ACTIVE. Estado: $($tableResponse.Table.TableStatus)."
}
$weeklyIndex = $tableResponse.Table.GlobalSecondaryIndexes |
    Where-Object { $_.IndexName -eq 'byWeek' } |
    Select-Object -First 1
if (-not $weeklyIndex -or $weeklyIndex.IndexStatus -ne 'ACTIVE') {
    throw 'O GSI byWeek não existe ou não está ACTIVE.'
}
if ([string]::IsNullOrWhiteSpace($tableResponse.Table.LatestStreamArn)) {
    throw 'O DynamoDB Stream esperado não está habilitado.'
}
Write-ValidationSuccess "Tabela $tableName, GSI byWeek e Stream estão ativos."

$backupResponse = Invoke-AwsJson `
    -Arguments (@('dynamodb', 'describe-continuous-backups', '--table-name', $tableName) + $commonArguments) `
    -Operation "Consulta do PITR da tabela $tableName"
$actualPitrStatus = $backupResponse.ContinuousBackupsDescription.PointInTimeRecoveryDescription.PointInTimeRecoveryStatus
if ($actualPitrStatus -ne $ExpectedPitrStatus) {
    throw "PITR divergente. Esperado=$ExpectedPitrStatus, atual=$actualPitrStatus."
}
Write-ValidationSuccess "PITR no estado esperado: $actualPitrStatus."

foreach ($topicOutputKey in @('CriticalAlertsTopicArn', 'WeeklyReportsTopicArn')) {
    $topicArn = Get-RequiredStackOutput -Outputs $stack.Outputs -Key $topicOutputKey
    $null = Invoke-AwsJson `
        -Arguments (@('sns', 'get-topic-attributes', '--topic-arn', $topicArn) + $commonArguments) `
        -Operation "Consulta do tópico $topicOutputKey"
    Write-ValidationSuccess "Tópico SNS acessível: $topicOutputKey."
}

foreach ($queueOutputKey in @('NotificationsFailureQueueUrl', 'ReportsFailureQueueUrl')) {
    $queueUrl = Get-RequiredStackOutput -Outputs $stack.Outputs -Key $queueOutputKey
    $null = Invoke-AwsJson `
        -Arguments (@(
            'sqs', 'get-queue-attributes',
            '--queue-url', $queueUrl,
            '--attribute-names', 'QueueArn', 'ApproximateNumberOfMessages'
        ) + $commonArguments) `
        -Operation "Consulta da fila $queueOutputKey"
    Write-ValidationSuccess "Fila SQS acessível: $queueOutputKey."
}

$scheduleName = "$StackName-weekly-report"
$schedule = Invoke-AwsJson `
    -Arguments (@('scheduler', 'get-schedule', '--name', $scheduleName) + $commonArguments) `
    -Operation "Consulta do Scheduler $scheduleName"
$reportsFunctionArn = $functionConfigurations['ReportsFunctionName'].FunctionArn
if ($schedule.State -ne 'ENABLED' -or $schedule.Target.Arn -ne $reportsFunctionArn) {
    throw "Scheduler inválido. State=$($schedule.State), Target=$($schedule.Target.Arn)."
}
Write-ValidationSuccess 'Scheduler semanal habilitado e apontando para a Lambda de relatórios.'

Write-Host 'Validação pós-deploy concluída sem operações de escrita na AWS.' -ForegroundColor Green
