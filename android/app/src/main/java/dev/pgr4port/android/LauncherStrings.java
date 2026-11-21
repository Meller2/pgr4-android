package dev.pgr4port.android;

import android.content.Context;
import java.util.Locale;

final class LauncherStrings {
    final boolean ru;

    LauncherStrings(Context context, String langPref) {
        if ("ru".equals(langPref)) {
            this.ru = true;
        } else if ("en".equals(langPref)) {
            this.ru = false;
        } else {
            String lang = Locale.getDefault().getLanguage();
            this.ru = "ru".equalsIgnoreCase(lang) || "uk".equalsIgnoreCase(lang) || "be".equalsIgnoreCase(lang);
        }
    }

    String t(String en, String ruText) {
        return ru ? ruText : en;
    }

    String navHome() { return t("Play", "Главная"); }
    String navGame() { return t("Game", "Игра"); }
    String navDrivers() { return t("Drivers", "Драйверы"); }
    String navSettings() { return t("Settings", "Настройки"); }

    String statusReady() { return t("Ready to Race", "Готово к старту"); }
    String statusSetupNeeded() { return t("Game Files Required", "Нужны файлы игры"); }
    String statusCheckingGpu() { return t("Checking GPU...", "Проверка GPU..."); }

    String btnPlay() { return t("PLAY", "ЗАПУСК"); }
    String btnSetupGame() { return t("SELECT GAME", "ВЫБРАТЬ ИГРУ"); }
    String btnChange() { return t("Change", "Изменить"); }
    String btnSelectFolder() { return t("Select Game Folder", "Выбрать папку с игрой"); }
    String btnSelectIso() { return t("Select Xbox 360 .ISO", "Выбрать .ISO образ"); }
    String btnGrantStorage() { return t("Allow Storage Access", "Разрешить доступ к файлам"); }
    String btnInstallDriver() { return t("Install Driver (.zip)", "Установить драйвер (.zip)"); }
    String btnTestDriver() { return t("Test Driver", "Проверить драйвер"); }
    String btnDelete() { return t("Delete", "Удалить"); }
    String btnShareLog() { return t("Share Last Log", "Поделиться логом"); }
    String btnClearCache() { return t("Clear Shader Cache", "Очистить кэш"); }
    String btnResetDefaults() { return t("Reset to Defaults", "Сбросить настройки"); }

    String gameSourceTitle() { return t("Game Source", "Источник игры"); }
    String gameSourceNone() {
        return t("Select an extracted Xbox 360 folder (with default.xex) or a direct .iso image.",
                 "Выберите распакованную папку игры (с default.xex) или напрямую .iso образ Xbox 360.");
    }
    String gameFolderMode() { return t("Extracted Folder", "Распакованная папка"); }
    String gameIsoMode() { return t("Xbox 360 ISO Image", "Образ Xbox 360 (.ISO)"); }
    String gameValidLabel() { return t("Verified", "Проверено"); }
    String gameInvalidLabel() { return t("Not found / unreadable", "Не найден или нет доступа"); }

    String storageBannerTitle() { return t("Storage permission needed", "Нужен доступ к хранилищу"); }
    String storageBannerDesc() {
        return t("Grant 'All files access' so PGR4 can read your game folder or .iso directly without copying 7 GB.",
                 "Разрешите доступ ко всем файлам, чтобы игра читала папку или .iso напрямую без копирования 7 ГБ.");
    }

    String quickStartTitle() { return t("Quick Setup", "Быстрая настройка"); }
    String quickStartStep1() { return t("1. Allow storage access & pick your game folder or .iso", "1. Разрешите доступ к файлам и выберите папку или .iso"); }
    String quickStartStep2() { return t("2. Driver & optimal graphics are configured automatically", "2. Драйвер и быстрая графика настраиваются автоматически"); }
    String quickStartStep3() { return t("3. Tap PLAY and enjoy Project Gotham Racing 4", "3. Нажмите ЗАПУСК и поехали!"); }

    String profileLabel() { return t("Graphics Profile", "Профиль графики"); }
    String profileFast() { return t("Performance (720p)", "Быстрый (720p)"); }
    String profileCompatible() { return t("Compatible (Mali / Safe)", "Совместимый (Mali)"); }
    String profileQuality() { return t("High Quality (MSAA)", "Качество (MSAA)"); }
    String profileCustom() { return t("Custom", "Свой режим"); }

    String systemDriverName() { return t("System Vulkan Driver", "Системный драйвер Vulkan"); }
    String systemDriverDesc() { return t("Built-in device GPU driver", "Встроенный драйвер устройства"); }
    String driverAdrenoHint() {
        return t("Snapdragon Adreno detected. Turnip drivers (.zip) usually give the best FPS and shadow accuracy.",
                 "Обнаружен Snapdragon Adreno. Драйверы Turnip (.zip) обычно дают лучший FPS и правильные тени.");
    }
    String driverMaliHint() {
        return t("Mali / non-Adreno GPU detected. Custom Turnip drivers are not compatible with this GPU — use the System Driver and Compatible preset.",
                 "Обнаружен Mali / другой GPU. Драйверы Turnip подходят только для Snapdragon — используйте системный драйвер и совместимый профиль.");
    }

    String logsTitle() { return t("Saves & Diagnostics", "Сохранения и диагностика"); }
    String noLogsYet() { return t("No log files yet. Launch the game at least once.", "Логов пока нет — запустите игру хотя бы один раз."); }
}
