param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('CE','EE')]
    [string]$Edition,

    [string]$OutPath
)

if($Edition -eq "") {
    $Edition = "CE"
}

Write-Host "LiteCore Edition : $Edition"

if($Edition -eq "EE") {
    Push-Location $PSScriptRoot\..\..\couchbase-lite-core-EE
    $EeSha = (& git rev-parse HEAD).Substring(0, 40)
    Write-Verbose "EE SHA is: '$EeSha'"
    Pop-Location
}

Push-Location $PSScriptRoot\..\..\core
$CeSha = (& git rev-parse HEAD).Substring(0, 40)
Write-Verbose "Base SHA is: '$CeSha'"
Pop-Location

if($Edition -eq "EE") {
    $sha1 = New-Object System.Security.Cryptography.SHA1CryptoServiceProvider
    $amalgamation = $CeSha + $EeSha
    $finalSha = [System.BitConverter]::ToString($sha1.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($amalgamation)))
} else {
    $finalSha = $CeSha
}

Write-Output $finalSha.ToLowerInvariant().Replace("-", "")
if($OutPath) {
    Write-Output $finalSha.ToLowerInvariant().Replace("-", "") | Out-File -FilePath $OutPath -Force -NoNewline -Encoding ASCII
}
