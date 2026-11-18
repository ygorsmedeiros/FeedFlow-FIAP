[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$endpoint = 'http://dynamodb-local:8000'
$tableName = 'FeedFlowFeedback'

Push-Location $repositoryRoot
try {
    docker compose up -d dynamodb-local
    if ($LASTEXITCODE -ne 0) {
        throw 'Não foi possível iniciar o DynamoDB Local.'
    }

    $tableExists = $false
    $serviceReady = $false

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $describeOutput = docker compose run --rm dynamodb-admin `
            dynamodb describe-table `
            --table-name $tableName `
            --endpoint-url $endpoint `
            --region us-east-2 2>&1

        if ($LASTEXITCODE -eq 0) {
            $serviceReady = $true
            $tableExists = $true
            break
        }

        if ($describeOutput -match 'ResourceNotFoundException') {
            $serviceReady = $true
            break
        }

        Start-Sleep -Seconds 2
    }

    if (-not $serviceReady) {
        throw 'O DynamoDB Local não ficou disponível no tempo esperado.'
    }

    if (-not $tableExists) {
        docker compose run --rm dynamodb-admin `
            dynamodb create-table `
            --cli-input-json file:///work/create-table.json `
            --endpoint-url $endpoint `
            --region us-east-2

        if ($LASTEXITCODE -ne 0) {
            throw 'Não foi possível criar a tabela local.'
        }

        docker compose run --rm dynamodb-admin `
            dynamodb wait table-exists `
            --table-name $tableName `
            --endpoint-url $endpoint `
            --region us-east-2

        if ($LASTEXITCODE -ne 0) {
            throw 'A tabela não ficou ativa no tempo esperado.'
        }
    }

    docker compose run --rm dynamodb-admin `
        dynamodb describe-table `
        --table-name $tableName `
        --endpoint-url $endpoint `
        --region us-east-2 `
        --query 'Table.{Status:TableStatus,Index:GlobalSecondaryIndexes[0].IndexName,Projection:GlobalSecondaryIndexes[0].Projection.ProjectionType,Stream:StreamSpecification.StreamViewType}'

    if ($LASTEXITCODE -ne 0) {
        throw 'Não foi possível validar a tabela local.'
    }
}
finally {
    Pop-Location
}
