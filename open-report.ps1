param(
    [ValidateSet('allure', 'testng')]
    [string]$Report = 'allure',
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'

function Get-PythonLauncher {
    foreach ($commandName in @('python', 'py')) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw 'Python is required to serve the Allure report locally.'
}

function Resolve-ExistingPath {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$CandidatePaths,
        [Parameter(Mandatory = $true)]
        [string]$MissingMessage
    )

    foreach ($candidatePath in $CandidatePaths) {
        if (Test-Path $candidatePath) {
            return $candidatePath
        }
    }

    throw $MissingMessage
}

if ($Report -eq 'testng') {
    $testNgReport = Resolve-ExistingPath -CandidatePaths @(
        (Join-Path $PSScriptRoot 'report/testng/index.html'),
        (Join-Path $PSScriptRoot 'target/surefire-reports/emailable-report.html')
    ) -MissingMessage 'TestNG report not found. Run the test suite first.'

    Start-Process $testNgReport | Out-Null
    exit 0
}

$reportRoot = Resolve-ExistingPath -CandidatePaths @(
    (Join-Path $PSScriptRoot 'report/allure'),
    (Join-Path $PSScriptRoot 'target/site/allure-maven-plugin')
) -MissingMessage 'Allure report not found. Run .\mvnw.cmd allure:report first.'

$pythonLauncher = Get-PythonLauncher
$serverUrl = "http://127.0.0.1:$Port"

Write-Output "Starting Allure report server at $serverUrl"
Start-Process -FilePath $pythonLauncher -ArgumentList @('-m', 'http.server', $Port, '--bind', '127.0.0.1') -WorkingDirectory $reportRoot | Out-Null
Start-Process $serverUrl | Out-Null