param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('CE','EE')]
    [string]$Edition,

    [switch]$DebugLib
)

$suffix = ""
if($DebugLib) {
    $suffix = "-debug"
}

$OutputDir="$PSScriptRoot/../lite-core/windows/x86_64"
New-Item -Type directory -ErrorAction Ignore $OutputDir
Push-Location $OutputDir 

$ArtifactId = (& "$PSScriptRoot/litecore_sha.ps1" $Edition)
$ArtifactUrl = "http://latestbuilds.service.couchbase.com/builds/latestbuilds/couchbase-lite-core/sha/$($ArtifactId.Substring(0,2))/$ArtifactId/couchbase-lite-core-windows-win64$suffix.zip"
Write-Host "=== Fetching Win64 LiteCore-${EDITION}"
Write-Host "   From $ArtifactUrl"

try {      
  Invoke-WebRequest $ArtifactUrl -OutFile litecore.zip
} catch [System.Net.WebException] {
    Pop-Location
    if($_.Exception.Status -eq [System.Net.WebExceptionStatus]::ProtocolError) {
        $res = $_.Exception.Response.StatusCode
        if($res -eq 404) {
            Write-Host "No LiteCore available for $ArtifactId"
            exit 1
        }
    }
    throw
}

if(Test-Path litecore.zip) {
  & 7z e -y litecore.zip
  rm litecore.zip
}

Pop-Location
