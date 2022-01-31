
if (Test-Path "$($PSScriptRoot)/../lite-core") {
    Remove-Item -LiteralPath "$($PSScriptRoot)/../lite-core" -Force -Recurse
}
New-Item -Type directory -ErrorAction Ignore "$($PSScriptRoot)/../lite-core"
 
