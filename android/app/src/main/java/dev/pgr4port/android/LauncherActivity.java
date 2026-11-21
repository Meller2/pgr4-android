package dev.pgr4port.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;

public final class LauncherActivity extends Activity {
    private static final int TAB_HOME = 0;
    private static final int TAB_GAME = 1;
    private static final int TAB_DRIVERS = 2;
    private static final int TAB_SETTINGS = 3;

    private static final int REQ_PICK_FOLDER = 1001;
    private static final int REQ_PICK_ISO = 1002;
    private static final int REQ_PICK_DRIVER_ZIP = 1003;
    private static final int REQ_STORAGE_LEGACY = 1004;

    private Pgr4Config config;
    private DriverManager driverManager;
    private LauncherStrings s;

    private GpuProbe.GpuInfo systemGpuInfo;
    private GpuProbe.GpuInfo activeDriverProbe;
    private boolean probeInFlight = false;
    private String lastProbeBanner = "";
    private boolean lastProbeOk = true;

    private int currentTab = TAB_HOME;

    private FrameLayout rootContainer;
    private LinearLayout contentContainer;
    private LinearLayout navContainer;
    private TextView headerStatusText;
    private View headerStatusDot;
    private TextView headerChipText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        config = new Pgr4Config(this);
        driverManager = new DriverManager(this);
        s = new LauncherStrings(this, config.uiLanguage);

        buildScaffold();
        runInitialGpuProbe();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (config != null) {
            s = new LauncherStrings(this, config.uiLanguage);
            refreshUi();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildScaffold();
        refreshUi();
    }

    private void runInitialGpuProbe() {
        probeInFlight = true;
        new Thread(() -> {
            GpuProbe.GpuInfo sys = GpuProbe.probeNow(getApplicationContext(), "", "");
            runOnUiThread(() -> {
                systemGpuInfo = sys;
                config.applyAutoProfileIfNeeded(sys, driverManager);
                if (config.driverDir.isEmpty() || config.driverName.isEmpty()) {
                    activeDriverProbe = sys;
                    probeInFlight = false;
                    refreshUi();
                } else {
                    probeActiveDriverIsolated(false);
                }
            });
        }, "pgr4-sys-gpu-probe").start();
    }

    private void probeActiveDriverIsolated(boolean showToast) {
        probeInFlight = true;
        refreshUi();
        ProbeService.probeIsolated(this, config.driverDir, config.driverName, info -> {
            probeInFlight = false;
            activeDriverProbe = info;
            lastProbeOk = info.ok;
            if (info.ok) {
                lastProbeBanner = s.t("Driver verified: ", "Драйвер проверен: ")
                        + info.deviceName + " (Vulkan " + info.apiVersion + ")";
            } else {
                lastProbeBanner = s.t("Driver check failed: ", "Ошибка проверки драйвера: ") + info.error;
            }
            if (showToast) {
                Toast.makeText(this, lastProbeBanner, Toast.LENGTH_LONG).show();
            }
            refreshUi();
        });
    }

    private void buildScaffold() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(LauncherTheme.BG_DARK);

