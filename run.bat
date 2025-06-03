@echo off

echo Building fat JAR...
call gradlew.bat shadowJar

if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

echo Launching app...
java -jar build\libs\EtoroScraper-1.0-SNAPSHOT-all.jar

pause
