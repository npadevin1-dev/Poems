# Сборка Android-приложения

Приложение написано на Java без AndroidX; экраны создаются программно.
Для сборки нужны Android SDK Platform 34, Build Tools 34.0.0, `apktool`,
`uber-apk-signer` и базовый APK `$HOME/work/poems/base.apk`.

Запустите `./app/build.sh` из корня репозитория. Скрипт компилирует Java,
создаёт DEX, собирает и подписывает APK, затем кладёт его в `versions/` и
обновляет `versions/latest.json`. Ключ и пароль хранятся только в
`$HOME/work/poems/poems-release.jks` и `$HOME/work/poems/keystore-pass.txt`.
