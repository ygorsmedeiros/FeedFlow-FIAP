[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('PREVIEW', 'DEPLOY')]
    [string]$Authorization,

    [string]$StackName = 'feedflow-dev',
    [string]$Profile = 'feedflow',
    [string]$Region = 'us-east-2',
    [string]$TableName = 'FeedFlowFeedback',
    [string]$AdminEmail = '',
    [string]$JavaHome = 'E:\Program Files\Java\jdk-17',
    [switch]$PreviewOnly,
    [switch]$ChangeSetPreviewApproved
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Resolve-SamCli {
    $command = Get-Command sam -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $knownPath = 'C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd'
    if (Test-Path -LiteralPath $knownPath) {
        return $knownPath
    }

    throw 'AWS SAM CLI não encontrada no PATH nem no caminho padrão do Windows.'
}

function Invoke-RequiredScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [hashtable]$Parameters = @{},

        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    $global:LASTEXITCODE = 0
    & $Path @Parameters
    $childScriptSucceeded = $?
    $childScriptExitCode = $LASTEXITCODE

    if (-not $childScriptSucceeded -or $childScriptExitCode -ne 0) {
        throw "$FailureMessage Exit code: $childScriptExitCode."
    }
}

if ($PreviewOnly -and $Authorization -ne 'PREVIEW') {
    throw 'O preview exige -Authorization PREVIEW.'
}

if (-not $PreviewOnly -and $Authorization -ne 'DEPLOY') {
    throw 'O deploy exige -Authorization DEPLOY.'
}

if (-not $PreviewOnly -and -not $ChangeSetPreviewApproved) {
    throw 'O deploy exige -ChangeSetPreviewApproved após a revisão humana do preview.'
}

$samCli = Resolve-SamCli

Push-Location $repositoryRoot
try {
    Invoke-RequiredScript `
        -Path .\scripts\build-and-test.ps1 `
        -Parameters @{ JavaHome = $JavaHome } `
        -FailureMessage 'O script de build e testes falhou. O deploy foi interrompido.'

    Invoke-RequiredScript `
        -Path .\scripts\validate-sam.ps1 `
        -FailureMessage 'O script de validação SAM falhou. O deploy foi interrompido.'

    $functionZip = Join-Path $repositoryRoot 'feedflow-api\target\function.zip'
    if (-not (Test-Path -LiteralPath $functionZip)) {
        throw 'O function.zip validado não foi encontrado. O deploy foi interrompido.'
    }
    $functionZipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $functionZip).Hash
    $notificationsFunctionZip = Join-Path $repositoryRoot `
        'feedflow-notifications\target\function.zip'
    if (-not (Test-Path -LiteralPath $notificationsFunctionZip)) {
        throw 'O function.zip de notificações validado não foi encontrado. O deploy foi interrompido.'
    }
    $notificationsFunctionZipHash = `
        (Get-FileHash -Algorithm SHA256 -LiteralPath $notificationsFunctionZip).Hash
    $reportsFunctionZip = Join-Path $repositoryRoot 'feedflow-reports\target\function.zip'
    if (-not (Test-Path -LiteralPath $reportsFunctionZip)) {
        throw 'O function.zip de relatórios validado não foi encontrado. O deploy foi interrompido.'
    }
    $reportsFunctionZipHash = `
        (Get-FileHash -Algorithm SHA256 -LiteralPath $reportsFunctionZip).Hash

    Write-Host ''
    $operationName = if ($PreviewOnly) { 'preview do change set' } else { 'deploy' }
    Write-Host "Parâmetros do $operationName aguardando confirmação:"
    Write-Host "Stack: $StackName"
    Write-Host "Perfil: $Profile"
    Write-Host "Região: $Region"
    Write-Host "Tabela: $TableName"
    Write-Host "Artefato API: $functionZip"
    Write-Host "SHA-256 API: $functionZipHash"
    Write-Host "Artefato notificações: $notificationsFunctionZip"
    Write-Host "SHA-256 notificações: $notificationsFunctionZipHash"
    Write-Host "Artefato relatórios: $reportsFunctionZip"
    Write-Host "SHA-256 relatórios: $reportsFunctionZipHash"
    Write-Host ''

    $samArguments = @(
        'deploy'
        '--template-file'
        'template.yaml'
        '--stack-name'
        $StackName
        '--profile'
        $Profile
        '--region'
        $Region
        '--capabilities'
        'CAPABILITY_NAMED_IAM'
        '--resolve-s3'
    )

    if ($PreviewOnly) {
        Write-Host 'Modo preview: o SAM criará o change set e encerrará sem executá-lo.'
        $samArguments += '--no-execute-changeset'
    }
    else {
        Write-Host 'O SAM criará um change set, exibirá as mudanças e pedirá nova confirmação antes de executá-las.'
        $samArguments += '--confirm-changeset'
    }

    $samArguments += @(
        '--no-fail-on-empty-changeset'
        '--parameter-overrides'
        'EnvironmentName=dev'
        "FeedbackTableName=$TableName"
        'LogRetentionDays=14'
        'RuntimeRoleHardeningEnabled=false'
        'DynamoDbSafetyControlsEnabled=false'
        "AdminEmail=$AdminEmail"
    )

    $global:LASTEXITCODE = 0
    & $samCli @samArguments

    $samOperationSucceeded = $?
    $samOperationExitCode = $LASTEXITCODE
    if (-not $samOperationSucceeded -or $samOperationExitCode -ne 0) {
        throw "A operação SAM '$operationName' não foi concluída. Exit code: $samOperationExitCode."
    }

    if ($PreviewOnly) {
        Write-Host 'Preview concluído. O change set foi criado, mas não foi executado.'
    }
}
finally {
    Pop-Location
}
