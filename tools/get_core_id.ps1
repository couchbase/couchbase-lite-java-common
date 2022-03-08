param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('CE','EE')]
    [string]$Edition,

    [string]$OutPath
)

$Sha = (& Select-String -Path $PSScriptRoot\..\..\core_version.txt -Pattern $Edition).Substring(4, 40)
$Sha = $Sha.ToLowerInvariant().Replace("-", "")
if($OutPath) {
    Write-Output $Sha | Out-File -FilePath $OutPath -Force -NoNewline -Encoding ASCII
} else {
   -Output $Sha
}

