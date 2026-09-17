@echo off
echo Nettoyage du projet Flutter...
cd C:\MAHDI\MOBILE
call flutter clean
call flutter pub get

echo.
echo Lancement de l'application sur Microsoft Edge...
call flutter run -d edge --web-renderer canvaskit --web-port 3000
