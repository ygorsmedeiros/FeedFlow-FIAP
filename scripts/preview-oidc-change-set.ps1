[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('PREVIEW')]
    [string]$Authorization,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$')]
    [string]$ArtifactBucket,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^arn:aws:iam::143506099760:role/feedflow-cloudformation-execution$')]
    [string]$CloudFormationExecutionRoleArn,

    [ValidateSet('feedflow-dev')]
    [string]$StackName = 'feedflow-dev',

    [ValidateSet('feedflow-dev')]
    [string]$ArtifactPrefix = 'feedflow-dev',

    [ValidateSet('us-east-2')]
    [string]$Region = 'us-east-2',

    [ValidateSet('FeedFlowFeedback')]
    [string]$TableName = 'FeedFlowFeedback'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Resolve-SamCli {
    $command = Get-Command sam -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    if ($IsWindows) {
        $knownPath = 'C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd'
        if (Test-Path -LiteralPath $knownPath) {
            return $knownPath
        }
    }

    throw 'AWS SAM CLI não encontrada. Instale-a no runner antes do preview.'
}

function Assert-TemporaryCredentials {
    $requiredVariables = @(
        'AWS_ACCESS_KEY_ID'
        'AWS_SECRET_ACCESS_KEY'
        'AWS_SESSION_TOKEN'
    )

    foreach ($variableName in $requiredVariables) {
        $value = [Environment]::GetEnvironmentVariable($variableName)
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Credencial temporária ausente: $variableName. Configure OIDC antes do preview."
        }
    }
}

function Assert-LambdaArtifact {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Artefato da Lambda $Name não encontrado: $Path"
    }

    if ((Get-Item -LiteralPath $Path).Length -le 0) {
        throw "Artefato da Lambda $Name está vazio: $Path"
    }
}

if ($Authorization -ne 'PREVIEW') {
    throw 'O preview OIDC exige -Authorization PREVIEW.'
}

Assert-TemporaryCredentials
$samCli = Resolve-SamCli
$template = Join-Path $repositoryRoot 'template.yaml'

Assert-LambdaArtifact `
    -Path (Join-Path $repositoryRoot 'feedflow-api/target/function.zip') `
    -Name 'API'
Assert-LambdaArtifact `
    -Path (Join-Path $repositoryRoot 'feedflow-notifications/target/function.zip') `
    -Name 'notificações'
Assert-LambdaArtifact `
    -Path (Join-Path $repositoryRoot 'feedflow-reports/target/function.zip') `
    -Name 'relatórios'

Write-Host 'Preview OIDC preparado com credenciais temporárias:'
Write-Host "Stack: $StackName"
Write-Host "Região: $Region"
Write-Host "Bucket: $ArtifactBucket"
Write-Host "Prefixo: $ArtifactPrefix"
Write-Host "Execution role: $CloudFormationExecutionRoleArn"
Write-Host 'O change set será criado, mas não será executado.'

Push-Location $repositoryRoot
try {
    $global:LASTEXITCODE = 0
    & $samCli validate --lint --template-file $template --region $Region
    $validationSucceeded = $?
    $validationExitCode = $LASTEXITCODE
    if (-not $validationSucceeded -or $validationExitCode -ne 0) {
        throw "A validação SAM falhou. Exit code: $validationExitCode."
    }

    $samArguments = @(
        'deploy'
        '--template-file'
        $template
        '--stack-name'
        $StackName
        '--region'
        $Region
        '--capabilities'
        'CAPABILITY_NAMED_IAM'
        '--s3-bucket'
        $ArtifactBucket
        '--s3-prefix'
        $ArtifactPrefix
        '--role-arn'
        $CloudFormationExecutionRoleArn
        '--no-execute-changeset'
        '--no-fail-on-empty-changeset'
        '--parameter-overrides'
        'EnvironmentName=dev'
        "FeedbackTableName=$TableName"
        'LogRetentionDays=14'
        'RuntimeRoleHardeningEnabled=true'
        'DynamoDbSafetyControlsEnabled=true'
    )

    $global:LASTEXITCODE = 0
    & $samCli @samArguments
    $previewSucceeded = $?
    $previewExitCode = $LASTEXITCODE
    if (-not $previewSucceeded -or $previewExitCode -ne 0) {
        throw "A criação do change set falhou. Exit code: $previewExitCode."
    }

    Write-Host 'Change set criado sem execução automática.'
    Write-Host 'A execução exige uma etapa separada e aprovação do GitHub Environment.'
}
finally {
    Pop-Location
}
