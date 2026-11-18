[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$WeekKey,

    [Parameter(Mandatory = $true)]
    [string]$InicioUtc,

    [Parameter(Mandatory = $true)]
    [string]$FimUtc
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$endpoint = 'http://dynamodb-local:8000'

$attributeNames = @{
    '#weekKey' = 'weekKey'
    '#createdAt' = 'createdAt'
} | ConvertTo-Json -Compress

$attributeValues = @{
    ':weekKey' = @{ S = $WeekKey }
    ':inicio' = @{ S = $InicioUtc }
    ':fim' = @{ S = $FimUtc }
} | ConvertTo-Json -Compress -Depth 4

Push-Location $repositoryRoot
try {
    docker compose run --rm dynamodb-admin `
        dynamodb query `
        --table-name FeedFlowFeedback `
        --index-name byWeek `
        --key-condition-expression '#weekKey = :weekKey AND #createdAt BETWEEN :inicio AND :fim' `
        --expression-attribute-names $attributeNames `
        --expression-attribute-values $attributeValues `
        --endpoint-url $endpoint `
        --region us-east-2

    if ($LASTEXITCODE -ne 0) {
        throw 'A consulta ao índice byWeek falhou.'
    }
}
finally {
    Pop-Location
}
