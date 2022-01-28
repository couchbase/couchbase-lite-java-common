
echo on 

rem Download and extract source for Windows

:: make sure that there are exactly 4 non-empty arguments 

if "%1%" == "" (
	echo Usage: fetch_source_windows.bat ^<LATESTBUILDS^> ^<VERSION^> ^<BUILD_BUMBER^> ^<SOURCE^>
	exit /B 1
)

if "%2%" == "" (
	echo Usage: source_windows.bat ^<LATESTBUILDS^> ^<VERSION^> ^<BUILD_BUMBER^> ^<SOURCE^>
	exit /B 1
)

if "%3%" == "" (
	echo Usage: source_windows.bat ^<LATESTBUILDS^> ^<VERSION^> ^<BUILD_BUMBER^> ^<SOURCE^>
	exit /B 1
)

if "%4%" == "" (
	echo Usage: source_windows.bat ^<LATESTBUILDS^> ^<VERSION^> ^<BUILD_BUMBER^> ^<SOURCE^>
	exit /B 1
)

if NOT "%5%" == "" (
	echo Usage: source_windows.bat ^<LATESTBUILDS^> ^<VERSION^> ^<BUILD_BUMBER^> ^<SOURCE^>
	exit /B 1
)

set latestBuilds=%1%
set version=%2%
set buildNumber=%3%
set source=%4%

echo "======== Windows: Download source"
set site=%latestBuilds%/couchbase-lite-java/%version%/%buildNumber%/%source%
powershell -command "& { (New-Object Net.WebClient).DownloadFile('%site%', '%source%') }" || goto error

echo "======== Windows: Extract source"
cmake -E tar xzf %source% || goto error

goto :eof

:error
echo Failed with error %ERRORLEVEL%.
exit /b %ERRORLEVEL%