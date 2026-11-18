[CmdletBinding()]
param()

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

$samCli = Resolve-SamCli
$template = Join-Path $repositoryRoot 'template.yaml'
$functionZip = Join-Path $repositoryRoot 'feedflow-api\target\function.zip'
$notificationsFunctionZip = Join-Path $repositoryRoot `
    'feedflow-notifications\target\function.zip'
$reportsFunctionZip = Join-Path $repositoryRoot 'feedflow-reports\target\function.zip'

if (-not (Test-Path -LiteralPath $template)) {
    throw 'template.yaml não foi encontrado na raiz do repositório.'
}
if (-not (Test-Path -LiteralPath $functionZip)) {
    throw 'Execute scripts/build-and-test.ps1 antes da validação SAM.'
}
if ((Get-Item -LiteralPath $functionZip).Length -le 0) {
    throw 'O function.zip está vazio.'
}
if (-not (Test-Path -LiteralPath $notificationsFunctionZip)) {
    throw 'O function.zip de notificações não existe. Execute scripts/build-and-test.ps1 antes da validação SAM.'
}
if ((Get-Item -LiteralPath $notificationsFunctionZip).Length -le 0) {
    throw 'O function.zip de notificações está vazio.'
}
if (-not (Test-Path -LiteralPath $reportsFunctionZip)) {
    throw 'O function.zip de relatórios não existe. Execute scripts/build-and-test.ps1 antes da validação SAM.'
}
if ((Get-Item -LiteralPath $reportsFunctionZip).Length -le 0) {
    throw 'O function.zip de relatórios está vazio.'
}

$templateContent = Get-Content -Raw -LiteralPath $template
if ($templateContent -notmatch '(?m)^\s+CodeUri:\s+feedflow-api/target/function\.zip\s*$') {
    throw 'O template SAM não referencia feedflow-api/target/function.zip.'
}
if ($templateContent -notmatch '(?m)^\s+CodeUri:\s+feedflow-notifications/target/function\.zip\s*$') {
    throw 'O template SAM não referencia o function.zip de notificações gerado pelo Maven.'
}
if ($templateContent -notmatch '(?m)^\s+CodeUri:\s+feedflow-reports/target/function\.zip\s*$') {
    throw 'O template SAM não referencia o function.zip de relatórios gerado pelo Maven.'
}

$functionZipHashBeforeBuild = (Get-FileHash -Algorithm SHA256 -LiteralPath $functionZip).Hash
$notificationsFunctionZipHashBeforeBuild = `
    (Get-FileHash -Algorithm SHA256 -LiteralPath $notificationsFunctionZip).Hash
$reportsFunctionZipHashBeforeBuild = `
    (Get-FileHash -Algorithm SHA256 -LiteralPath $reportsFunctionZip).Hash

Push-Location $repositoryRoot
try {
    $global:LASTEXITCODE = 0
    & $samCli --version
    $samVersionSucceeded = $?
    $samVersionExitCode = $LASTEXITCODE
    if (-not $samVersionSucceeded -or $samVersionExitCode -ne 0) {
        throw "Não foi possível executar a AWS SAM CLI. Exit code: $samVersionExitCode."
    }

    $global:LASTEXITCODE = 0
    & $samCli validate --lint --template-file $template --region us-east-2
    $samValidateSucceeded = $?
    $samValidateExitCode = $LASTEXITCODE
    if (-not $samValidateSucceeded -or $samValidateExitCode -ne 0) {
        throw "sam validate --lint falhou. Exit code: $samValidateExitCode."
    }

    $samBuildStartedAtUtc = [DateTime]::UtcNow
    $global:LASTEXITCODE = 0
    & $samCli build --template-file $template --no-cached
    $samBuildSucceeded = $?
    $samBuildExitCode = $LASTEXITCODE
    if (-not $samBuildSucceeded -or $samBuildExitCode -ne 0) {
        throw "sam build falhou. Exit code: $samBuildExitCode."
    }

    $builtTemplate = Join-Path $repositoryRoot '.aws-sam\build\template.yaml'
    if (-not (Test-Path -LiteralPath $builtTemplate)) {
        throw 'O sam build não gerou .aws-sam/build/template.yaml.'
    }
    if ((Get-Item -LiteralPath $builtTemplate).LastWriteTimeUtc -lt $samBuildStartedAtUtc.AddSeconds(-2)) {
        throw 'O template em .aws-sam/build não foi atualizado pelo sam build atual.'
    }

    $functionZipHashAfterBuild = (Get-FileHash -Algorithm SHA256 -LiteralPath $functionZip).Hash
    if ($functionZipHashBeforeBuild -ne $functionZipHashAfterBuild) {
        throw 'O function.zip foi alterado durante a validação SAM.'
    }

    $notificationsFunctionZipHashAfterBuild = `
        (Get-FileHash -Algorithm SHA256 -LiteralPath $notificationsFunctionZip).Hash
    if ($notificationsFunctionZipHashBeforeBuild -ne $notificationsFunctionZipHashAfterBuild) {
        throw 'O function.zip de notificações foi alterado durante a validação SAM.'
    }

    $reportsFunctionZipHashAfterBuild = `
        (Get-FileHash -Algorithm SHA256 -LiteralPath $reportsFunctionZip).Hash
    if ($reportsFunctionZipHashBeforeBuild -ne $reportsFunctionZipHashAfterBuild) {
        throw 'O function.zip de relatórios foi alterado durante a validação SAM.'
    }

    Write-Output "SamFunctionZipSha256=$functionZipHashAfterBuild"
    Write-Output "SamNotificationsFunctionZipSha256=$notificationsFunctionZipHashAfterBuild"
    Write-Output "SamReportsFunctionZipSha256=$reportsFunctionZipHashAfterBuild"
}
finally {
    Pop-Location
}
