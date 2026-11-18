[CmdletBinding()]
param(
    [string]$JavaHome = 'E:\Program Files\Java\jdk-17',
    [switch]$IncludeDynamoDbLocalTests
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JavaHome 'bin\java.exe'

function Assert-LambdaPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [DateTime]$BuildStartedAtUtc
    )

    $artifact = Get-Item -LiteralPath $Path
    if ($artifact.Length -le 0) {
        throw 'O function.zip foi gerado vazio.'
    }
    if ($artifact.LastWriteTimeUtc -lt $BuildStartedAtUtc.AddSeconds(-2)) {
        throw 'O function.zip não foi atualizado pelo build Maven atual.'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact.FullName)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $requiredApplicationClasses = @(
            'br/com/feedflow/api/HealthResource.class',
            'br/com/feedflow/avaliacao/api/AvaliacaoResource.class',
            'br/com/feedflow/avaliacao/persistence/DynamoDbAvaliacaoRepository.class'
        )

        foreach ($requiredClass in $requiredApplicationClasses) {
            if ($entryNames -notcontains $requiredClass) {
                throw "Classe obrigatória ausente do function.zip: $requiredClass"
            }
        }

        $requiredLibraryPatterns = @(
            '^lib/io\.quarkus\.quarkus-amazon-lambda-http-.*\.jar$',
            '^lib/software\.amazon\.awssdk\.dynamodb-.*\.jar$',
            '^lib/software\.amazon\.awssdk\.url-connection-client-.*\.jar$'
        )

        foreach ($libraryPattern in $requiredLibraryPatterns) {
            if (-not ($entryNames | Where-Object { $_ -match $libraryPattern })) {
                throw "Dependência obrigatória ausente do function.zip: $libraryPattern"
            }
        }

        $handlerClass = 'io/quarkus/amazon/lambda/runtime/QuarkusStreamHandler.class'
        $handlerFound = $false
        $handlerLibraries = $archive.Entries | Where-Object {
            $_.FullName -match '^lib/io\.quarkus\.quarkus-amazon-lambda-.*\.jar$'
        }

        foreach ($handlerLibrary in $handlerLibraries) {
            $memory = [System.IO.MemoryStream]::new()
            $libraryStream = $handlerLibrary.Open()
            try {
                $libraryStream.CopyTo($memory)
            }
            finally {
                $libraryStream.Dispose()
            }

            $memory.Position = 0
            $libraryArchive = [System.IO.Compression.ZipArchive]::new(
                $memory,
                [System.IO.Compression.ZipArchiveMode]::Read)
            try {
                if ($libraryArchive.GetEntry($handlerClass)) {
                    $handlerFound = $true
                    break
                }
            }
            finally {
                $libraryArchive.Dispose()
                $memory.Dispose()
            }
        }

        if (-not $handlerFound) {
            throw "Handler Quarkus ausente do function.zip: $handlerClass"
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Assert-NotificationsPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [DateTime]$BuildStartedAtUtc
    )

    $artifact = Get-Item -LiteralPath $Path
    if ($artifact.Length -le 0) {
        throw 'O JAR da Lambda de notificações foi gerado vazio.'
    }
    if ($artifact.LastWriteTimeUtc -lt $BuildStartedAtUtc.AddSeconds(-2)) {
        throw 'O JAR da Lambda de notificações não foi atualizado pelo build Maven atual.'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact.FullName)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $requiredEntries = @(
            'br/com/feedflow/notifications/handler/CriticalNotificationHandler.class',
            'br/com/feedflow/notifications/processing/CriticalNotificationProcessor.class',
            'br/com/feedflow/notifications/publishing/SnsAlertPublisher.class',
            'com/amazonaws/services/lambda/runtime/RequestHandler.class',
            'software/amazon/awssdk/services/sns/SnsClient.class',
            'software/amazon/awssdk/http/urlconnection/UrlConnectionHttpClient.class'
        )

        foreach ($requiredEntry in $requiredEntries) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "Classe ou dependência obrigatória ausente do JAR de notificações: $requiredEntry"
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Assert-ReportsPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [DateTime]$BuildStartedAtUtc
    )

    $artifact = Get-Item -LiteralPath $Path
    if ($artifact.Length -le 0) {
        throw 'O JAR da Lambda de relatórios foi gerado vazio.'
    }
    if ($artifact.LastWriteTimeUtc -lt $BuildStartedAtUtc.AddSeconds(-2)) {
        throw 'O JAR da Lambda de relatórios não foi atualizado pelo build Maven atual.'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact.FullName)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $requiredEntries = @(
            'br/com/feedflow/reports/handler/WeeklyReportHandler.class',
            'br/com/feedflow/reports/application/WeeklyReportService.class',
            'br/com/feedflow/reports/repository/DynamoDbFeedbackReportRepository.class',
            'br/com/feedflow/reports/publishing/SnsReportPublisher.class',
            'com/amazonaws/services/lambda/runtime/RequestHandler.class',
            'software/amazon/awssdk/services/dynamodb/DynamoDbClient.class',
            'software/amazon/awssdk/services/sns/SnsClient.class',
            'software/amazon/awssdk/http/urlconnection/UrlConnectionHttpClient.class'
        )

        foreach ($requiredEntry in $requiredEntries) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "Classe ou dependência obrigatória ausente do JAR de relatórios: $requiredEntry"
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "JDK 17 não encontrado em '$JavaHome'. Informe -JavaHome com o caminho correto."
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$(Join-Path $JavaHome 'bin');$env:Path"

