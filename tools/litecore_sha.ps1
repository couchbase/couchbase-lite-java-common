param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('CE','EE')]
    [string]$Edition,

    [string]$OutPath
)

Push-Location $PSScriptRoot\..\..\core
$ArtifactId = (& git rev-parse HEAD).Substring(0, 40)
Pop-Location

if($Edition -eq "EE") {
    Push-Location $PSScriptRoot\..\..\couchbase-lite-core-EE
    $EeSha = (& git rev-parse HEAD).Substring(0, 40)
    Pop-Location
    $Sha1 = New-Object System.Security.Cryptography.SHA1CryptoServiceProvider
    $ArtifactId = [System.BitConverter]::ToString($Sha1.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($ArtifactId + $EeSha)))
    $ArtifactId = $ArtifactId.ToLowerInvariant().Replace("-", "")
}

if ([string]::IsNullOrEmpty($OutPath)) {
   Write-Output $ArtifactId
} else {
    Write-Output $ArtifactId | Out-File -FilePath $OutPath -Force -NoNewline -Encoding ASCII
}

