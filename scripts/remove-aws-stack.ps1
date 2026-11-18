[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('DELETE')]
    [string]$Authorization,

    [string]$StackName = 'feedflow-dev',
    [string]$Profile = 'feedflow',
    [string]$Region = 'us-east-2'
)

$ErrorActionPreference = 'Stop'

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

Write-Host "A stack '$StackName' e seus dados de desenvolvimento serão removidos. O SAM pedirá confirmação interativa."
& $samCli delete `
    --stack-name $StackName `
    --profile $Profile `
    --region $Region

if ($LASTEXITCODE -ne 0) {
    throw 'A remoção da stack não foi concluída.'
}
