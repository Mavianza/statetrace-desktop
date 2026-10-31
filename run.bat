@echo off
setlocal
pushd "%~dp0"
java -jar "dist\statetrace.jar" %*
if errorlevel 2 pause
popd
endlocal