        // Background artwork
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg_pgr4);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootContainer.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Cinematic dark gradient scrim (left & bottom heavier, leaving car visible)
        View scrim = new View(this);
        GradientDrawable scrimGd = new GradientDrawable(
                landscape ? GradientDrawable.Orientation.LEFT_RIGHT : GradientDrawable.Orientation.TOP_BOTTOM,
                landscape
                        ? new int[]{0xEA080A0F, 0xCC0A0D14, 0x7A0A0D14, 0xB8080A0F}
                        : new int[]{0xD8080A0F, 0x990A0D14, 0xE6080A0F});
        scrim.setBackground(scrimGd);
        rootContainer.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Subtle red racing accent glow along top/bottom edge
        View topAccent = new View(this);
        topAccent.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{LauncherTheme.RED_PRIMARY, 0x00E11D2A}));
        rootContainer.addView(topAccent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, LauncherTheme.dp(this, 3)));

        LinearLayout mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setFitsSystemWindows(true);

        // Top Telemetry Header
        mainColumn.addView(buildHeader(landscape));

        if (landscape) {
            LinearLayout bodyRow = new LinearLayout(this);
            bodyRow.setOrientation(LinearLayout.HORIZONTAL);
            bodyRow.setPadding(LauncherTheme.dp(this, 16), 0, LauncherTheme.dp(this, 16), LauncherTheme.dp(this, 12));

            navContainer = new LinearLayout(this);
            navContainer.setOrientation(LinearLayout.VERTICAL);
            navContainer.setBackground(LauncherTheme.cardBg(this, false));
            int np = LauncherTheme.dp(this, 8);
            navContainer.setPadding(np, np, np, np);
            LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                    LauncherTheme.dp(this, 176), ViewGroup.LayoutParams.WRAP_CONTENT);
            navLp.rightMargin = LauncherTheme.dp(this, 14);
            bodyRow.addView(navContainer, navLp);

            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            contentContainer = new LinearLayout(this);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(contentContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            bodyRow.addView(scroll, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            mainColumn.addView(bodyRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            contentContainer = new LinearLayout(this);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            int pad = LauncherTheme.dp(this, 16);
            contentContainer.setPadding(pad, LauncherTheme.dp(this, 4), pad, pad);
            scroll.addView(contentContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mainColumn.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            navContainer = new LinearLayout(this);
            navContainer.setOrientation(LinearLayout.HORIZONTAL);
            navContainer.setBackgroundColor(0xF00B0E14);
            int np = LauncherTheme.dp(this, 8);
            navContainer.setPadding(np, np, np, np);
            mainColumn.addView(navContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        rootContainer.addView(mainColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootContainer);
    }

    private View buildHeader(boolean landscape) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(LauncherTheme.dp(this, 16), LauncherTheme.dp(this, 12),
                LauncherTheme.dp(this, 16), LauncherTheme.dp(this, 10));

        // Brand block
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pgr = new TextView(this);
        pgr.setText("PGR");
        pgr.setTextColor(Color.WHITE);
        pgr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        pgr.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(pgr);

        TextView four = new TextView(this);
        four.setText("4");
        four.setTextColor(LauncherTheme.RED_PRIMARY);
        four.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        four.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(four);

        TextView tag = new TextView(this);
        tag.setText("ANDROID");
        tag.setTextColor(LauncherTheme.TEXT_SECONDARY);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setLetterSpacing(0.14f);
        tag.setPadding(LauncherTheme.dp(this, 8), LauncherTheme.dp(this, 3), 0, 0);
        titleRow.addView(tag);
        brand.addView(titleRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        headerStatusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                LauncherTheme.dp(this, 8), LauncherTheme.dp(this, 8));
        dotLp.rightMargin = LauncherTheme.dp(this, 6);
        statusRow.addView(headerStatusDot, dotLp);

        headerStatusText = new TextView(this);
        headerStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        headerStatusText.setTextColor(LauncherTheme.TEXT_SECONDARY);
        statusRow.addView(headerStatusText);
        brand.addView(statusRow);

        header.addView(brand, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Hardware & Driver pill
        headerChipText = new TextView(this);
        headerChipText.setTextColor(LauncherTheme.TEXT_PRIMARY);
        headerChipText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        headerChipText.setMaxLines(1);
        headerChipText.setEllipsize(TextUtils.TruncateAt.END);
        headerChipText.setBackground(LauncherTheme.secondaryButtonBg(this));
        headerChipText.setPadding(LauncherTheme.dp(this, 12), LauncherTheme.dp(this, 8),
                LauncherTheme.dp(this, 12), LauncherTheme.dp(this, 8));
        headerChipText.setOnClickListener(v -> {
            currentTab = TAB_DRIVERS;
            refreshUi();
        });
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!landscape) {
            headerChipText.setMaxWidth(LauncherTheme.dp(this, 190));
        }
        header.addView(headerChipText, chipLp);

        return header;
    }

    private void refreshUi() {
        if (contentContainer == null || navContainer == null) return;
        s = new LauncherStrings(this, config.uiLanguage);

        GameSourceHelper.ValidationResult gameVal =
                GameSourceHelper.validateGameSource(this, config.gamePath, s);
        DriverManager.DriverEntry currentDriver =
                driverManager.findByIdOrPath(config.driverId, config.driverDir, s);

        // Update header status
        if (gameVal.valid) {
            headerStatusDot.setBackground(LauncherTheme.dotDrawable(this, LauncherTheme.GREEN_READY));
            headerStatusText.setText(s.statusReady());
            headerStatusText.setTextColor(0xFF86EFAC);
        } else {
            headerStatusDot.setBackground(LauncherTheme.dotDrawable(this, LauncherTheme.GOLD_WARN));
            headerStatusText.setText(s.statusSetupNeeded());
            headerStatusText.setTextColor(0xFFFDE68A);
        }

        String gpuLabel = probeInFlight
                ? s.statusCheckingGpu()
                : (activeDriverProbe != null && activeDriverProbe.ok
                    ? activeDriverProbe.deviceName
                    : (systemGpuInfo != null && systemGpuInfo.ok ? systemGpuInfo.deviceName : GpuProbe.deviceModelLabel()));
        String drvShort = currentDriver.isSystem ? s.t("System", "Системный") : currentDriver.name;
        headerChipText.setText(gpuLabel + " · " + drvShort);

        rebuildNav();

        contentContainer.removeAllViews();
        switch (currentTab) {
            case TAB_HOME:
                buildHomeTab(gameVal, currentDriver);
                break;
            case TAB_GAME:
                buildGameTab(gameVal);
                break;
            case TAB_DRIVERS:
                buildDriversTab(currentDriver);
                break;
            case TAB_SETTINGS:
                buildSettingsTab();
                break;
        }
    }

    private void rebuildNav() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        navContainer.removeAllViews();
        String[] labels = {s.navHome(), s.navGame(), s.navDrivers(), s.navSettings()};
        String[] icons = {"▶", "▣", "⚙", "☰"};
        for (int i = 0; i < labels.length; i++) {
            final int tabIdx = i;
            boolean active = (currentTab == i);
            TextView item = new TextView(this);
            item.setText((landscape ? icons[i] + "  " : "") + labels[i]);
            item.setTextColor(active ? Color.WHITE : LauncherTheme.TEXT_SECONDARY);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 14 : 12);
            item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setGravity(landscape ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
            item.setBackground(LauncherTheme.navItemBg(this, active));
            item.setPadding(LauncherTheme.dp(this, 12), LauncherTheme.dp(this, 11),
                    LauncherTheme.dp(this, 12), LauncherTheme.dp(this, 11));
            item.setOnClickListener(v -> {
                currentTab = tabIdx;
                refreshUi();
            });
            LinearLayout.LayoutParams lp = landscape
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (landscape && i < labels.length - 1) {
                lp.bottomMargin = LauncherTheme.dp(this, 6);
            } else if (!landscape && i < labels.length - 1) {
                lp.rightMargin = LauncherTheme.dp(this, 6);
            }
            navContainer.addView(item, lp);
        }
    }

    private void buildHomeTab(GameSourceHelper.ValidationResult gameVal,
                              DriverManager.DriverEntry currentDriver) {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (!GameSourceHelper.hasStoragePermission(this) && !gameVal.valid) {
            contentContainer.addView(buildStorageBanner(), cardMarginParams());
        }

        if (!gameVal.valid) {
            LinearLayout guide = LauncherTheme.makeCard(this, true);
            guide.addView(LauncherTheme.makeCaption(this, s.quickStartTitle()));
            addSpace(guide, 6);
            guide.addView(LauncherTheme.makeTitle(this, s.gameSourceTitle()));
            addSpace(guide, 6);
            guide.addView(LauncherTheme.makeSubtitle(this,
                    s.quickStartStep1() + "\n" + s.quickStartStep2() + "\n" + s.quickStartStep3()));
            addSpace(guide, 14);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView pickFolder = LauncherTheme.makePrimaryButton(this, s.btnSelectFolder(), v -> openFolderPicker());
            TextView pickIso = LauncherTheme.makeSecondaryButton(this, s.btnSelectIso(), v -> openIsoPicker());
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p1.rightMargin = LauncherTheme.dp(this, 8);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            btnRow.addView(pickFolder, p1);
            btnRow.addView(pickIso, p2);
            guide.addView(btnRow);
            contentContainer.addView(guide, cardMarginParams());
            return;
        }

        // Ready state: clean 2-card telemetry + prominent PLAY action
        LinearLayout topCards = new LinearLayout(this);
        topCards.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // Card 1: Game Source
        LinearLayout gameCard = LauncherTheme.makeCard(this, false);
        gameCard.addView(LauncherTheme.makeCaption(this, s.gameSourceTitle()));
        addSpace(gameCard, 4);
        gameCard.addView(LauncherTheme.makeTitle(this, new File(gameVal.resolvedPath).getName()));
        addSpace(gameCard, 4);
        gameCard.addView(LauncherTheme.makeSubtitle(this, gameVal.detail));
        addSpace(gameCard, 10);
        TextView changeGame = LauncherTheme.makeSecondaryButton(this, s.btnChange(), v -> {
            currentTab = TAB_GAME;
            refreshUi();
        });
        gameCard.addView(changeGame);

        // Card 2: Performance & Driver
        LinearLayout perfCard = LauncherTheme.makeCard(this, false);
        perfCard.addView(LauncherTheme.makeCaption(this, s.profileLabel()));
        addSpace(perfCard, 4);
        perfCard.addView(LauncherTheme.makeTitle(this, presetDisplayName(config.preset)));
        addSpace(perfCard, 4);
        String gpuLine = (systemGpuInfo != null && systemGpuInfo.ok ? systemGpuInfo.deviceName : GpuProbe.deviceModelLabel())
                + "\n" + currentDriver.name + " · " + config.renderScale + "%";
        perfCard.addView(LauncherTheme.makeSubtitle(this, gpuLine));
        addSpace(perfCard, 10);
        LinearLayout presetPills = new LinearLayout(this);
        presetPills.setOrientation(LinearLayout.HORIZONTAL);
        addPresetPill(presetPills, s.t("Fast", "Быстрый"), Pgr4Config.PRESET_FAST, true);
        addPresetPill(presetPills, s.t("Safe", "Совм."), Pgr4Config.PRESET_COMPATIBLE, true);
        addPresetPill(presetPills, s.t("Quality", "Кач."), Pgr4Config.PRESET_QUALITY, false);
        perfCard.addView(presetPills);

        if (landscape) {
            LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            c1.rightMargin = LauncherTheme.dp(this, 12);
            LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            topCards.addView(gameCard, c1);
            topCards.addView(perfCard, c2);
        } else {
            LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            c1.bottomMargin = LauncherTheme.dp(this, 12);
            topCards.addView(gameCard, c1);
            topCards.addView(perfCard);
        }
        contentContainer.addView(topCards, cardMarginParams());

        // Adreno warning if still on system driver
        if (systemGpuInfo != null && systemGpuInfo.isAdreno() && currentDriver.isSystem) {
            LinearLayout adrenoWarn = LauncherTheme.makeCard(this, true);
            adrenoWarn.addView(LauncherTheme.makeTitle(this, s.driverAdrenoMissingWarning()));
            addSpace(adrenoWarn, 4);
            adrenoWarn.addView(LauncherTheme.makeSubtitle(this, s.driverAdrenoMissingBody()));
            addSpace(adrenoWarn, 10);
            adrenoWarn.addView(LauncherTheme.makeSecondaryButton(this, s.btnOpenDrivers(), v -> {
                currentTab = TAB_DRIVERS;
                refreshUi();
            }));
            contentContainer.addView(adrenoWarn, cardMarginParams());
        }

        // Driver warning if custom probe failed
        if (!lastProbeOk && !lastProbeBanner.isEmpty()) {
            LinearLayout warn = LauncherTheme.makeCard(this, true);
            warn.addView(LauncherTheme.makeTitle(this, s.t("Driver Warning", "Предупреждение о драйвере")));
            addSpace(warn, 4);
            warn.addView(LauncherTheme.makeSubtitle(this, lastProbeBanner));
            contentContainer.addView(warn, cardMarginParams());
        }

        // Launch Bar
        LinearLayout launchCard = LauncherTheme.makeCard(this, true);
        LinearLayout launchRow = new LinearLayout(this);
        launchRow.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        launchRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.addView(LauncherTheme.makeTitle(this, "Project Gotham Racing 4"));
        addSpace(infoCol, 3);
        infoCol.addView(LauncherTheme.makeSubtitle(this,
                (config.touchEnabled ? s.t("Touch HUD On", "Сенсор вкл") : s.t("Controller Mode", "Геймпад"))
                        + "  ·  "
                        + (config.hudEnabled ? s.t("FPS Overlay On", "FPS счётчик вкл") : s.t("FPS Overlay Off", "FPS счётчик выкл"))));

        TextView playBtn = LauncherTheme.makePrimaryButton(this, "▶   " + s.btnPlay(), v -> launchGame(gameVal));
        playBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        playBtn.setPadding(LauncherTheme.dp(this, 30), LauncherTheme.dp(this, 16),
                LauncherTheme.dp(this, 30), LauncherTheme.dp(this, 16));

        if (landscape) {
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ilp.rightMargin = LauncherTheme.dp(this, 16);
            launchRow.addView(infoCol, ilp);
            launchRow.addView(playBtn);
        } else {
            launchRow.addView(infoCol);
            addSpace(launchRow, 14);
            launchRow.addView(playBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        launchCard.addView(launchRow);
        contentContainer.addView(launchCard, cardMarginParams());
    }

    private void buildGameTab(GameSourceHelper.ValidationResult gameVal) {
        if (!GameSourceHelper.hasStoragePermission(this)) {
            contentContainer.addView(buildStorageBanner(), cardMarginParams());
        }

        LinearLayout sourceCard = LauncherTheme.makeCard(this, false);
        sourceCard.addView(LauncherTheme.makeCaption(this, s.gameSourceTitle()));
        addSpace(sourceCard, 6);
        sourceCard.addView(LauncherTheme.makeTitle(this,
                gameVal.valid ? gameVal.detail : s.statusSetupNeeded()));
        addSpace(sourceCard, 4);
        sourceCard.addView(LauncherTheme.makeSubtitle(this,
                gameVal.resolvedPath + "\n\n" + s.gameSourceNone()));
        addSpace(sourceCard, 14);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView folderBtn = LauncherTheme.makePrimaryButton(this, s.btnSelectFolder(), v -> openFolderPicker());
        TextView isoBtn = LauncherTheme.makeSecondaryButton(this, s.btnSelectIso(), v -> openIsoPicker());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = LauncherTheme.dp(this, 8);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(folderBtn, lp1);
        row.addView(isoBtn, lp2);
        sourceCard.addView(row);
        contentContainer.addView(sourceCard, cardMarginParams());

        // Saves, cache & logs card
        LinearLayout diagCard = LauncherTheme.makeCard(this, false);
        diagCard.addView(LauncherTheme.makeCaption(this, s.logsTitle()));
        addSpace(diagCard, 6);
        File latestLog = GameSourceHelper.findLatestLogFile(this);
        long cacheBytes = GameSourceHelper.dirSize(new File(getFilesDir(), "cache"));
        String diagText = (latestLog != null
                ? s.t("Latest log: ", "Последний лог: ") + latestLog.getName()
                        + " (" + GameSourceHelper.formatSize(latestLog.length()) + ")"
                : s.noLogsYet())
                + "\n" + s.t("Shader & runtime cache: ", "Кэш шейдеров: ")
                + GameSourceHelper.formatSize(cacheBytes);
        diagCard.addView(LauncherTheme.makeSubtitle(this, diagText));
        addSpace(diagCard, 12);

        LinearLayout diagBtns = new LinearLayout(this);
        diagBtns.setOrientation(LinearLayout.HORIZONTAL);
        TextView shareLog = LauncherTheme.makeSecondaryButton(this, s.btnShareLog(), v -> shareLastLog());
        TextView clearCache = LauncherTheme.makeSecondaryButton(this, s.btnClearCache(), v -> {
            long freed = GameSourceHelper.clearShaderCache(this);
            Toast.makeText(this,
                    s.t("Cleared: ", "Очищено: ") + GameSourceHelper.formatSize(freed),
                    Toast.LENGTH_SHORT).show();
            refreshUi();
        });
        LinearLayout.LayoutParams dlp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dlp1.rightMargin = LauncherTheme.dp(this, 8);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        diagBtns.addView(shareLog, dlp1);
        diagBtns.addView(clearCache, dlp2);
        diagCard.addView(diagBtns);
        contentContainer.addView(diagCard, cardMarginParams());
    }

    private void buildDriversTab(DriverManager.DriverEntry currentDriver) {
        // Hardware Info Card
        LinearLayout gpuCard = LauncherTheme.makeCard(this, false);
        gpuCard.addView(LauncherTheme.makeCaption(this, s.t("Device Graphics", "Графика устройства")));
        addSpace(gpuCard, 6);
        if (systemGpuInfo != null && systemGpuInfo.ok) {
            gpuCard.addView(LauncherTheme.makeTitle(this,
                    GpuProbe.deviceModelLabel() + " · " + systemGpuInfo.deviceName));
            addSpace(gpuCard, 4);
            String details = "Vulkan " + systemGpuInfo.apiVersion
                    + "  ·  Vendor " + systemGpuInfo.vendorId
                    + "  ·  BDA: " + (systemGpuInfo.bdaSupported ? "Yes" : "No")
                    + "\n\n" + (systemGpuInfo.isAdreno() ? s.driverAdrenoHint() : s.driverMaliHint());
            gpuCard.addView(LauncherTheme.makeSubtitle(this, details));
        } else {
            gpuCard.addView(LauncherTheme.makeTitle(this, GpuProbe.deviceModelLabel()));
            addSpace(gpuCard, 4);
            gpuCard.addView(LauncherTheme.makeSubtitle(this, s.statusCheckingGpu()));
        }
        addSpace(gpuCard, 12);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView installBtn = LauncherTheme.makePrimaryButton(this, s.btnInstallDriver(), v -> openDriverZipPicker());
        TextView testBtn = LauncherTheme.makeSecondaryButton(this, s.btnTestDriver(), v -> probeActiveDriverIsolated(true));
        LinearLayout.LayoutParams alp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alp1.rightMargin = LauncherTheme.dp(this, 8);
        LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actionRow.addView(installBtn, alp1);
        actionRow.addView(testBtn, alp2);
        gpuCard.addView(actionRow);

        if (systemGpuInfo == null || systemGpuInfo.isAdreno()) {
            addSpace(gpuCard, 8);
            gpuCard.addView(LauncherTheme.makeSecondaryButton(this, s.btnDownloadTurnip(), v -> {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/K11MCH1/AdrenoToolsDrivers/releases"));
                startActivity(browser);
            }));
        }

        if (!lastProbeBanner.isEmpty()) {
            addSpace(gpuCard, 10);
            TextView probeNote = LauncherTheme.makeSubtitle(this, lastProbeBanner);
            probeNote.setTextColor(lastProbeOk ? 0xFF86EFAC : 0xFFFCA5A5);
            gpuCard.addView(probeNote);
        }
        contentContainer.addView(gpuCard, cardMarginParams());

        // Driver list
        List<DriverManager.DriverEntry> drivers = driverManager.listDrivers(s);
        for (DriverManager.DriverEntry entry : drivers) {
            boolean selected = (entry.isSystem && currentDriver.isSystem)
                    || (!entry.isSystem && entry.id.equals(currentDriver.id));
            LinearLayout card = LauncherTheme.makeCard(this, selected);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout metaCol = new LinearLayout(this);
            metaCol.setOrientation(LinearLayout.VERTICAL);
            metaCol.addView(LauncherTheme.makeTitle(this, entry.name + (selected ? "  ✓" : "")));
            String sub = entry.version.isEmpty() ? entry.libraryName : entry.version;
            if (!entry.description.isEmpty()) {
                sub = sub.isEmpty() ? entry.description : sub + " — " + entry.description;
            }
            if (!sub.isEmpty()) {
                addSpace(metaCol, 2);
                metaCol.addView(LauncherTheme.makeSubtitle(this, sub));
            }
            topRow.addView(metaCol, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (!entry.isSystem) {
                TextView del = LauncherTheme.makeSecondaryButton(this, s.btnDelete(), v -> confirmDeleteDriver(entry));
                topRow.addView(del);
            }
            card.addView(topRow);
            card.setOnClickListener(v -> {
                config.setDriver(entry);
                config.saveAll();
                lastProbeBanner = "";
                lastProbeOk = true;
                if (!entry.isSystem) {
                    probeActiveDriverIsolated(false);
                } else {
                    activeDriverProbe = systemGpuInfo;
                    refreshUi();
                }
            });
            contentContainer.addView(card, cardMarginParams());
        }
    }

    private void buildSettingsTab() {
        // Presets Card
        LinearLayout presetCard = LauncherTheme.makeCard(this, false);
        presetCard.addView(LauncherTheme.makeCaption(this, s.profileLabel()));
        addSpace(presetCard, 8);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        addPresetPill(presetRow, s.profileFast(), Pgr4Config.PRESET_FAST, true);
        addPresetPill(presetRow, s.profileCompatible(), Pgr4Config.PRESET_COMPATIBLE, true);
        addPresetPill(presetRow, s.profileQuality(), Pgr4Config.PRESET_QUALITY, false);
        presetCard.addView(presetRow);

        addSpace(presetCard, 12);
        presetCard.addView(LauncherTheme.makeCaption(this, s.t("Render Resolution Scale", "Масштаб разрешения")));
        addSpace(presetCard, 8);
        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] scales = {100, 85, 75, 50};
        for (int i = 0; i < scales.length; i++) {
            final int sc = scales[i];
            String label = sc == 100 ? "100% (720p)" : sc + "%";
            TextView pill = LauncherTheme.makePillButton(this, label, config.renderScale == sc, v -> {
                config.renderScale = sc;
                config.preset = Pgr4Config.PRESET_CUSTOM;
                config.saveAll();
                refreshUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < scales.length - 1) lp.rightMargin = LauncherTheme.dp(this, 6);
            scaleRow.addView(pill, lp);
        }
        presetCard.addView(scaleRow);
        contentContainer.addView(presetCard, cardMarginParams());

        // Graphics Toggles
        LinearLayout gfxCard = LauncherTheme.makeCard(this, false);
        gfxCard.addView(LauncherTheme.makeCaption(this, s.t("Graphics & Performance", "Графика и производительность")));
        addSpace(gfxCard, 8);

        addToggle(gfxCard,
                s.t("Single-Tile Fast Render", "Ускоренный рендер (1 тайл)"),
                s.t("Recommended for mobile GPUs. Major FPS boost.", "Рекомендуется для мобильных GPU, сильно повышает FPS."),
                config.singleTile, val -> {
                    config.singleTile = val;
                    config.preset = Pgr4Config.PRESET_CUSTOM;
                    config.saveAll();
                });

        addToggle(gfxCard,
                s.t("Disable 2x MSAA", "Отключить сглаживание MSAA"),
                s.t("Saves GPU bandwidth and avoids thermal throttling.", "Снижает нагрузку на GPU и нагрев телефона."),
                config.sinMsaa, val -> {
                    config.sinMsaa = val;
                    config.preset = Pgr4Config.PRESET_CUSTOM;
                    config.saveAll();
                });

        addToggle(gfxCard,
                s.t("Fast Resolve Path", "Быстрый Resolve"),
                s.t("Speeds up render target copies (turn off if artifacts appear on Mali).", "Ускоряет копирование кадров (отключите при артефактах на Mali)."),
                config.atalhoResolve, val -> {
                    config.atalhoResolve = val;
                    config.preset = Pgr4Config.PRESET_CUSTOM;
                    config.saveAll();
                });

        addToggle(gfxCard,
                s.t("BDA Shared Memory", "Быстрая память GPU (BDA)"),
                s.t("Faster vertex fetch on Vulkan 1.2+ drivers (disable if Mali crashes).", "Ускоряет геометрию на современных драйверах Vulkan."),
                config.bdaSharedMemory, val -> {
                    config.bdaSharedMemory = val;
                    config.preset = Pgr4Config.PRESET_CUSTOM;
                    config.saveAll();
                });

        addToggle(gfxCard,
                s.t("Vertical Sync (VSync)", "Вертикальная синхронизация (VSync)"),
                s.t("Caps frame rate to screen refresh; turn off for maximum FPS.", "Убирает разрывы, но может снижать FPS."),
                config.vsync, val -> {
                    config.vsync = val;
                    config.preset = Pgr4Config.PRESET_CUSTOM;
                    config.saveAll();
                });

        contentContainer.addView(gfxCard, cardMarginParams());

        // Controls & Launcher UI
        LinearLayout ctrlCard = LauncherTheme.makeCard(this, false);
        ctrlCard.addView(LauncherTheme.makeCaption(this, s.t("Controls & Interface", "Управление и интерфейс")));
        addSpace(ctrlCard, 8);

        addToggle(ctrlCard,
                s.t("In-Game FPS & Heat Monitor", "Счётчик FPS и температуры"),
                s.t("Shows live FPS, frame time and thermal status at the top.", "Показывает FPS и нагрев во время гонки."),
                config.hudEnabled, val -> {
                    config.hudEnabled = val;
                    config.savePrefsOnly();
                });

        addToggle(ctrlCard,
                s.t("On-Screen Touch Controls", "Сенсорные кнопки на экране"),
                s.t("Turn off if you are playing with a Bluetooth/USB gamepad.", "Выключите, если играете с геймпадом."),
                config.touchEnabled, val -> {
                    config.touchEnabled = val;
                    config.savePrefsOnly();
                });

        addSpace(ctrlCard, 8);
        ctrlCard.addView(LauncherTheme.makeCaption(this, s.t("Steering Mode", "Режим руления")));
        addSpace(ctrlCard, 6);
        LinearLayout steerRow = new LinearLayout(this);
        steerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] steerModes = {
            {Pgr4Config.STEER_STICK, s.t("Analog Stick", "Стик")},
            {Pgr4Config.STEER_GYRO, s.t("Tilt / Gyro", "Гироскоп")},
            {Pgr4Config.STEER_BUTTONS, s.t("D-Pad", "Кнопки")}
        };
        for (int i = 0; i < steerModes.length; i++) {
            final String mode = steerModes[i][0];
            TextView pill = LauncherTheme.makePillButton(this, steerModes[i][1], mode.equals(config.steeringMode), v -> {
                config.steeringMode = mode;
                config.savePrefsOnly();
                refreshUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < steerModes.length - 1) lp.rightMargin = LauncherTheme.dp(this, 6);
            steerRow.addView(pill, lp);
        }
        ctrlCard.addView(steerRow);

        if (Pgr4Config.STEER_GYRO.equals(config.steeringMode)) {
            addSpace(ctrlCard, 10);
            ctrlCard.addView(LauncherTheme.makeCaption(this, s.t("Tilt Sensitivity", "Чувствительность наклона")));
            addSpace(ctrlCard, 6);
            LinearLayout sensRow = new LinearLayout(this);
            sensRow.setOrientation(LinearLayout.HORIZONTAL);
            int[] sensVals = {100, 135, 180};
            for (int i = 0; i < sensVals.length; i++) {
                final int sv = sensVals[i];
                TextView pill = LauncherTheme.makePillButton(this, sv + "%", config.gyroSensitivity == sv, v -> {
                    config.gyroSensitivity = sv;
                    config.savePrefsOnly();
                    refreshUi();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < sensVals.length - 1) lp.rightMargin = LauncherTheme.dp(this, 6);
                sensRow.addView(pill, lp);
            }
            ctrlCard.addView(sensRow);
        }

        addSpace(ctrlCard, 8);
        ctrlCard.addView(LauncherTheme.makeCaption(this, s.t("Touch Opacity", "Прозрачность кнопок")));
        addSpace(ctrlCard, 6);
        LinearLayout opRow = new LinearLayout(this);
        opRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] opacities = {35, 65, 95};
        for (int i = 0; i < opacities.length; i++) {
            final int op = opacities[i];
            TextView pill = LauncherTheme.makePillButton(this, op + "%", config.touchOpacity == op, v -> {
                config.touchOpacity = op;
                config.savePrefsOnly();
                refreshUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < opacities.length - 1) lp.rightMargin = LauncherTheme.dp(this, 6);
            opRow.addView(pill, lp);
        }
        ctrlCard.addView(opRow);

        addSpace(ctrlCard, 12);
        ctrlCard.addView(LauncherTheme.makeCaption(this, s.t("Launcher Language", "Язык лаунчера")));
        addSpace(ctrlCard, 6);
        LinearLayout langRow = new LinearLayout(this);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] langs = {{"auto", "Auto"}, {"en", "English"}, {"ru", "Русский"}};
        for (int i = 0; i < langs.length; i++) {
            final String code = langs[i][0];
            TextView pill = LauncherTheme.makePillButton(this, langs[i][1], code.equals(config.uiLanguage), v -> {
                config.uiLanguage = code;
                config.savePrefsOnly();
                refreshUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < langs.length - 1) lp.rightMargin = LauncherTheme.dp(this, 6);
            langRow.addView(pill, lp);
        }
        ctrlCard.addView(langRow);

        addSpace(ctrlCard, 14);
        ctrlCard.addView(LauncherTheme.makeSecondaryButton(this, s.btnResetDefaults(), v -> {
            config.autoConfigured = false;
            config.applyAutoProfileIfNeeded(systemGpuInfo, driverManager);
            refreshUi();
            Toast.makeText(this, s.t("Defaults restored", "Настройки сброшены"), Toast.LENGTH_SHORT).show();
        }));
        contentContainer.addView(ctrlCard, cardMarginParams());
    }

    private LinearLayout buildStorageBanner() {
        LinearLayout banner = LauncherTheme.makeCard(this, true);
        banner.addView(LauncherTheme.makeTitle(this, s.storageBannerTitle()));
        addSpace(banner, 4);
        banner.addView(LauncherTheme.makeSubtitle(this, s.storageBannerDesc()));
        addSpace(banner, 10);
        banner.addView(LauncherTheme.makePrimaryButton(this, s.btnGrantStorage(), v -> requestStoragePermission()));
        return banner;
    }

    private void addPresetPill(LinearLayout row, String label, String presetKey, boolean marginRight) {
        boolean selected = presetKey.equals(config.preset);
        TextView pill = LauncherTheme.makePillButton(this, label, selected, v -> {
            GpuProbe.GpuInfo targetGpu = (activeDriverProbe != null && activeDriverProbe.ok)
                    ? activeDriverProbe : systemGpuInfo;
            config.applyPreset(presetKey, targetGpu);
            config.saveAll();
            refreshUi();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (marginRight) lp.rightMargin = LauncherTheme.dp(this, 6);
        row.addView(pill, lp);
    }

    private void addToggle(LinearLayout parent, String title, String desc,
                           boolean checked, LauncherTheme.ToggleListener listener) {
        LinearLayout row = LauncherTheme.makeToggleRow(this, title, desc, checked, listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = LauncherTheme.dp(this, 8);
        parent.addView(row, lp);
    }

    private String presetDisplayName(String preset) {
        if (Pgr4Config.PRESET_FAST.equals(preset)) return s.profileFast();
        if (Pgr4Config.PRESET_COMPATIBLE.equals(preset)) return s.profileCompatible();
        if (Pgr4Config.PRESET_QUALITY.equals(preset)) return s.profileQuality();
        return s.profileCustom();
    }

    private LinearLayout.LayoutParams cardMarginParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = LauncherTheme.dp(this, 12);
        return lp;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, LauncherTheme.dp(this, dp)));
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = GameSourceHelper.createStoragePermissionIntent(this);
            if (intent != null) {
                startActivity(intent);
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE_LEGACY);
        }
    }

    private void openFolderPicker() {
        if (!GameSourceHelper.hasStoragePermission(this)) {
            requestStoragePermission();
            Toast.makeText(this, s.storageBannerDesc(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQ_PICK_FOLDER);
    }

    private void openIsoPicker() {
        if (!GameSourceHelper.hasStoragePermission(this)) {
            requestStoragePermission();
            Toast.makeText(this, s.storageBannerDesc(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_ISO);
    }

    private void openDriverZipPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "*/*"});
        startActivityForResult(intent, REQ_PICK_DRIVER_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_PICK_FOLDER) {
            String path = GameSourceHelper.resolveTreeUriToPath(uri);
            if (path == null) {
                Toast.makeText(this, s.t("Could not resolve folder path", "Не удалось определить путь к папке"), Toast.LENGTH_LONG).show();
                return;
            }
            GameSourceHelper.ValidationResult check = GameSourceHelper.validateGameSource(this, path, s);
            config.gamePath = path;
            config.savePrefsOnly();
            refreshUi();
            Toast.makeText(this, check.valid ? check.detail : check.detail, Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_PICK_ISO) {
            String path = GameSourceHelper.resolveDocumentUriToPath(this, uri);
            if (path == null) {
                Toast.makeText(this, s.t("Select the .iso file from internal storage or SD card", "Выберите .iso файл из памяти телефона"), Toast.LENGTH_LONG).show();
                return;
            }
            GameSourceHelper.ValidationResult check = GameSourceHelper.validateGameSource(this, path, s);
            config.gamePath = path;
            config.savePrefsOnly();
            refreshUi();
            Toast.makeText(this, check.detail, Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_PICK_DRIVER_ZIP) {
            String name = GameSourceHelper.queryDisplayName(this, uri);
            new Thread(() -> {
                try {
                    DriverManager.DriverEntry installed = driverManager.installFromZip(uri, name);
                    runOnUiThread(() -> {
                        config.setDriver(installed);
                        config.saveAll();
                        Toast.makeText(this, s.t("Installed driver: ", "Установлен драйвер: ") + installed.name, Toast.LENGTH_SHORT).show();
                        probeActiveDriverIsolated(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            s.t("Driver install failed: ", "Ошибка установки: ") + e.getMessage(),
                            Toast.LENGTH_LONG).show());
                }
            }, "pgr4-install-driver").start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE_LEGACY && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshUi();
        }
    }

    private void confirmDeleteDriver(DriverManager.DriverEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setMessage(s.t("Remove this installed driver?", "Удалить этот установленный драйвер?"))
                .setPositiveButton(s.btnDelete(), (d, w) -> {
                    boolean wasActive = entry.id.equals(config.driverId);
                    driverManager.deleteDriver(entry);
                    if (wasActive) {
                        config.setDriver(null);
                        config.saveAll();
                        activeDriverProbe = systemGpuInfo;
                    }
                    refreshUi();
                })
                .setNegativeButton(s.t("Cancel", "Отмена"), null)
                .show();
    }

    private void shareLastLog() {
        File log = GameSourceHelper.findLatestLogFile(this);
        if (log == null || !log.isFile()) {
            Toast.makeText(this, s.noLogsYet(), Toast.LENGTH_SHORT).show();
            return;
        }
        Uri contentUri = LogFileProvider.uriForLog(log);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_STREAM, contentUri);
        send.putExtra(Intent.EXTRA_SUBJECT, "PGR4 Android Log (" + GpuProbe.deviceModelLabel() + ")");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, s.btnShareLog()));
    }

    private void launchGame(GameSourceHelper.ValidationResult gameVal) {
        if (!gameVal.valid) {
            Toast.makeText(this, gameVal.detail, Toast.LENGTH_LONG).show();
            return;
        }
        config.saveAll();
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_GAME_PATH, gameVal.resolvedPath);
        intent.putExtra(GameActivity.EXTRA_FROM_LAUNCHER, true);
        startActivity(intent);
    }
}
