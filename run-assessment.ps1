param(
    [ValidateSet('smoke', 'role-based', 'regression')]
    [string]$Suite = 'smoke',
    [switch]$GenerateReport,
    [switch]$OpenReport,
    [int]$ReportPort = 8080
)

$ErrorActionPreference = 'Stop'

$mavenCommand = Join-Path $PSScriptRoot 'mvnw.cmd'

function Invoke-MavenCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $mavenCommand @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Get-PythonLauncher {
    foreach ($commandName in @('python', 'py')) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw 'Python is required to serve the Allure report locally. Install Python or open the static TestNG report instead.'
}

function Start-AllureReportServer {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    # Prefers the freshly generated report, then falls back to the committed snapshot, so the
    # script still works for a reviewer who only wants to look at the shipped evidence.
    $reportRoot = @(
        (Join-Path $PSScriptRoot 'target/site/allure-maven-plugin'),
        (Join-Path $PSScriptRoot 'report/allure')
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    if (-not $reportRoot) {
        throw 'Allure report not found. Run with -GenerateReport first.'
    }

    $pythonLauncher = Get-PythonLauncher
    $serverUrl = "http://127.0.0.1:$Port"

    Write-Output "Starting Allure report server at $serverUrl"
    Start-Process -FilePath $pythonLauncher -ArgumentList @('-m', 'http.server', $Port, '--bind', '127.0.0.1') -WorkingDirectory $reportRoot | Out-Null
    Start-Process $serverUrl | Out-Null
}

function Sync-GeneratedReports {
    $snapshotRoot = Join-Path $PSScriptRoot 'report'
    $allureSource = Join-Path $PSScriptRoot 'target/site/allure-maven-plugin'
    $allureTarget = Join-Path $snapshotRoot 'allure'
    $testNgSource = Join-Path $PSScriptRoot 'target/surefire-reports/emailable-report.html'
    $testNgTargetDir = Join-Path $snapshotRoot 'testng'
    $testNgTarget = Join-Path $testNgTargetDir 'index.html'

    if (Test-Path $allureSource) {
        if (Test-Path $allureTarget) {
            Remove-Item $allureTarget -Recurse -Force
        }

        New-Item -ItemType Directory -Path $snapshotRoot -Force | Out-Null
        Copy-Item $allureSource $allureTarget -Recurse
    }

    if (Test-Path $testNgSource) {
        New-Item -ItemType Directory -Path $testNgTargetDir -Force | Out-Null
        Copy-Item $testNgSource $testNgTarget -Force
    }

    # The assessment deliverables require build artifacts to live in the repository,
    # so failure screenshots and videos are snapshotted alongside the reports.
    #
    # This only overwrites the snapshot when the run actually produced evidence. A passing run
    # leaves artifacts/ empty, and blindly mirroring it would delete the committed failure
    # evidence - quietly removing a graded deliverable as a side effect of the suite going green.
    $artifactsSource = Join-Path $PSScriptRoot 'artifacts'
    $artifactsTarget = Join-Path $snapshotRoot 'artifacts'
    $producedEvidence = (Test-Path $artifactsSource) -and
        ((Get-ChildItem $artifactsSource -Recurse -File -ErrorAction SilentlyContinue).Count -gt 0)

    if ($producedEvidence) {
        if (Test-Path $artifactsTarget) {
            Remove-Item $artifactsTarget -Recurse -Force
        }

        New-Item -ItemType Directory -Path $snapshotRoot -Force | Out-Null
        Copy-Item $artifactsSource $artifactsTarget -Recurse
    }
}

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME must be set before running the assessment.'
}

$suiteFile = "src/test/resources/testng/$Suite.xml"

Push-Location $PSScriptRoot
try {
    Write-Output "Running suite: $Suite"
    Invoke-MavenCommand -Arguments @('clean', 'test', "-Dsuite=$suiteFile")

    Sync-GeneratedReports

    if ($GenerateReport) {
        Write-Output 'Generating Allure report'
        Invoke-MavenCommand -Arguments @('allure:report')
        Sync-GeneratedReports
    }

    if ($OpenReport) {
        Start-AllureReportServer -Port $ReportPort
    }
}
finally {
    Pop-Location
}