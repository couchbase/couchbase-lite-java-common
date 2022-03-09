param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('CE','EE')]
    [string]$Edition,

    [string]$OutPath
)

 $Sha = (Select-String -Path $PSScriptRoot\..\..\core_version.txt -Pattern "^${Edition}: ").Line.Substring(4,40)
if ([string]::IsNullOrEmpty($OutPath)) {
   Write-Output $Sha
} else {
    Write-Output $Sha | Out-File -FilePath $OutPath -Force -NoNewline -Encoding ASCII
}

