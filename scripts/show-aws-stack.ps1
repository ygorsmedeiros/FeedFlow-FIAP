[CmdletBinding()]
param(
    [string]$StackName = 'feedflow-dev',
    [string]$Profile = 'feedflow',
    [string]$Region = 'us-east-2'
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

$awsCli = Resolve-AwsCli

& $awsCli cloudformation describe-stacks `
    --stack-name $StackName `
    --profile $Profile `
    --region $Region `
    --query 'Stacks[0].{Status:StackStatus,Outputs:Outputs}' `
    --output json

if ($LASTEXITCODE -ne 0) {
    throw 'Não foi possível consultar a stack.'
}

& $awsCli cloudformation list-stack-resources `
    --stack-name $StackName `
    --profile $Profile `
    --region $Region `
    --query 'StackResourceSummaries[].{LogicalId:LogicalResourceId,Type:ResourceType,Status:ResourceStatus,PhysicalId:PhysicalResourceId}' `
    --output table

if ($LASTEXITCODE -ne 0) {
    throw 'Não foi possível listar os recursos da stack.'
}