Push-Location $repositoryRoot
try {
    & $javaExecutable -version
    $javaSucceeded = $?
    $javaExitCode = $LASTEXITCODE
    if (-not $javaSucceeded -or $javaExitCode -ne 0) {
        throw "Não foi possível executar o JDK 17. Exit code: $javaExitCode."
    }

    $buildStartedAtUtc = [DateTime]::UtcNow
    $global:LASTEXITCODE = 0
    & .\mvnw.cmd clean verify
    $mavenSucceeded = $?
    $mavenExitCode = $LASTEXITCODE
    if (-not $mavenSucceeded -or $mavenExitCode -ne 0) {
        throw "O build Maven ou os testes rápidos falharam. Exit code: $mavenExitCode."
    }

    if ($IncludeDynamoDbLocalTests) {
        $global:LASTEXITCODE = 0
        & .\scripts\initialize-dynamodb-local.ps1
        $dynamoInitializationSucceeded = $?
        $dynamoInitializationExitCode = $LASTEXITCODE
        if (-not $dynamoInitializationSucceeded -or $dynamoInitializationExitCode -ne 0) {
            throw "A inicialização do DynamoDB Local falhou. Exit code: $dynamoInitializationExitCode."
        }

        $global:LASTEXITCODE = 0
        & .\mvnw.cmd -pl feedflow-api -Pdynamodb-local verify
        $integrationTestsSucceeded = $?
        $integrationTestsExitCode = $LASTEXITCODE
        if (-not $integrationTestsSucceeded -or $integrationTestsExitCode -ne 0) {
            throw "Os testes de integração com DynamoDB Local falharam. Exit code: $integrationTestsExitCode."
        }
    }

    $functionZip = Join-Path $repositoryRoot 'feedflow-api\target\function.zip'
    if (-not (Test-Path -LiteralPath $functionZip)) {
        throw 'O empacotamento não gerou feedflow-api/target/function.zip.'
    }

    Assert-LambdaPackage -Path $functionZip -BuildStartedAtUtc $buildStartedAtUtc

    $notificationsJar = Join-Path $repositoryRoot `
        'feedflow-notifications\target\feedflow-notifications-1.0.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $notificationsJar)) {
        throw 'O empacotamento não gerou o JAR da Lambda de notificações.'
    }

    Assert-NotificationsPackage `
        -Path $notificationsJar `
        -BuildStartedAtUtc $buildStartedAtUtc

    $notificationsFunctionZip = Join-Path $repositoryRoot `
        'feedflow-notifications\target\function.zip'
    if (-not (Test-Path -LiteralPath $notificationsFunctionZip)) {
        throw 'O empacotamento não gerou o function.zip da Lambda de notificações.'
    }
    $notificationsZipArtifact = Get-Item -LiteralPath $notificationsFunctionZip
    if ($notificationsZipArtifact.Length -le 0) {
        throw 'O function.zip da Lambda de notificações foi gerado vazio.'
    }
    if ($notificationsZipArtifact.LastWriteTimeUtc -lt $buildStartedAtUtc.AddSeconds(-2)) {
        throw 'O function.zip da Lambda de notificações não foi atualizado pelo build Maven atual.'
    }

    $notificationsArchive = [System.IO.Compression.ZipFile]::OpenRead(
        $notificationsZipArtifact.FullName)
    try {
        $expectedJarEntry = 'lib/feedflow-notifications-1.0.0-SNAPSHOT.jar'
        if (-not $notificationsArchive.GetEntry($expectedJarEntry)) {
            throw "JAR sombreado ausente do ZIP de notificações: $expectedJarEntry"
        }
    }
    finally {
        $notificationsArchive.Dispose()
    }

    $reportsJar = Join-Path $repositoryRoot `
        'feedflow-reports\target\feedflow-reports-1.0.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $reportsJar)) {
        throw 'O empacotamento não gerou o JAR da Lambda de relatórios.'
    }

    Assert-ReportsPackage `
        -Path $reportsJar `
        -BuildStartedAtUtc $buildStartedAtUtc

    $reportsFunctionZip = Join-Path $repositoryRoot 'feedflow-reports\target\function.zip'
    if (-not (Test-Path -LiteralPath $reportsFunctionZip)) {
        throw 'O empacotamento não gerou o function.zip da Lambda de relatórios.'
    }
    $reportsZipArtifact = Get-Item -LiteralPath $reportsFunctionZip
    if ($reportsZipArtifact.Length -le 0) {
        throw 'O function.zip da Lambda de relatórios foi gerado vazio.'
    }
    if ($reportsZipArtifact.LastWriteTimeUtc -lt $buildStartedAtUtc.AddSeconds(-2)) {
        throw 'O function.zip da Lambda de relatórios não foi atualizado pelo build Maven atual.'
    }

    $reportsArchive = [System.IO.Compression.ZipFile]::OpenRead($reportsZipArtifact.FullName)
    try {
        $expectedJarEntry = 'lib/feedflow-reports-1.0.0-SNAPSHOT.jar'
        if (-not $reportsArchive.GetEntry($expectedJarEntry)) {
            throw "JAR sombreado ausente do ZIP de relatórios: $expectedJarEntry"
        }
    }
    finally {
        $reportsArchive.Dispose()
    }

    $artifact = Get-Item -LiteralPath $functionZip
    $artifactHash = Get-FileHash -Algorithm SHA256 -LiteralPath $functionZip
    $notificationsArtifact = Get-Item -LiteralPath $notificationsJar
    $notificationsArtifactHash = Get-FileHash -Algorithm SHA256 -LiteralPath $notificationsJar
    $notificationsZipArtifactHash = `
        Get-FileHash -Algorithm SHA256 -LiteralPath $notificationsFunctionZip
    $reportsArtifact = Get-Item -LiteralPath $reportsJar
    $reportsArtifactHash = Get-FileHash -Algorithm SHA256 -LiteralPath $reportsJar
    $reportsZipArtifactHash = Get-FileHash -Algorithm SHA256 -LiteralPath $reportsFunctionZip
    Write-Output "FunctionZip=$($artifact.FullName)"
    Write-Output "FunctionZipBytes=$($artifact.Length)"
    Write-Output "FunctionZipSha256=$($artifactHash.Hash)"
    Write-Output "NotificationsJar=$($notificationsArtifact.FullName)"
    Write-Output "NotificationsJarBytes=$($notificationsArtifact.Length)"
    Write-Output "NotificationsJarSha256=$($notificationsArtifactHash.Hash)"
    Write-Output "NotificationsFunctionZip=$($notificationsZipArtifact.FullName)"
    Write-Output "NotificationsFunctionZipBytes=$($notificationsZipArtifact.Length)"
    Write-Output "NotificationsFunctionZipSha256=$($notificationsZipArtifactHash.Hash)"
    Write-Output "ReportsJar=$($reportsArtifact.FullName)"
    Write-Output "ReportsJarBytes=$($reportsArtifact.Length)"
    Write-Output "ReportsJarSha256=$($reportsArtifactHash.Hash)"
    Write-Output "ReportsFunctionZip=$($reportsZipArtifact.FullName)"
    Write-Output "ReportsFunctionZipBytes=$($reportsZipArtifact.Length)"
    Write-Output "ReportsFunctionZipSha256=$($reportsZipArtifactHash.Hash)"
}
finally {
    Pop-Location
}
