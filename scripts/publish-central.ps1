param(
    [string]$Version = "",
    [ValidateSet("AUTOMATIC", "USER_MANAGED")]
    [string]$PublishingType = "AUTOMATIC",
    [string]$DeploymentName = "",
    [int]$PollSeconds = 10,
    [int]$MaxPolls = 90,
    [switch]$SkipPublishSigned
)

$ErrorActionPreference = "Stop"

function Get-BuildVersion {
    $buildSbt = Join-Path $PSScriptRoot "..\build.sbt"
    $match = Select-String -Path $buildSbt -Pattern '^\s*(?:ThisBuild\s*/\s*)?version\s*:=\s*"([^"]+)"' | Select-Object -First 1
    if (-not $match) {
        throw "Could not read the version from build.sbt."
    }
    return $match.Matches[0].Groups[1].Value
}

function Get-CentralCredentialValue {
    param(
        [string]$Key,
        [string]$EnvName
    )

    $envValue = [Environment]::GetEnvironmentVariable($EnvName)
    if ($envValue) {
        return $envValue
    }

    $credFile = Join-Path ([Environment]::GetFolderPath("UserProfile")) ".sbt\sonatype_central_credentials"
    if (-not (Test-Path $credFile)) {
        throw "Credentials file not found: $credFile"
    }

    $match = Select-String -Path $credFile -Pattern "^$Key=(.*)$" | Select-Object -First 1
    if (-not $match) {
        throw "Entry '$Key' is missing from $credFile"
    }
    return $match.Matches[0].Groups[1].Value
}

function Invoke-Curl {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $result = & curl.exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed with exit code $LASTEXITCODE"
    }
    return ($result | Out-String).Trim()
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$buildVersion = Get-BuildVersion
if (-not $Version) {
    $Version = $buildVersion
}

# The bundle is built from build.sbt. A different -Version would upload artifacts under a
# deployment name that does not match their POMs.
if ($Version -ne $buildVersion -and -not $SkipPublishSigned) {
    throw "Version $Version does not match build.sbt ($buildVersion). Set the version in build.sbt first."
}

# A SNAPSHOT is published straight to the snapshot repository by sbt and never reaches
# target\sona-staging, so this script has nothing to upload for it.
if ($Version.EndsWith("-SNAPSHOT")) {
    throw "Refusing to publish SNAPSHOT version $Version to Maven Central. Set a release version in build.sbt."
}

if (-not $DeploymentName) {
    $DeploymentName = "com.anjunar:scalajs-ember:$Version"
}

$bundleDir = Join-Path $repoRoot "target\sona-staging"
$bundleZip = Join-Path $repoRoot "target\central-bundle-$Version.zip"

if (-not $SkipPublishSigned) {
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot "target"))
    $resolvedBundleDir = [IO.Path]::GetFullPath($bundleDir)
    if (-not $resolvedBundleDir.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to clean staging outside target: $resolvedBundleDir"
    }
    if (Test-Path $resolvedBundleDir) {
        Remove-Item -LiteralPath $resolvedBundleDir -Recurse -Force
    }
    & sbt --server "publishSigned"
    if ($LASTEXITCODE -ne 0) {
        throw "sbt --server publishSigned failed."
    }
}

if (-not (Test-Path $bundleDir)) {
    throw "Bundle directory not found: $bundleDir"
}

if (Test-Path $bundleZip) {
    Remove-Item $bundleZip -Force
}

Compress-Archive -Path (Join-Path $bundleDir "*") -DestinationPath $bundleZip -CompressionLevel Optimal

$user = Get-CentralCredentialValue -Key "user" -EnvName "SONATYPE_CENTRAL_USERNAME"
$password = Get-CentralCredentialValue -Key "password" -EnvName "SONATYPE_CENTRAL_PASSWORD"
$token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${user}:${password}"))
$encodedName = [uri]::EscapeDataString($DeploymentName)
$uploadUrl = "https://central.sonatype.com/api/v1/publisher/upload?name=$encodedName&publishingType=$PublishingType"

Write-Host "Uploading bundle: $bundleZip"
$deploymentId = Invoke-Curl -Arguments @(
    "--silent",
    "--show-error",
    "--fail",
    "--request", "POST",
    "--header", "Authorization: Bearer $token",
    "--form", "bundle=@$bundleZip",
    $uploadUrl
)

if (-not $deploymentId) {
    throw "Sonatype did not return a deployment ID."
}

Write-Host "Deployment ID: $deploymentId"

for ($attempt = 1; $attempt -le $MaxPolls; $attempt++) {
    Start-Sleep -Seconds $PollSeconds

    $statusJson = Invoke-Curl -Arguments @(
        "--silent",
        "--show-error",
        "--fail",
        "--request", "POST",
        "--header", "Authorization: Bearer $token",
        "https://central.sonatype.com/api/v1/publisher/status?id=$deploymentId"
    )

    $status = $statusJson | ConvertFrom-Json
    Write-Host "[$attempt/$MaxPolls] Status: $($status.deploymentState)"

    if ($status.deploymentState -eq "PUBLISHED") {
        Write-Host "Maven Central publishing completed."
        $status | ConvertTo-Json -Depth 10
        exit 0
    }

    if ($status.deploymentState -eq "FAILED" -or ($status.deploymentState -eq "VALIDATED" -and $PublishingType -eq "USER_MANAGED")) {
        Write-Host "Deployment ended with status $($status.deploymentState)."
        $status | ConvertTo-Json -Depth 10
        exit 1
    }
}

throw "Timed out while waiting for the Sonatype status. Deployment ID: $deploymentId"
