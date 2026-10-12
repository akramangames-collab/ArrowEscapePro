package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.DashPathEffect;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.graphics.Region;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ArrowGameView extends View {
    public interface Host {
        void onLevelCompleted();
        void requestRewardedErase();
        default void openBoosters() {}
        void requestRewardedRevive();
        void requestRewardedCoins();
        void openWallet();
        void openSettings();
        default void openHome() {}
        default void onRouteOpened() {}
        default void onGameplayStarted() {}
        void onContinueAfterWin(Runnable proceed);
    }

    private static final int MAX_LEVEL = CampaignRules.LEVEL_COUNT;
    private static final String LEVEL_PACK_VERSION = "v28-cadence-400-1";
    private static final int NAVY = Color.rgb(248, 251, 255);
    private static final int MUTED = Color.rgb(142,177,218);
    private static final int BLUE = Color.rgb(34, 211, 238);
    private static final int PALE = Color.rgb(10, 28, 58);
    private static final int DOT = Color.rgb(27, 58, 96);
    private static final int RED = Color.rgb(255, 92, 120);
    private static final int LOST_HEART = Color.rgb(52, 72, 108);
    private static final int TEXT = Color.rgb(151, 177, 214);
    private static final int SUPER_HARD = Color.rgb(255, 92, 118);
    private static final int BOSS_GOLD = Color.rgb(255, 200, 61);
    private static final int DAILY_PURPLE = Color.rgb(177, 114, 255);

    private enum Screen { PLAY, LEVELS }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Host host;
    private final SharedPreferences prefs;
    private final SharedPreferences settings;
    private final Wallet wallet;
    private boolean paused;
    private int mistakes, winReward, earnedStars, assistsUsed;
    private int combo, bestCombo;
    private long lastCorrectTapAt = 0L;
    private boolean flowShield;
    private boolean dailyChallenge, weeklyChallenge;
    private long challengeDay = -1L, challengeWeek = -1L, milestoneIntroUntil = 0L, comboFlashUntil = 0L;
    private int normalLevelBeforeChallenge = 1;
    private String runId;
    private final RectF walletHit = new RectF(), hintHit = new RectF(), eraseHit = new RectF(), rewardHit = new RectF();
    private final RectF primaryHit = new RectF(), secondaryHit = new RectF(), tertiaryHit = new RectF();
    private final Vibrator vibrator;
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 58);
    private final Random rng = new Random();
    private final ArrayList<Piece> pieces = new ArrayList<>();
    private String[] packedLevels;
    private String[] packedDailyChallenges;

    private Screen screen = Screen.PLAY;
    private int level;
    private int maxUnlocked;
    private int hearts = 3;
    private int hints = 2;
    private int erasers = 1;
    private int boosterMode=0; // 1 precise eraser, 2 area bomb, 3 row sweep
    private float boosterX,boosterY;
    private boolean boosterTargeted;
    private final ArrayList<Piece> boosterTargets=new ArrayList<>();
    private long blastUntil;
    private float blastX,blastY;
    public static final int BOMB_COST=60, ROW_COST=75;
    private int gridW = 24, gridH = 30;
    private boolean finished = false;
    private boolean failed = false;
    private boolean tutorial;
    private boolean settingsOpen = false;
    private int levelPage = 0;
    private float mapScroll = 0, mapDownY, mapLastY;
    private boolean mapDragging;
    private final RectF[] mapNodeHits = new RectF[20];
    private final RectF mapPrevHit = new RectF(), mapNextHit = new RectF(), mapCurrentHit = new RectF();
    private float mapViewportTop, mapViewportBottom;

    private float boardLeft, boardTop, cell, boardW, boardH;
    private float boardZoom = 1f, boardPanX = 0f, boardPanY = 0f;
    private float boardViewportLeft, boardViewportTop, boardViewportRight, boardViewportBottom;
    private float boardViewportCenterX, boardViewportCenterY;
    private boolean zoomGestureActive;
    private float lastZoomFocusX, lastZoomFocusY;
    private final ScaleGestureDetector zoomDetector;
    private static final float MIN_BOARD_ZOOM = 1f;
    private static final float MAX_BOARD_ZOOM = 2.35f;
    private static final int SHAPE_MASK_ROWS = 121;
    private int shapeMaskLevel = -1;
    private final ArrayList<float[]> shapeMaskIntervals = new ArrayList<>();
    private final Path normalizedShapePath = new Path();
    private int insetTop = 0, insetBottom = 0;
    private long lastFrame = 0L;
    private long touchRippleUntil = 0L;
    private float rippleX, rippleY;
    private Piece pressPiece, previewPiece;
    private boolean longPressPreview;
    private String toast = "";
    private long toastUntil = 0L;

    private static class Piece {
        int id;
        ArrayList<Point> pts = new ArrayList<>();
        int dx, dy;
        HashSet<Long> nodes = new HashSet<>();
        HashSet<Long> edges = new HashSet<>();
        boolean removed = false;
        boolean moving = false;
        float moveT = 0f;
        int moveSteps = 0;
        long flashUntil = 0L;
        long hintUntil = 0L;
        // V18 special mechanics. Requirements are derived only from existing
        // physical blockers, so they never make a solvable board impossible.
        int specialType = 0; // 1 key, 2 lock, 3 frozen, 4 switch, 5 gate, 6 link-source, 7 linked
        int prereqA = -1, prereqB = -1, specialTarget = -1;
    }

    public ArrowGameView(Context context, Host host, Wallet wallet) {
        super(context);
        this.host = host;
        this.wallet = wallet;
        zoomDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                zoomGestureActive = true;
                pressPiece = null;
                previewPiece = null;
                longPressPreview = false;
                lastZoomFocusX = detector.getFocusX();
                lastZoomFocusY = detector.getFocusY();
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();

                // Two-finger movement pans the enlarged board.
                boardPanX += focusX - lastZoomFocusX;
                boardPanY += focusY - lastZoomFocusY;

                float previous = boardZoom;
                float requested = previous * detector.getScaleFactor();
                boardZoom = Math.max(MIN_BOARD_ZOOM, Math.min(MAX_BOARD_ZOOM, requested));
                float ratio = previous <= 0f ? 1f : boardZoom / previous;

                // Keep the puzzle point under the user's fingers visually anchored while zooming.
                boardPanX = focusX - boardViewportCenterX
                    - (focusX - boardViewportCenterX - boardPanX) * ratio;
                boardPanY = focusY - boardViewportCenterY
                    - (focusY - boardViewportCenterY - boardPanY) * ratio;

                if (boardZoom <= MIN_BOARD_ZOOM + .01f) {
                    boardZoom = MIN_BOARD_ZOOM;
                    boardPanX = 0f;
                    boardPanY = 0f;
                }
                lastZoomFocusX = focusX;
                lastZoomFocusY = focusY;
                clampBoardPan();
                invalidate();
                return true;
            }
        });
        settings = context.getSharedPreferences("arrow_escape_pro", Context.MODE_PRIVATE);
        prefs = context.getSharedPreferences("arrow_puzzle_faithful", Context.MODE_PRIVATE);
        maxUnlocked = Math.min(MAX_LEVEL,Math.max(1,prefs.getInt("maxUnlocked",1)));
        if(maxUnlocked==200&&prefs.getInt("stars_200",0)>0){maxUnlocked=201;prefs.edit().putInt("maxUnlocked",201).apply();}
        level = Math.min(MAX_LEVEL, Math.max(1, prefs.getInt("lastLevel", 1)));
        if(!prefs.getBoolean("v28_migrated",false)){
            boolean legacy=true;for(int lv:new int[]{25,50,100,150,200})if(prefs.getInt("stars_"+lv,0)==0)legacy=false;
            prefs.edit().putBoolean("legacy_boss_hunter",legacy).putBoolean("v28_migrated",true).apply();
        }
        tutorial = !prefs.getBoolean("tutorialSeen", false);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        setBackgroundColor(Color.rgb(5,11,30));
        setFocusable(true);
        loadPackedLevels();
        loadPackedDailyChallenges();
        setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                insetTop = bars.top;
                insetBottom = bars.bottom;
            } else {
                insetTop = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            invalidate();
            return insets;
        });
        String checkpoint = prefs.getString("v16_progress", "");
        startLevel(level);
        restoreProgress(checkpoint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (screen == Screen.PLAY) {
            if (!paused && !tutorial) updateAnimations(dt, now);
            drawPlay(c, now);
        } else {
            drawLevels(c);
        }
        if (isShown() && !paused && !settings.getBoolean("reduce_motion",false) && (screen != Screen.PLAY || finished)) postInvalidateDelayed(33);
        if (toastUntil > now) drawToast(c, toast);
        if (previewPiece != null && previewPiece.removed) previewPiece = null;
        if (touchRippleUntil > now && !settings.getBoolean("reduce_motion",false)) {
            float t = 1f - (touchRippleUntil - now) / 360f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb((int)(110 * (1f-t)), 150, 164, 184));
            c.drawCircle(rippleX, rippleY, dp(10) + dp(18)*t, paint);
            paint.setStyle(Paint.Style.FILL);
            invalidate();
        }
    }

    private void updateAnimations(float dt, long now) {
        boolean any = false;
        for (Piece p : pieces) {
            if (p.moving && !p.removed) {
                float travelPx = Math.max(cell, p.moveSteps * cell);
        float flowBoost=1f+Math.min(10,combo)*0.035f;
        float speedPxPerSec =
                Math.max(getWidth() * 1.30f, cell * 9f) * flowBoost;

        float travelSec =
                Math.max(0.30f,
                Math.min(1.45f,
                travelPx / speedPxPerSec));

        p.moveT += dt / travelSec;
                if (p.moveT >= 1f) {
                    p.moveT = 1f;
                    p.moving = false;
                    p.removed = true;
                    if (p.specialType == 1 && p.specialTarget >= 0) showToast("Key collected · locked arrow opened");
                    else if (p.specialType == 4 && p.specialTarget >= 0) showToast("Switch activated · gate opened");
                    else if (p.specialType == 6 && p.specialTarget >= 0) showToast("Link released · partner arrow is ready");
                } else any = true;
            }
            if (p.flashUntil > now || p.hintUntil > now) any = true;
        }
        if (!finished && !failed && remaining() == 0) {
            finished = true;
            earnedStars = computeStars();
            if (weeklyChallenge) {
                saveWeeklyChallengeStars(challengeWeek, earnedStars);
                winReward = wallet.rewardWeeklyChallenge(challengeWeek, earnedStars);
            } else if (dailyChallenge) {
                saveDailyChallengeStars(challengeDay, earnedStars);
                winReward = wallet.rewardDailyChallenge(challengeDay, earnedStars);
            } else {
                if (level >= maxUnlocked && level < MAX_LEVEL) {
                    maxUnlocked = level + 1;
                    prefs.edit().putInt("maxUnlocked", maxUnlocked).apply();
                }
                prefs.edit().putInt("lastLevel", Math.min(MAX_LEVEL, level + 1)).apply();
                saveBestStars(level, earnedStars);
                winReward = wallet.rewardLevel(runId, level, earnedStars);
            }
            playSound(ToneGenerator.TONE_PROP_ACK, 180);
            buzz(isBoss(level) ? 140 : 70);
            saveProgress();
            host.onLevelCompleted();
            any = true;
        }
        if (any) invalidate();
    }

    private void drawPlay(Canvas c, long now) {
        c.drawColor(boardBackground());
        float w = getWidth(), h = getHeight(), top = insetTop + dp(8);
        drawThemeAtmosphere(c,w,h);
        if(isBoss(level) && !challengeActive()) drawBossWorldBackdrop(c,w,h);
        RectF headerCard=new RectF(dp(12),top+dp(2),w-dp(12),top+dp(112));
        paint.setShader(new LinearGradient(0,top,w,top+dp(112),0xEE233B68,0xEE25234F,Shader.TileMode.CLAMP));c.drawRoundRect(headerCard,dp(22),dp(22),paint);paint.setShader(null);
        strokeRound(c,headerCard,Color.argb(210,31,83,133),dp(1));
        drawBack(c, dp(28), top + dp(30));
        String title = weeklyChallenge ? "Weekly Challenge" : dailyChallenge ? "Daily Challenge" : (isBoss(level) ? "Extreme Level " + level : "Level " + level);
        label(c, title, w/2, top + dp(29), 25, NAVY, true);
        int difficultyColor = challengeActive() ? DAILY_PURPLE : (isBoss(level) ? BOSS_GOLD : isSuperHard(level) ? SUPER_HARD : Color.rgb(168,189,220));
        String sub = weeklyChallenge
            ? "ELITE WEEKLY  ·  puzzle " + level
            : dailyChallenge
            ? "SUPER HARD DAILY  ·  puzzle " + dailyChallengeNumberForDay(challengeDay)
            : "CH " + chapterNumber(level) + " · " + chapterName(level) + " · " + difficulty();
        if (!challengeActive() && combo >= 2) sub += " · STREAK x" + combo;
        label(c, sub, w/2, top + dp(49), 11, difficultyColor, challengeActive() || isSuperHard(level) || combo >= 3);
        if(combo>=2)label(c,"FLOW ×"+combo,w/2,top+dp(65),10,BLUE,true);
        drawGear(c, w-dp(31), top+dp(30));
        float statY = top + dp(84);
        RectF remainingCard=new RectF(dp(16),statY-dp(21),dp(94),statY+dp(21));
        pill(c,remainingCard,PALE);strokeRound(c,remainingCard,Color.rgb(31,83,133),dp(1));
        drawMiniArrow(c,dp(32),statY,dp(12),BLUE,-45);
        label(c,String.valueOf(remaining()),dp(65),statY+dp(6),18,NAVY,true);
        for(int i=0;i<3;i++) drawHeart(c,w/2-dp(30)+i*dp(30),statY,dp(11),i<hearts?RED:LOST_HEART);
        walletHit.set(w-dp(112),statY-dp(21),w-dp(16),statY+dp(21));
        pill(c,walletHit,Color.rgb(58,42,8));strokeRound(c,walletHit,Color.rgb(143,106,19),dp(1)); drawCoin(c,walletHit.left+dp(19),statY,dp(10));
        String amount = wallet.balance()>99999 ? (wallet.balance()/1000)+"k" : String.valueOf(wallet.balance());
        label(c,amount,walletHit.centerX()+dp(10),statY+dp(6),16,NAVY,true);
        float controlY=h-insetBottom-dp(58),gap=dp(10),controlW=(w-dp(32)-2*gap)/3;
        hintHit.set(dp(16),controlY-dp(28),dp(16)+controlW,controlY+dp(28));
        eraseHit.set(hintHit.right+gap,hintHit.top,hintHit.right+gap+controlW,hintHit.bottom);
        rewardHit.set(eraseHit.right+gap,hintHit.top,w-dp(16),hintHit.bottom);
        float areaTop=statY+dp(80), areaBottom=hintHit.top-dp(23);
        float availW=w-dp(30),availH=Math.max(dp(80),areaBottom-areaTop);
        float fitCell=Math.min(availW/(gridW+2f),availH/(gridH+2f));
        boardViewportLeft=dp(8);boardViewportTop=areaTop;boardViewportRight=w-dp(8);boardViewportBottom=areaBottom;
        boardViewportCenterX=(boardViewportLeft+boardViewportRight)/2f;
        boardViewportCenterY=(boardViewportTop+boardViewportBottom)/2f;
        cell=fitCell*boardZoom;
        boardW=gridW*cell;boardH=gridH*cell;
        clampBoardPan();
        boardLeft=boardViewportCenterX-boardW/2f+boardPanX;
        boardTop=boardViewportCenterY-boardH/2f+boardPanY;
        c.save();c.clipRect(boardViewportLeft,boardViewportTop,boardViewportRight,boardViewportBottom);drawDottedGrid(c);drawPieces(c,now);drawPathPreview(c);drawBoosterPreview(c,now);c.restore();
        if(boosterMode==0)label(c,"PINCH TO ZOOM  ·  "+Math.round(boardZoom*100f)+"%",w/2,areaBottom+dp(16),9,Color.rgb(102,145,194),false);
        polishedPill(c,hintHit,0xFF294D77,0xFF17304E);strokeRound(c,hintHit,0xFF5C92C9,dp(1));
        polishedPill(c,eraseHit,0xFF674AA0,0xFF332B64);strokeRound(c,eraseHit,0xFFAD91EB,dp(1));
        polishedPill(c,rewardHit,0xFF735623,0xFF3F321A);strokeRound(c,rewardHit,0xFFD9B65B,dp(1));
        drawBulb(c,hintHit.centerX(),controlY-dp(8),dp(12));
        label(c,hints>0?"Hint · "+hints+" free":"Hint · 25 coins",hintHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawEraser(c,eraseHit.centerX(),controlY-dp(8),dp(12));
        label(c,"Boosters",eraseHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawMiniArrow(c,rewardHit.centerX(),controlY-dp(8),dp(13),BLUE,0);
        label(c,"Sweep · "+ROW_COST,rewardHit.centerX(),controlY+dp(18),11,NAVY,true);
        label(c,"THINK  ·  TAP  ·  ESCAPE",w/2,h-insetBottom-dp(10),9,Color.rgb(96,132,184),false);
        if(boosterMode!=0&&!finished&&!failed){
            pill(c,hintHit,Color.rgb(12,61,103));pill(c,eraseHit,Color.rgb(15,73,64));pill(c,rewardHit,Color.rgb(58,42,8));
            label(c,"CANCEL",hintHit.centerX(),controlY+dp(5),12,NAVY,true);
            label(c,"USE · "+boosterTargets.size(),eraseHit.centerX(),controlY+dp(5),12,NAVY,true);
            label(c,boosterMode==1?"FREE ERASER":(boosterMode==2?BOMB_COST:ROW_COST)+" COINS",rewardHit.centerX(),controlY+dp(5),10,BOSS_GOLD,true);
            label(c,boosterTargeted?"Preview only · tap USE to confirm":"Tap the board to choose the target",w/2,areaBottom+dp(16),10,BLUE,true);
        }
        if(tutorial)drawTutorial(c);else if(finished)drawWin(c);else if(failed)drawFail(c);else if(milestoneIntroUntil>now)drawMilestoneIntro(c);
    }

    private void drawThemeAtmosphere(Canvas c,float w,float h){
        int t=currentThemeIndex(),accent=themeAccent();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.1f));
        if(t==1){
            paint.setColor(Color.argb(42,Color.red(accent),Color.green(accent),Color.blue(accent)));
            for(int i=0;i<18;i++){float x=(i*73%100)/100f*w,y=(i*47%100)/100f*h;c.drawCircle(x,y,dp(2+(i%3)),paint);}
        }else if(t==2){
            paint.setColor(Color.argb(34,Color.red(accent),Color.green(accent),Color.blue(accent)));
            float cx=w*.82f,cy=h*.16f;for(int i=1;i<=4;i++)c.drawCircle(cx,cy,dp(25*i),paint);
        }else if(t==3){
            paint.setColor(Color.argb(34,Color.red(accent),Color.green(accent),Color.blue(accent)));
            for(int i=0;i<12;i++){float x=(i*91%100)/100f*w,y=(i*61%100)/100f*h;c.drawCircle(x,y,dp(4),paint);c.drawLine(x-dp(5),y,x+dp(5),y,paint);}
        }else if(t==4){
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(110,210,220,255));
            for(int i=0;i<28;i++){float x=(i*37%100)/100f*w,y=(i*67%100)/100f*h;c.drawCircle(x,y,dp(i%5==0?1.8f:.9f),paint);}
        }else if(t==5){
            paint.setColor(Color.argb(52,255,112,72));
            for(int i=0;i<14;i++){float x=(i*83%100)/100f*w,y=(i*41%100)/100f*h;c.drawLine(x,y,x+dp(7),y-dp(13),paint);}
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFlowMeter(Canvas c,float cx,float y){
        float gap=dp(5),r=dp(2.6f),start=cx-2*(r*2+gap);
        int filled=Math.min(5,combo);
        for(int i=0;i<5;i++){
            paint.setColor(i<filled?themeAccent():Color.rgb(210,218,230));
            c.drawCircle(start+i*(r*2+gap),y,r,paint);
        }
        if(flowShield){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(themeAccent());
            c.drawCircle(cx+dp(43),y,dp(6.5f),paint);paint.setStyle(Paint.Style.FILL);
            label(c,"S",cx+dp(43),y+dp(3),7,themeAccent(),true);
        }
    }

    private void drawBossWorldBackdrop(Canvas c,float w,float h){
        int accent=level==25?Color.rgb(239,178,50):level==50?Color.rgb(116,78,194):level==100?Color.rgb(44,164,200):level==150?Color.rgb(222,92,75):Color.rgb(26,129,112);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(Color.argb(28,Color.red(accent),Color.green(accent),Color.blue(accent)));
        float cx=w/2,cy=h*.49f,r=Math.min(w,h)*.31f;
        if(level==25){
            Path p=new Path();p.moveTo(cx,cy-r);p.lineTo(cx+r*.72f,cy);p.lineTo(cx,cy+r);p.lineTo(cx-r*.72f,cy);p.close();c.drawPath(p,paint);
        }else if(level==50){
            Path p=new Path();p.moveTo(cx-r,cy+r*.45f);p.lineTo(cx-r*.72f,cy-r*.38f);p.lineTo(cx-r*.18f,cy+r*.02f);p.lineTo(cx,cy-r*.58f);p.lineTo(cx+r*.18f,cy+r*.02f);p.lineTo(cx+r*.72f,cy-r*.38f);p.lineTo(cx+r,cy+r*.45f);p.close();c.drawPath(p,paint);
        }else if(level==100){
            c.drawCircle(cx-r*.38f,cy,r*.48f,paint);c.drawCircle(cx+r*.38f,cy,r*.48f,paint);
        }else if(level==150){
            Path p=new Path();p.moveTo(cx,cy-r);p.lineTo(cx+r*.42f,cy+r*.38f);p.lineTo(cx,cy+r*.18f);p.lineTo(cx-r*.42f,cy+r*.38f);p.close();c.drawPath(p,paint);c.drawLine(cx,cy+r*.18f,cx,cy+r,paint);
        }else{
            for(int i=1;i<=4;i++)c.drawCircle(cx,cy,r*i/4f,paint);
            for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(cx,cy,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,paint);}
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(44,Color.red(accent),Color.green(accent),Color.blue(accent)));
        label(c,"BOSS WORLD · "+(level==25?"DIAMOND":level==50?"CROWN":level==100?"INFINITY":level==150?"ROCKET":"GRANDMASTER"),w/2,h*.20f,11,accent,true);
    }

    private static final String[] SPECIAL_SHAPES={
        "Heart","Star","Crown","Rocket","Butterfly","Diamond","Shield","Lightning","Moon","Flower"
    };

    public String shapeNameForLevel(int lv) {
        int index=Math.floorMod(lv-1,46);
        if(index<26)return String.valueOf((char)('A'+index));
        if(index<36)return String.valueOf((char)('0'+index-26));
        return SPECIAL_SHAPES[index-36];
    }
    public String currentShapeName(){return shapeNameForLevel(currentLevelNumber());}
    public int currentChapterNumber(){return chapterNumber(currentLevelNumber());}
    public String currentChapterName(){return chapterName(currentLevelNumber());}
    private boolean alphanumericShape(String token){return token.length()==1&&Character.isLetterOrDigit(token.charAt(0));}
    private float clamp01(float v){return Math.max(0f,Math.min(1f,v));}

    private void ensureShapeMask(){
        if(shapeMaskLevel==level&&!shapeMaskIntervals.isEmpty())return;
        shapeMaskLevel=level;shapeMaskIntervals.clear();normalizedShapePath.reset();
        String token=shapeNameForLevel(level);
        if(alphanumericShape(token)) {
            buildGlyphShape(token,normalizedShapePath);
            normalizedShapePath.setFillType(Path.FillType.EVEN_ODD);
        } else {
            buildSpecialShape(token,normalizedShapePath);
            normalizedShapePath.setFillType("Moon".equals(token)?Path.FillType.EVEN_ODD:Path.FillType.WINDING);
        }

        Region region=new Region();
        region.setPath(normalizedShapePath,new Region(0,0,1000,1200));
        for(int row=0;row<SHAPE_MASK_ROWS;row++){
            int y=Math.round(60f+1080f*row/(SHAPE_MASK_ROWS-1f));
            ArrayList<Float> spans=new ArrayList<>();
            boolean inside=false;int startX=0;
            for(int x=20;x<=980;x++){
                boolean now=region.contains(x,y);
                if(now&&!inside){inside=true;startX=x;}
                if(!now&&inside){inside=false;spans.add((float)startX);spans.add((float)(x-1));}
            }
            if(inside){spans.add((float)startX);spans.add(980f);}
            float[] packed=new float[spans.size()];
            for(int i=0;i<spans.size();i++)packed[i]=spans.get(i);
            shapeMaskIntervals.add(packed);
        }
    }

    private void buildGlyphShape(String token,Path out){
        Paint glyph=new Paint(Paint.ANTI_ALIAS_FLAG);
        glyph.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        glyph.setTextSize(1000f);
        glyph.getTextPath(token,0,token.length(),0,0,out);
        RectF bounds=new RectF();out.computeBounds(bounds,true);
        float targetW=850f,targetH=1030f;
        float scale=Math.min(targetW/Math.max(1f,bounds.width()),targetH/Math.max(1f,bounds.height()));
        Matrix m=new Matrix();m.setScale(scale,scale);out.transform(m);
        out.computeBounds(bounds,true);
        Matrix move=new Matrix();move.setTranslate(500f-bounds.centerX(),600f-bounds.centerY());out.transform(move);
    }

    private void buildSpecialShape(String token,Path p){
        if("Heart".equals(token)){
            p.moveTo(500,1110);p.cubicTo(430,1000,90,760,90,390);p.cubicTo(90,160,360,90,500,305);p.cubicTo(640,90,910,160,910,390);p.cubicTo(910,760,570,1000,500,1110);p.close();
        }else if("Star".equals(token)){
            for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=(i%2==0)?500:225;float x=500+(float)Math.cos(a)*r,y=610+(float)Math.sin(a)*r;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();
        }else if("Crown".equals(token)){
            p.moveTo(85,1030);p.lineTo(115,320);p.lineTo(300,570);p.lineTo(500,150);p.lineTo(700,570);p.lineTo(885,320);p.lineTo(915,1030);p.close();
        }else if("Rocket".equals(token)){
            p.moveTo(500,80);p.cubicTo(760,250,800,590,690,900);p.lineTo(885,1080);p.lineTo(650,1030);p.lineTo(500,1140);p.lineTo(350,1030);p.lineTo(115,1080);p.lineTo(310,900);p.cubicTo(200,590,240,250,500,80);p.close();
        }else if("Butterfly".equals(token)){
            p.moveTo(485,170);p.cubicTo(350,120,90,220,120,520);p.cubicTo(140,700,330,660,430,590);p.cubicTo(300,770,250,1090,455,1020);p.lineTo(500,680);p.lineTo(545,1020);p.cubicTo(750,1090,700,770,570,590);p.cubicTo(670,660,860,700,880,520);p.cubicTo(910,220,650,120,515,170);p.close();
        }else if("Diamond".equals(token)){
            p.moveTo(500,70);p.lineTo(925,600);p.lineTo(500,1130);p.lineTo(75,600);p.close();
        }else if("Shield".equals(token)){
            p.moveTo(500,80);p.cubicTo(670,180,800,190,900,190);p.lineTo(860,690);p.cubicTo(830,900,650,1050,500,1130);p.cubicTo(350,1050,170,900,140,690);p.lineTo(100,190);p.cubicTo(200,190,330,180,500,80);p.close();
        }else if("Lightning".equals(token)){
            p.moveTo(560,70);p.lineTo(210,650);p.lineTo(450,650);p.lineTo(350,1130);p.lineTo(790,500);p.lineTo(545,500);p.close();
        }else if("Moon".equals(token)){
            p.addCircle(500,600,470,Path.Direction.CW);p.addCircle(675,510,390,Path.Direction.CW);
        }else{
            p.addCircle(500,600,150,Path.Direction.CW);
            for(int i=0;i<8;i++){double a=i*Math.PI/4;float x=500+(float)Math.cos(a)*300,y=600+(float)Math.sin(a)*300;p.addCircle(x,y,190,Path.Direction.CW);}
        }
    }

    private float[] nearestMaskRow(int row){
        ensureShapeMask();row=Math.max(0,Math.min(SHAPE_MASK_ROWS-1,row));
        if(shapeMaskIntervals.get(row).length>0)return shapeMaskIntervals.get(row);
        for(int d=1;d<SHAPE_MASK_ROWS;d++){
            int a=row-d,b=row+d;
            if(a>=0&&shapeMaskIntervals.get(a).length>0)return shapeMaskIntervals.get(a);
            if(b<SHAPE_MASK_ROWS&&shapeMaskIntervals.get(b).length>0)return shapeMaskIntervals.get(b);
        }
        return new float[]{100,900};
    }

    private float maskX(float u,float v){
        int row=Math.round(clamp01(v)*(SHAPE_MASK_ROWS-1));
        float[] spans=nearestMaskRow(row);
        float total=0f;for(int i=0;i<spans.length;i+=2)total+=Math.max(1f,spans[i+1]-spans[i]);
        float target=clamp01(u)*total;
        for(int i=0;i<spans.length;i+=2){
            float width=Math.max(1f,spans[i+1]-spans[i]);
            if(target<=width)return (spans[i]+target)/1000f;
            target-=width;
        }
        return spans[spans.length-1]/1000f;
    }

    private android.graphics.PointF shapePointInside(float gx,float gy){
        return new android.graphics.PointF(boardLeft+gx*cell,boardTop+gy*cell);
    }

    private android.graphics.PointF shapePoint(float gx,float gy){
        return new android.graphics.PointF(boardLeft+gx*cell,boardTop+gy*cell);
    }

    private android.graphics.PointF shapeDirection(float gx,float gy,float dx,float dy){
        return new android.graphics.PointF(dx,dy);
    }

    private void drawShapeOutline(Canvas c){
        // Shapes removed for release 1.0.0. Keep the normal puzzle grid only.
    }

    private void drawPathPreview(Canvas c) {
        Piece p=previewPiece;
        if(p==null||p.removed||p.moving||p.pts.isEmpty())return;
        Point tip=p.pts.get(p.pts.size()-1);
        int steps=gridW+gridH+10;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1.5f),cell*.09f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(125,47,149,235));
        android.graphics.PointF prev=shapePoint(tip.x,tip.y);
        for(int i=1;i<=steps;i++){
            android.graphics.PointF next=shapePoint(tip.x+p.dx*i,tip.y+p.dy*i);
            if(i%2==1)c.drawLine(prev.x,prev.y,next.x,next.y,paint);
            prev=next;
            if((p.dx<0&&tip.x+p.dx*i<-3)||(p.dx>0&&tip.x+p.dx*i>gridW+3)||(p.dy<0&&tip.y+p.dy*i<-3)||(p.dy>0&&tip.y+p.dy*i>gridH+3))break;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDottedGrid(Canvas c) {
        paint.setColor(themeDotColor());
        for (int y=0; y<=gridH; y++) {
            float py=boardTop+y*cell;
            for (int x=0; x<=gridW; x++) {
                c.drawCircle(boardLeft+x*cell,py,Math.max(0.8f,cell*0.045f),paint);
            }
        }
    }


    private float piecePathLength(Piece p) {
        float len = 0f;

        for (int i = 0; i < p.pts.size() - 1; i++) {
  Point a = p.pts.get(i);
  Point b = p.pts.get(i + 1);

  len += Math.abs(b.x - a.x)
          + Math.abs(b.y - a.y);
        }

        return len;
    }

    private android.graphics.PointF routePoint(
  Piece p,
  float distance
    ) {
        if (distance <= 0f) {
  Point q = p.pts.get(0);

  return new android.graphics.PointF(
          q.x,
          q.y
  );
        }

        float walked = 0f;

        for (int i = 0; i < p.pts.size() - 1; i++) {
  Point a = p.pts.get(i);
  Point b = p.pts.get(i + 1);

  float seg =
          Math.abs(b.x - a.x)
          + Math.abs(b.y - a.y);

  if (distance <= walked + seg) {
      float t =
              (distance - walked)
              / Math.max(0.0001f, seg);

      return new android.graphics.PointF(
              a.x + (b.x - a.x) * t,
              a.y + (b.y - a.y) * t
      );
  }

  walked += seg;
        }

        Point tip = p.pts.get(
      p.pts.size() - 1
        );

        float extra = distance - walked;

        return new android.graphics.PointF(
      tip.x + p.dx * extra,
      tip.y + p.dy * extra
        );
    }

    private void buildMappedRoutePath(Path path,Piece p,float from,float to){
        int steps=Math.max(2,(int)Math.ceil(Math.max(.1f,to-from)*2f));
        for(int i=0;i<=steps;i++){
            float d=from+(to-from)*i/(float)steps;
            android.graphics.PointF q=routePoint(p,d);
            android.graphics.PointF s=shapePoint(q.x,q.y);
            if(i==0)path.moveTo(s.x,s.y);else path.lineTo(s.x,s.y);
        }
    }

    private void buildMovingSnakePath(
  Path path,
  Piece p,
  float advance
    ) {
        float total = piecePathLength(p);

        android.graphics.PointF start =
      routePoint(p, advance);

        path.moveTo(
      boardLeft + start.x * cell,
      boardTop + start.y * cell
        );

        float walked = 0f;

        for (int i = 0; i < p.pts.size() - 1; i++) {
  Point a = p.pts.get(i);
  Point b = p.pts.get(i + 1);

  float seg =
          Math.abs(b.x - a.x)
          + Math.abs(b.y - a.y);

  walked += seg;

  if (walked > advance) {
      path.lineTo(
              boardLeft + b.x * cell,
              boardTop + b.y * cell
      );
  }
        }

        android.graphics.PointF head =
      routePoint(
              p,
              advance + total
      );

        path.lineTo(
      boardLeft + head.x * cell,
      boardTop + head.y * cell
        );
    }



    private int arrowType(){
        int selected=Math.floorMod(settings.getInt("arrow_type",0),5);
        return wallet.ownsArrowType(selected)?selected:0;
    }
    private float arrowStroke(){
        switch(arrowType()){case 1:return .078f;case 2:return .175f;case 3:return .105f;case 4:return .125f;default:return .12f;}
    }
    private float arrowHeadScale(){
        switch(arrowType()){case 1:return .27f;case 2:return .42f;case 3:return .35f;case 4:return .38f;default:return .34f;}
    }

    private void drawArrowHeadAt(Canvas c,float gx,float gy,int dx,int dy,int col,int alpha) {
        android.graphics.PointF p=shapePoint(gx,gy),dir=shapeDirection(gx,gy,dx,dy);
        drawClearArrowHead(c,p.x,p.y,dir.x,dir.y,col,alpha);
    }

    /**
     * V20 has real arrow TYPES, not just different colours.
     * Classic, Slim, Bold, Chevron and Neon all share the same gameplay geometry.
     */
    private void drawClearArrowHead(Canvas c,float tx,float ty,float dx,float dy,int col,int alpha) {
        float len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.001f){dx=1;dy=0;}else{dx/=len;dy/=len;}
        float s=Math.max(dp(5.8f),cell*arrowHeadScale());
        float px=-dy,py=dx;
        int type=arrowType();

        if(type==3){ // Chevron
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(dp(1.8f),s*.20f));paint.setColor(Color.argb(alpha,Color.red(col),Color.green(col),Color.blue(col)));
            Path v=new Path();v.moveTo(tx-dx*s*.52f+px*s*.68f,ty-dy*s*.52f+py*s*.68f);v.lineTo(tx+dx*s*.78f,ty+dy*s*.78f);v.lineTo(tx-dx*s*.52f-px*s*.68f,ty-dy*s*.52f-py*s*.68f);c.drawPath(v,paint);
            paint.setStyle(Paint.Style.FILL);return;
        }

        Path a=new Path();
        float wing=type==1?.52f:type==2?.82f:.72f;
        float neck=type==1?.18f:type==2?.31f:.25f;
        float back=type==1?.76f:type==2?1.02f:.92f;
        a.moveTo(tx+dx*s*.82f,ty+dy*s*.82f);
        a.lineTo(tx-dx*s*.50f+px*s*wing,ty-dy*s*.50f+py*s*wing);
        a.lineTo(tx-dx*s*.28f+px*s*neck,ty-dy*s*.28f+py*s*neck);
        a.lineTo(tx-dx*s*back+px*s*neck,ty-dy*s*back+py*s*neck);
        a.lineTo(tx-dx*s*back-px*s*neck,ty-dy*s*back-py*s*neck);
        a.lineTo(tx-dx*s*.28f-px*s*neck,ty-dy*s*.28f-py*s*neck);
        a.lineTo(tx-dx*s*.50f-px*s*wing,ty-dy*s*.50f-py*s*wing);
        a.close();

        if(type==4){ // Neon glow
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(Math.min(85,alpha),Color.red(col),Color.green(col),Color.blue(col)));
            c.save();c.scale(1.32f,1.32f,tx,ty);c.drawPath(a,paint);c.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha,Color.red(col),Color.green(col),Color.blue(col)));
        c.drawPath(a,paint);
    }

    private void drawPieces(Canvas c,long now) {
        float stroke=Math.max(dp(2.4f),cell*arrowStroke());
        for(Piece p:pieces){
            if(p.removed)continue;
            float shake=p.flashUntil>now?(float)Math.sin(now*.085)*dp(3.5f):0f;
            int col=p.flashUntil>now?Color.rgb(228,65,71):(p.hintUntil>now?Color.rgb(236,167,28):(settings.getBoolean("contrast",false)?Color.WHITE:selectedArrowColor()));
            int alpha=255;
            paint.setColor(col);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
            paint.setStrokeCap(arrowType()==1?Paint.Cap.ROUND:Paint.Cap.SQUARE);paint.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            if(p.moving){
                float advance=p.moveT*p.moveSteps;
                buildMovingSnakePath(path,p,advance);
                if(arrowType()==4){paint.setStrokeWidth(stroke*2.5f);paint.setColor(Color.argb(45,Color.red(col),Color.green(col),Color.blue(col)));c.drawPath(path,paint);paint.setStrokeWidth(stroke);paint.setColor(col);}
                c.drawPath(path,paint);
                float total=piecePathLength(p);
                android.graphics.PointF head=routePoint(p,advance+total);
                drawArrowMotionEffect(c,p,advance,total,col);
                paint.setStyle(Paint.Style.FILL);
                drawArrowHeadAt(c,head.x,head.y,p.dx,p.dy,col,alpha);
            }else{
                buildMappedRoutePath(path,p,0f,piecePathLength(p));
                Point tip=p.pts.get(p.pts.size()-1);
                android.graphics.PointF dir=shapeDirection(tip.x,tip.y,p.dx,p.dy);
                float sx=-dir.y*shake,sy=dir.x*shake;
                c.save();c.translate(sx,sy);
                if(arrowType()==4){paint.setStrokeWidth(stroke*2.5f);paint.setColor(Color.argb(45,Color.red(col),Color.green(col),Color.blue(col)));c.drawPath(path,paint);paint.setStrokeWidth(stroke);paint.setColor(col);}
                c.drawPath(path,paint);
                paint.setStyle(Paint.Style.FILL);
                drawArrowHead(c,p,col,alpha);
                c.restore();
            }
            drawSpecialBadge(c,p,now);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawArrowMotionEffect(Canvas c,Piece p,float advance,float total,int col){
        int type=arrowType();
        if(type==0)return;
        android.graphics.PointF headGrid=routePoint(p,advance+total);
        android.graphics.PointF head=shapePoint(headGrid.x,headGrid.y);
        android.graphics.PointF dir=shapeDirection(headGrid.x,headGrid.y,p.dx,p.dy);

        if(type==1){ // Slim: clean comet trail
            paint.setStyle(Paint.Style.FILL);
            for(int i=1;i<=4;i++){
                float d=total+advance-i*.65f;
                android.graphics.PointF q=routePoint(p,Math.max(0f,d));
                android.graphics.PointF sp=shapePoint(q.x,q.y);
                paint.setColor(Color.argb(105-i*18,Color.red(col),Color.green(col),Color.blue(col)));
                c.drawCircle(sp.x,sp.y,Math.max(dp(1.2f),cell*.075f),paint);
            }
        }else if(type==2){ // Bold: impact pulse
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(1.4f),cell*.08f));
            paint.setColor(Color.argb(72,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawCircle(head.x,head.y,Math.max(dp(9),cell*.62f),paint);
            paint.setStyle(Paint.Style.FILL);
        }else if(type==3){ // Chevron: echo chevrons
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(dp(1.2f),cell*.07f));
            for(int i=1;i<=2;i++){
                float back=cell*(.46f+i*.30f);
                float cx=head.x-dir.x*back,cy=head.y-dir.y*back;
                float px=-dir.y,py=dir.x,s=Math.max(dp(4f),cell*.24f);
                paint.setColor(Color.argb(95-i*25,Color.red(col),Color.green(col),Color.blue(col)));
                Path v=new Path();
                v.moveTo(cx-dir.x*s*.35f+px*s*.55f,cy-dir.y*s*.35f+py*s*.55f);
                v.lineTo(cx+dir.x*s*.48f,cy+dir.y*s*.48f);
                v.lineTo(cx-dir.x*s*.35f-px*s*.55f,cy-dir.y*s*.35f-py*s*.55f);
                c.drawPath(v,paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }else if(type==4){ // Neon: moving spark pair
            paint.setStyle(Paint.Style.FILL);
            float px=-dir.y,py=dir.x;
            paint.setColor(Color.argb(150,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawCircle(head.x+px*cell*.28f,head.y+py*cell*.28f,Math.max(dp(1.5f),cell*.09f),paint);
            c.drawCircle(head.x-px*cell*.28f,head.y-py*cell*.28f,Math.max(dp(1.5f),cell*.09f),paint);
        }
    }

    private void drawArrowHead(Canvas c,Piece p,int col,int alpha) {
        Point tip=p.pts.get(p.pts.size()-1);
        android.graphics.PointF pt=shapePoint(tip.x,tip.y),dir=shapeDirection(tip.x,tip.y,p.dx,p.dy);
        drawClearArrowHead(c,pt.x,pt.y,dir.x,dir.y,col,alpha);
    }

    private void drawSpecialBadge(Canvas c,Piece p,long now) {
        if(p.specialType==0||p.removed)return;
        android.graphics.PointF pos;
        if(p.moving){float total=piecePathLength(p);pos=routePoint(p,p.moveT*p.moveSteps+total);}
        else{Point tip=p.pts.get(p.pts.size()-1);pos=new android.graphics.PointF(tip.x,tip.y);}
        android.graphics.PointF sp=shapePoint(pos.x,pos.y),dir=shapeDirection(pos.x,pos.y,p.dx,p.dy);
        float x=sp.x-dir.y*dp(10),y=sp.y+dir.x*dp(10);
        float r=Math.max(dp(6.5f),cell*.34f);
        if(p.specialType==1)drawKeyBadge(c,x,y,r);
        else if(p.specialType==2)drawLockBadge(c,x,y,r,!specialUnlocked(p));
        else if(p.specialType==3)drawFreezeBadge(c,x,y,r,!specialUnlocked(p));
        else if(p.specialType==4)drawSwitchBadge(c,x,y,r);
        else if(p.specialType==5)drawGateBadge(c,x,y,r,!specialUnlocked(p));
        else if(p.specialType==6||p.specialType==7)drawLinkBadge(c,x,y,r,p.specialType==7&&!specialUnlocked(p));
    }

    private void badgeCircle(Canvas c,float x,float y,float r,int bg){
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(x,y,r+dp(1.8f),paint);
        paint.setColor(bg);c.drawCircle(x,y,r,paint);
    }

    private void drawKeyBadge(Canvas c,float x,float y,float r){
        badgeCircle(c,x,y,r,Color.rgb(247,181,32));
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(dp(1.6f),r*.22f));
        c.drawCircle(x-r*.23f,y-r*.13f,r*.24f,paint);
        c.drawLine(x-r*.03f,y+r*.03f,x+r*.40f,y+r*.43f,paint);
        c.drawLine(x+r*.22f,y+r*.25f,x+r*.35f,y+r*.12f,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLockBadge(Canvas c,float x,float y,float r,boolean closed){
        badgeCircle(c,x,y,r,closed?Color.rgb(103,116,139):Color.rgb(47,149,235));
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1.4f),r*.18f));
        RectF shackle=new RectF(x-r*.34f,y-r*.48f,x+r*.34f,y+r*.10f);c.drawArc(shackle,180,180,false,paint);
        paint.setStyle(Paint.Style.FILL);RectF body=new RectF(x-r*.46f,y-r*.05f,x+r*.46f,y+r*.48f);c.drawRoundRect(body,r*.12f,r*.12f,paint);
    }

    private void drawFreezeBadge(Canvas c,float x,float y,float r,boolean frozen){
        badgeCircle(c,x,y,r,frozen?Color.rgb(60,173,220):Color.rgb(101,190,156));
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1.2f),r*.16f));paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<3;i++){double a=i*Math.PI/3;float dx=(float)Math.cos(a)*r*.55f,dy=(float)Math.sin(a)*r*.55f;c.drawLine(x-dx,y-dy,x+dx,y+dy,paint);}
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSwitchBadge(Canvas c,float x,float y,float r){
        badgeCircle(c,x,y,r,Color.rgb(132,88,207));
        paint.setColor(Color.WHITE);Path bolt=new Path();bolt.moveTo(x+r*.10f,y-r*.55f);bolt.lineTo(x-r*.28f,y+r*.02f);bolt.lineTo(x+r*.02f,y+r*.02f);bolt.lineTo(x-r*.08f,y+r*.55f);bolt.lineTo(x+r*.34f,y-r*.10f);bolt.lineTo(x+r*.05f,y-r*.10f);bolt.close();c.drawPath(bolt,paint);
    }

    private void drawGateBadge(Canvas c,float x,float y,float r,boolean closed){
        badgeCircle(c,x,y,r,closed?Color.rgb(222,92,75):Color.rgb(75,181,124));
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1.2f),r*.15f));
        for(int i=-1;i<=1;i++)c.drawLine(x+i*r*.28f,y-r*.48f,x+i*r*.28f,y+r*.48f,paint);
        c.drawLine(x-r*.48f,y-r*.25f,x+r*.48f,y-r*.25f,paint);c.drawLine(x-r*.48f,y+r*.25f,x+r*.48f,y+r*.25f,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLinkBadge(Canvas c,float x,float y,float r,boolean waiting){
        badgeCircle(c,x,y,r,waiting?Color.rgb(63,128,191):Color.rgb(39,169,158));
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1.2f),r*.17f));paint.setStrokeCap(Paint.Cap.ROUND);
        RectF left=new RectF(x-r*.62f,y-r*.22f,x-r*.02f,y+r*.22f);
        RectF right=new RectF(x+r*.02f,y-r*.22f,x+r*.62f,y+r*.22f);
        c.drawArc(left,35,290,false,paint);c.drawArc(right,215,290,false,paint);
        c.drawLine(x-r*.12f,y,x+r*.12f,y,paint);paint.setStyle(Paint.Style.FILL);
    }

    private void drawMilestoneIntro(Canvas c) {
        if(!challengeActive()&&(level==41||level==81||level==121||level==161)){
            RectF r=modal(c,300);
            int accent=level==41?Color.rgb(219,157,25):level==81?Color.rgb(60,173,220):level==121?Color.rgb(132,88,207):Color.rgb(39,169,158);
            String title=level==41?"KEYS & LOCKS":level==81?"FROZEN ARROWS":level==121?"SWITCHES & GATES":"LINKED ARROWS";
            String line1=level==41?"Clear the gold key arrow before its locked arrow can escape."
                :level==81?"Frozen arrows thaw only after both marked blockers are gone."
                :level==121?"Clear the purple switch arrow to open its red gate."
                :"Linked partners must be released in their chain order.";
            String line2=level==41?"The key was already part of the puzzle's blocking chain."
                :level==81?"Hints understand the ice rule, so they stay safe."
                :level==121?"Gate logic follows the same full-clearance solver."
                :"The chain is built from a real blocker relationship, never a fake dead end.";
            label(c,"NEW MECHANIC",r.centerX(),r.top+dp(39),12,accent,true);
            label(c,title,r.centerX(),r.top+dp(79),27,accent,true);
            label(c,line1,r.centerX(),r.top+dp(128),13,NAVY,true);
            label(c,line2,r.centerX(),r.top+dp(161),12,TEXT,false);
            label(c,"Wrong special-arrow taps still cost a heart.",r.centerX(),r.top+dp(195),12,RED,true);
            label(c,"Tap anywhere to start",r.centerX(),r.bottom-dp(31),12,Color.rgb(111,124,145),false);
            return;
        }
        RectF r=modal(c,challengeActive()?270:270);
        int accent=challengeActive()?DAILY_PURPLE:(isBoss(level)?BOSS_GOLD:SUPER_HARD);
        label(c,weeklyChallenge?"WEEKLY CHALLENGE":dailyChallenge?"DAILY CHALLENGE":difficulty(),r.centerX(),r.top+dp(52),28,accent,true);
        label(c,weeklyChallenge?"One elite puzzle for the whole UTC week":dailyChallenge?"Same challenge for everyone today":"Level "+level+" is a milestone",r.centerX(),r.top+dp(89),15,NAVY,true);
        label(c,isBoss(level)&&!challengeActive()?"The toughest checkpoint in this chapter":weeklyChallenge?"A fixed high-level milestone puzzle. Best stars are saved all week.":"Expect deeper blocking chains and fewer obvious exits.",r.centerX(),r.top+dp(121),13,TEXT,false);
        label(c,weeklyChallenge?"Reward: 250–350 coins based on stars":dailyChallenge?"Reward: 200 coins · once per day":(isBoss(level)?"Extreme bonus: +150 coins":"Milestone bonus: +75 coins"),r.centerX(),r.top+dp(153),13,accent,true);
        label(c,"Tap anywhere to begin",r.centerX(),r.bottom-dp(28),12,Color.rgb(111,124,145),false);
    }

    private void drawTutorial(Canvas c) {
        RectF r = modal(c, 410);
        label(c,"Find the way out",r.centerX(),r.top+dp(43),24,NAVY,true);
        label(c,"Tap only when the full escape is clear.",r.centerX(),r.top+dp(83),14,TEXT,false);
        label(c,"Other arrows — and your own tail — can block it.",r.centerX(),r.top+dp(108),13,TEXT,false);
        label(c,"The body still follows through its bends.",r.centerX(),r.top+dp(133),14,TEXT,false);
        float y=r.top+dp(183);
        paint.setColor(NAVY);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));
        Path demo=new Path();demo.moveTo(r.centerX()-dp(72),y+dp(25));demo.lineTo(r.centerX()-dp(14),y+dp(25));demo.lineTo(r.centerX()-dp(14),y);demo.lineTo(r.centerX()+dp(62),y);c.drawPath(demo,paint);paint.setStyle(Paint.Style.FILL);drawMiniArrow(c,r.centerX()+dp(63),y,dp(15),NAVY,0);
        label(c,"Blocked taps cost one heart.",r.centerX(),r.top+dp(253),14,TEXT,false);
        label(c,"Two free hints, then 25 coins each.",r.centerX(),r.top+dp(280),14,TEXT,false);
        label(c,"Clear cleanly for 3★ and bigger rewards.",r.centerX(),r.top+dp(306),14,TEXT,false);
        primaryHit.set(r.left+dp(22),r.bottom-dp(72),r.right-dp(22),r.bottom-dp(22));
        action(c,primaryHit,"Let's play",BLUE,Color.WHITE);
    }

    private void drawSettings(Canvas c) { }

    private void drawWin(Canvas c) {
        drawScenicBackdrop(c);if(!settings.getBoolean("reduce_motion",false))drawCelebration(c);
        float w=getWidth(),h=getHeight(),top=insetTop+dp(18),bottom=h-insetBottom;
        label(c,"LEVEL COMPLETE",w/2,top+dp(24),24,NAVY,true);
        label(c,challengeActive()?"Challenge cleared":"Level "+level+" cleared",w/2,top+dp(52),14,MUTED,false);
        label(c,starString(earnedStars),w/2,top+dp(97),34,BOSS_GOLD,true);
        primaryHit.set(dp(24),bottom-dp(168),w-dp(24),bottom-dp(118));
        secondaryHit.set(dp(24),bottom-dp(106),w-dp(24),bottom-dp(56));
        tertiaryHit.set(dp(24),bottom-dp(48),w-dp(24),bottom-dp(4));
        RectF card=new RectF(dp(24),top+dp(120),w-dp(24),Math.min(top+dp(250),primaryHit.top-dp(12)));
        if(card.height()>=dp(60)){polishedPill(c,card,0xFF142C4B,0xFF0A1A31);strokeRound(c,card,0xFF2A5678,dp(1));label(c,winReward>0?"+"+winReward+" COINS":"REWARD ALREADY CLAIMED",w/2,card.top+dp(29),20,BOSS_GOLD,true);label(c,"Wallet: "+wallet.balance()+" coins",w/2,card.top+dp(53),13,MUTED,false);if(dailyChallenge&&card.height()>=dp(92))label(c,"Daily challenge · +200 coins · once per day",w/2,card.top+dp(82),12,MUTED,false);}
        action(c,primaryHit,challengeActive()?"Back to Route":level<MAX_LEVEL?"NEXT LEVEL  ›":"ALL LEVELS",BLUE,0xFF001822);
        action(c,secondaryHit,"▶  WATCH AD · +75 COINS",BOSS_GOLD,0xFF221700);
        label(c,challengeActive()?"Replay challenge":"Back to Route",w/2,tertiaryHit.centerY()+dp(5),14,NAVY,false);
    }

    private void drawFail(Canvas c) {
        drawScenicBackdrop(c);float w=getWidth(),h=getHeight(),top=insetTop+dp(18),bottom=h-insetBottom;
        label(c,"TRY AGAIN",w/2,top+dp(28),27,NAVY,true);
        label(c,"Your coins are safe. Find a fresh way out.",w/2,top+dp(56),13,MUTED,false);
        primaryHit.set(dp(24),bottom-dp(180),w-dp(24),bottom-dp(130));secondaryHit.set(dp(24),bottom-dp(118),w-dp(24),bottom-dp(68));tertiaryHit.set(dp(24),bottom-dp(60),w-dp(24),bottom-dp(12));
        float cy=(top+dp(76)+primaryHit.top)/2;paint.setColor(BLUE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));Path p=new Path();p.moveTo(w*.25f,cy+dp(28));p.lineTo(w*.55f,cy+dp(28));p.lineTo(w*.55f,cy-dp(28));p.lineTo(w*.75f,cy-dp(28));c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);drawMiniArrow(c,w*.75f,cy-dp(28),dp(13),BLUE,0);
        action(c,primaryHit,"RESTART LEVEL",BLUE,0xFF001822);action(c,secondaryHit,"▶  WATCH AD · REVIVE",BOSS_GOLD,0xFF221700);
        label(c,"Back to Route",w/2,tertiaryHit.centerY()+dp(5),14,NAVY,false);walletHit.setEmpty();
    }

    private float mapNodeX(int index) { return getWidth()*(index%2==0?.28f:.72f); }
    private float mapNodeY(int index) { return mapViewportBottom-dp(58)-index*dp(88)+mapScroll; }
    private void clampMapScroll() {
        float viewport=Math.max(dp(80),mapViewportBottom-mapViewportTop);
        mapScroll=Math.max(0,Math.min(mapScroll,dp(20*88+24)-viewport));
    }
    private void centerMapOnCurrent() {
        levelPage=(currentLevelNumber()-1)/20;
        mapScroll=Math.max(0,((currentLevelNumber()-1)%20)*dp(88)-Math.max(dp(80),getHeight()-insetTop-insetBottom-dp(190))*.45f);
        clampMapScroll();
    }
    private void polishedPill(Canvas c,RectF r,int a,int b){
        paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,a,b,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,dp(16),dp(16),paint);paint.setShader(null);
    }

    private void drawScenicBackdrop(Canvas c) {
        float w=getWidth(),h=getHeight();paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,0,w,h,0xFF071329,0xFF0A2036,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setColor(0xFF193A5C);
        for(float x=dp(20);x<w;x+=dp(22))for(float y=dp(20);y<h;y+=dp(22))c.drawCircle(x,y,dp(1),paint);
    }

    private void drawCelebration(Canvas c) {
        float w=getWidth(),h=getHeight();long now=SystemClock.elapsedRealtime();
        int[] colors={0xFFFFCF62,0xFF6AECD2,0xFFFF8EA8,0xFFAB9AFF};
        for(int i=0;i<32;i++){
            float t=((now+i*193)%4200)/4200f,x=(i*67%103)/103f*w+(float)Math.sin(t*6+i)*dp(12),y=t*h;
            paint.setColor(colors[i%4]);c.save();c.rotate(t*270+i*31,x,y);c.drawRoundRect(new RectF(x-dp(2),y-dp(4),x+dp(2),y+dp(4)),dp(1),dp(1),paint);c.restore();
        }
    }

    private Path routeSegment(float px,float py,float nx,float ny){
        float mid=(py+ny)/2,r=dp(10),dir=nx>px?1:-1;Path path=new Path();path.moveTo(px,py);
        path.lineTo(px,mid+r);path.quadTo(px,mid,px+dir*r,mid);
        path.lineTo(nx-dir*r,mid);path.quadTo(nx,mid,nx,mid-r);path.lineTo(nx,ny);
        return path;
    }
    private void drawRouteLock(Canvas c,float x,float y,int color){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));
        c.drawArc(new RectF(x-dp(3),y-dp(5),x+dp(3),y+dp(2)),180,180,false,paint);
        paint.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(x-dp(4),y-dp(1),x+dp(4),y+dp(5)),dp(1),dp(1),paint);
    }
    private void drawLevels(Canvas c) {
        drawScenicBackdrop(c);float w=getWidth(),h=getHeight(),top=insetTop+dp(10);
        mapViewportTop=top+dp(98);mapViewportBottom=h-insetBottom-dp(78);clampMapScroll();int start=levelPage*20+1;
        c.save();c.clipRect(0,mapViewportTop,w,mapViewportBottom);
        for(int i=1;i<20;i++){
            int lv=start+i;float px=mapNodeX(i-1),py=mapNodeY(i-1),nx=mapNodeX(i),ny=mapNodeY(i);
            Path route=routeSegment(px,py,nx,ny);boolean open=lv<=maxUnlocked;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(open?7:3));
            paint.setColor(open?0xFF2AD9E5:0xFF7C80B7);
            if(!open)paint.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(7)},0));c.drawPath(route,paint);paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);if(open)drawMiniArrow(c,nx,ny+dp(35),dp(9),BLUE,-90);
        }
        for(int i=0;i<20;i++){
            int lv=start+i;float x=mapNodeX(i),y=mapNodeY(i);
            if(mapNodeHits[i]==null)mapNodeHits[i]=new RectF();mapNodeHits[i].set(x-dp(30),y-dp(30),x+dp(30),y+dp(30));
            if(y+dp(46)<mapViewportTop||y-dp(46)>mapViewportBottom)continue;
            boolean open=lv<=maxUnlocked,current=lv==currentLevelNumber(),done=bestStars(lv)>0;
            int tier=CampaignRules.tier(lv),accent=tier==3?0xFFFF7984:tier==2?0xFFB091EF:tier==1?0xFFFFCE60:BLUE;
            if(current){float pulse=settings.getBoolean("reduce_motion",false)?0f:(float)(.5+.5*Math.sin(SystemClock.elapsedRealtime()/550.0));paint.setColor(0x3327D8EE);c.drawRoundRect(new RectF(x-dp(35+pulse*2),y-dp(35+pulse*2),x+dp(35+pulse*2),y+dp(35+pulse*2)),dp(20),dp(20),paint);}
            RectF token=new RectF(x-dp(27),y-dp(27),x+dp(27),y+dp(27));
            Path shape=new Path();
            if(tier>0){int sides=tier==1?4:tier==2?6:8;float offset=tier==1?-(float)Math.PI/2:0;
                for(int k=0;k<sides;k++){double angle=offset+k*Math.PI*2/sides;float ax=x+(float)Math.cos(angle)*dp(31),ay=y+(float)Math.sin(angle)*dp(31);if(k==0)shape.moveTo(ax,ay);else shape.lineTo(ax,ay);}shape.close();
            }else shape.addRoundRect(token,dp(15),dp(15),Path.Direction.CW);
            paint.setShader(new LinearGradient(x,y-dp(28),x,y+dp(28),open?(tier==0?0xFF127D8E:accent):0xFF253457,open?0xFF12384D:0xFF142037,Shader.TileMode.CLAMP));c.drawPath(shape,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(current?3:2));paint.setColor(open||tier>0?accent:0xFF5C6D99);c.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);
            label(c,String.valueOf(lv),x,y+dp(open?6:0),20,open?NAVY:MUTED,true);
            if(!open)drawRouteLock(c,x,y+dp(14),MUTED);
            if(done)label(c,starString(bestStars(lv)),x,y+dp(42),12,BOSS_GOLD,true);
            else if(current)label(c,lv==1?"START HERE":"YOU ARE HERE",x,y+dp(43),10,BLUE,true);
            if(tier>0)label(c,tier==3?"EXTREME":tier==2?"SUPER HARD":"HARD",x,y+dp(!done&&!current?43:-39),10,accent,true);
        }
        c.restore();
        RectF header=new RectF(dp(10),top,w-dp(10),top+dp(90));polishedPill(c,header,0xFF10294A,0xFF0B1932);strokeRound(c,header,0xFF23527A,dp(1));
        label(c,"ESCAPE ROUTE",dp(24),top+dp(29),20,NAVY,true,Paint.Align.LEFT);
        walletHit.set(w-dp(116),top+dp(7),w-dp(60),top+dp(45));pill(c,walletHit,0xFF30280F);drawCoin(c,walletHit.left+dp(12),walletHit.centerY(),dp(7));label(c,String.valueOf(wallet.balance()),walletHit.centerX()+dp(7),walletHit.centerY()+dp(4),12,BOSS_GOLD,true);
        drawGear(c,w-dp(32),top+dp(26));
        label(c,"CH "+chapterNumber(start)+" · "+chapterName(start),w/2,top+dp(53),12,MUTED,false);
        mapPrevHit.set(dp(14),top+dp(61),dp(64),top+dp(89));mapNextHit.set(w-dp(64),top+dp(61),w-dp(14),top+dp(89));
        label(c,"‹",mapPrevHit.centerX(),top+dp(81),22,levelPage>0?BLUE:MUTED,true);
        label(c,"›",mapNextHit.centerX(),top+dp(81),22,levelPage<19?BLUE:MUTED,true);
        label(c,"400 LEVELS · "+totalStars()+" ★",w/2,top+dp(78),11,BLUE,true);
        mapCurrentHit.set(dp(20),h-insetBottom-dp(64),w-dp(20),h-insetBottom-dp(14));
        action(c,mapCurrentHit,"PLAY LEVEL "+currentLevelNumber()+"  ›",BLUE,0xFF001822);
    }

    private void label(Canvas c,String value,float x,float y,int size,int color,boolean bold,Paint.Align align){
        paint.setTextAlign(align);paint.setTextSize(dp(size));paint.setColor(color);paint.setFakeBoldText(bold);c.drawText(value,x,y,paint);paint.setFakeBoldText(false);paint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawBack(Canvas c,float x,float y){
        RectF r=new RectF(x-dp(21),y-dp(21),x+dp(21),y+dp(21));
        paint.setColor(Color.argb(210,8,28,58));c.drawRoundRect(r,dp(15),dp(15),paint);
        strokeRound(c,r,Color.rgb(31,83,133),dp(1));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(BLUE);
        Path p=new Path();p.moveTo(x+dp(6),y-dp(10));p.lineTo(x-dp(5),y);p.lineTo(x+dp(6),y+dp(10));c.drawPath(p,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGear(Canvas c,float x,float y){
        RectF r=new RectF(x-dp(21),y-dp(21),x+dp(21),y+dp(21));
        paint.setColor(Color.argb(220,8,28,58));c.drawRoundRect(r,dp(15),dp(15),paint);
        strokeRound(c,r,Color.rgb(31,83,133),dp(1));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(2.2f));paint.setColor(BLUE);
        c.drawCircle(x,y,dp(7.5f),paint);c.drawCircle(x,y,dp(2.8f),paint);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            float x1=(float)(x+Math.cos(a)*dp(9.2f)),y1=(float)(y+Math.sin(a)*dp(9.2f));
            float x2=(float)(x+Math.cos(a)*dp(12.2f)),y2=(float)(y+Math.sin(a)*dp(12.2f));
            c.drawLine(x1,y1,x2,y2,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeart(Canvas c,float x,float y,float s,int color){Path p=new Path();p.moveTo(x,y+s*.9f);p.cubicTo(x-s*1.25f,y+s*.1f,x-s*1.15f,y-s*.75f,x-s*.55f,y-s*.85f);p.cubicTo(x-s*.1f,y-s*.95f,x,y-s*.55f,x,y-s*.35f);p.cubicTo(x,y-s*.55f,x+s*.1f,y-s*.95f,x+s*.55f,y-s*.85f);p.cubicTo(x+s*1.15f,y-s*.75f,x+s*1.25f,y+s*.1f,x,y+s*.9f);p.close();paint.setColor(color);c.drawPath(p,paint);}

    private void pill(Canvas c,RectF r,int color){paint.setColor(color);c.drawRoundRect(r,dp(16),dp(16),paint);}
    private void strokeRound(Canvas c,RectF r,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawRoundRect(r,dp(16),dp(16),paint);paint.setStyle(Paint.Style.FILL);}

    private void drawRoundControl(Canvas c,float x,float y,float r){paint.setColor(Color.rgb(20,51,90));c.drawCircle(x,y,r+dp(3),paint);paint.setColor(Color.rgb(10,29,61));c.drawCircle(x,y,r,paint);}

    private void drawBulb(Canvas c,float x,float y,float s){paint.setColor(Color.rgb(255,193,30));c.drawCircle(x,y-s*.25f,s*.62f,paint);paint.setColor(Color.rgb(255,218,72));c.drawCircle(x-s*.2f,y-s*.45f,s*.2f,paint);paint.setColor(Color.rgb(139,157,191));RectF base=new RectF(x-s*.35f,y+s*.30f,x+s*.35f,y+s*.82f);c.drawRoundRect(base,s*.13f,s*.13f,paint);paint.setColor(Color.rgb(95,113,151));c.drawRect(x-s*.28f,y+s*.62f,x+s*.28f,y+s*.78f,paint);}

    private void drawEraser(Canvas c,float x,float y,float s){c.save();c.rotate(-28,x,y);RectF body=new RectF(x-s*.55f,y-s*.82f,x+s*.55f,y+s*.82f);paint.setColor(Color.rgb(107,82,224));c.drawRoundRect(body,s*.18f,s*.18f,paint);RectF stripe=new RectF(body.left+s*.12f,body.top+s*.23f,body.right-s*.12f,body.top+s*.42f);paint.setColor(Color.rgb(159,140,244));c.drawRoundRect(stripe,s*.08f,s*.08f,paint);paint.setColor(Color.rgb(229,232,245));c.drawRoundRect(new RectF(body.left,body.bottom-s*.23f,body.right,body.bottom),s*.12f,s*.12f,paint);c.restore();}

    private void drawMiniArrow(Canvas c,float x,float y,float s,int color,float degrees){c.save();c.rotate(degrees,x,y);paint.setColor(color);Path a=new Path();a.moveTo(x+s*.7f,y);a.lineTo(x-s*.35f,y-s*.45f);a.lineTo(x-s*.18f,y);a.lineTo(x-s*.35f,y+s*.45f);a.close();c.drawPath(a,paint);c.restore();}

    private void drawToast(Canvas c,String msg){
        float y=screen==Screen.PLAY?insetTop+dp(125):mapViewportTop;
        paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);
        while(paint.measureText(msg)>getWidth()-dp(52)&&paint.getTextSize()>dp(9))paint.setTextSize(paint.getTextSize()-dp(.5f));
        float tw=paint.measureText(msg);RectF r=new RectF((getWidth()-tw)/2-dp(16),y,(getWidth()+tw)/2+dp(16),y+dp(32));
        paint.setColor(0xEE14273F);c.drawRoundRect(r,dp(16),dp(16),paint);paint.setColor(NAVY);c.drawText(msg,r.centerX(),r.centerY()+dp(4),paint);
    }

    private void clampBoardPan() {
        if (boardZoom <= MIN_BOARD_ZOOM + .01f) {
            boardPanX=0f;boardPanY=0f;return;
        }
        float viewportW=Math.max(1f,boardViewportRight-boardViewportLeft);
        float viewportH=Math.max(1f,boardViewportBottom-boardViewportTop);
        float maxX=Math.max(0f,(boardW-viewportW)/2f+dp(20));
        float maxY=Math.max(0f,(boardH-viewportH)/2f+dp(20));
        boardPanX=Math.max(-maxX,Math.min(maxX,boardPanX));
        boardPanY=Math.max(-maxY,Math.min(maxY,boardPanY));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(paused)return true;
        if(screen==Screen.LEVELS)return handleMapGesture(e);

        zoomDetector.onTouchEvent(e);
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_POINTER_DOWN || e.getPointerCount()>1 || zoomDetector.isInProgress()){
            zoomGestureActive=true;
            pressPiece=null;previewPiece=null;longPressPreview=false;
            invalidate();
            return true;
        }
        // Once a pinch starts, consume the rest of that gesture so lifting the
        // final finger can never be mistaken for an arrow tap.
        if(zoomGestureActive){
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)zoomGestureActive=false;
            return true;
        }

        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            pressPiece=(screen==Screen.PLAY&&boosterMode==0&&!tutorial&&!finished&&!failed)?findPieceAt(x,y):null;
            longPressPreview=false;
            if(pressPiece!=null){
                final Piece candidate=pressPiece;
                postDelayed(()->{
                    if(pressPiece==candidate&&!candidate.removed&&!candidate.moving&&!paused&&screen==Screen.PLAY&&!tutorial&&!finished&&!failed){
                        previewPiece=candidate;longPressPreview=true;
                        showToast("Path preview · release to keep playing");
                        invalidate();
                    }
                },420L);
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_CANCEL){
            pressPiece=null;longPressPreview=false;return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();
        rippleX=x;rippleY=y;touchRippleUntil=SystemClock.elapsedRealtime()+360;
        Piece releasedPress=pressPiece;pressPiece=null;
        if(longPressPreview){longPressPreview=false;previewPiece=releasedPress;invalidate();return true;}
        previewPiece=null;invalidate();
        if(screen==Screen.LEVELS)return handleLevelsTouch(x,y);
        if(milestoneIntroUntil>SystemClock.elapsedRealtime()){milestoneIntroUntil=0L;invalidate();return true;}
        if(tutorial){if(primaryHit.contains(x,y)){tutorial=false;prefs.edit().putBoolean("tutorialSeen",true).apply();invalidate();}return true;}
        if(finished){
            if(primaryHit.contains(x,y)){
                if(challengeActive())leaveChallenge();
                else host.onContinueAfterWin(()->{if(level<MAX_LEVEL)startLevel(level+1);else showLevelSelect();});
            } else if(secondaryHit.contains(x,y))host.requestRewardedCoins();
            else if(tertiaryHit.contains(x,y)){if(challengeActive())restartCurrentLevel();else showLevelSelect();}
            return true;
        }
        if(failed){
            if(primaryHit.contains(x,y))restartCurrentLevel();
            else if(secondaryHit.contains(x,y))host.requestRewardedRevive();
            else if(tertiaryHit.contains(x,y)){if(challengeActive())leaveChallenge();else showLevelSelect();}
            return true;
        }
        if(boosterMode!=0){
            if(hintHit.contains(x,y)){cancelBooster();return true;}
            if(eraseHit.contains(x,y)){confirmBooster();return true;}
            if(x>=boardViewportLeft&&x<=boardViewportRight&&y>=boardViewportTop&&y<=boardViewportBottom){selectBoosterTarget(x,y);return true;}
            cancelBooster();return true;
        }
        float top=insetTop+dp(8);
        if(x<dp(65)&&y<top+dp(60)){if(challengeActive())leaveChallenge();else host.openHome();return true;}
        if(x>getWidth()-dp(65)&&y<top+dp(60)){host.openSettings();return true;}
        if(walletHit.contains(x,y)){host.openWallet();return true;}
        if(rewardHit.contains(x,y)){beginBooster(3);return true;}
        if(hintHit.contains(x,y)){useHint();return true;}
        if(eraseHit.contains(x,y)){host.openBoosters();return true;}
        // A tap belongs to the arrow selected on ACTION_DOWN. Re-running hit testing
        // on ACTION_UP can switch to a neighbouring dense-grid arrow after normal finger
        // drift, causing a false blocked result and an incorrect heart penalty.
        Piece piece=(releasedPress!=null&&!releasedPress.removed&&!releasedPress.moving)
                ? releasedPress : findPieceAt(x,y);
        if(piece!=null)tapPiece(piece);return true;
    }

    private void startLevel(int lv) {
        host.onGameplayStarted();
        dailyChallenge=false;weeklyChallenge=false;challengeDay=-1L;challengeWeek=-1L;
        setupLevel(lv,true);
    }

    private void setupLevel(int lv,boolean persistNormal) {
        level=Math.max(1,Math.min(MAX_LEVEL,lv));
        if(persistNormal){prefs.edit().putInt("lastLevel",level).apply();normalLevelBeforeChallenge=level;}
        cancelBooster();blastUntil=0;screen=Screen.PLAY;settingsOpen=false;finished=false;failed=false;hearts=3;hints=2;erasers=1;
        mistakes=0;assistsUsed=0;earnedStars=0;winReward=0;combo=0;bestCombo=0;lastCorrectTapAt=0L;flowShield=false;comboFlashUntil=0L;runId=java.util.UUID.randomUUID().toString();lastFrame=0;
        if(dailyChallenge) generateDailyChallenge(challengeDay);
        else generateLevel(level);
        configureSpecialMechanics();
        boolean mechanicIntro=!challengeActive()&&(level==41||level==81||level==121||level==161);
        milestoneIntroUntil=(challengeActive()||isSuperHard(level)||mechanicIntro)?SystemClock.elapsedRealtime()+(mechanicIntro?5000L:1600L):0L;
        saveProgress();invalidate();
    }

    public void startDailyChallenge() {
        if(packedDailyChallenges==null||packedDailyChallenges.length==0){showToast("Daily Challenge unavailable");return;}
        normalLevelBeforeChallenge=Math.max(1,Math.min(MAX_LEVEL,prefs.getInt("lastLevel",level)));
        challengeDay=System.currentTimeMillis()/Wallet.DAY_MS;
        weeklyChallenge=false;challengeWeek=-1L;dailyChallenge=true;
        // Daily Challenge is its own puzzle pack. MAX_LEVEL is only a difficulty
        // profile marker for special mechanics; no campaign level is loaded.
        setupLevel(MAX_LEVEL,false);
    }

    public void startWeeklyChallenge() {
        normalLevelBeforeChallenge=Math.max(1,Math.min(MAX_LEVEL,prefs.getInt("lastLevel",level)));
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        challengeWeek=day/7L;
        dailyChallenge=false;challengeDay=-1L;weeklyChallenge=true;
        setupLevel(weeklyLevelForWeek(challengeWeek),false);
    }

    private boolean challengeActive(){return dailyChallenge||weeklyChallenge;}
    private int dailyChallengeIndex(long day){
        int count=packedDailyChallenges==null?0:packedDailyChallenges.length;
        if(count<=0)return 0;
        return (int)Math.floorMod(day*131L+47L,count);
    }
    private int dailyChallengeNumberForDay(long day){return dailyChallengeIndex(day)+1;}
    private int weeklyLevelForWeek(long week){return 120+(int)Math.floorMod(week*97L+11L,24L)*12;}
    public int bestDailyStarsToday(){
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        return prefs.getLong("challenge_star_day",-1L)==day?prefs.getInt("challenge_best_stars",0):0;
    }
    public int bestWeeklyStarsThisWeek(){
        long week=(System.currentTimeMillis()/Wallet.DAY_MS)/7L;
        return prefs.getLong("weekly_challenge_star_week",-1L)==week?prefs.getInt("weekly_challenge_best_stars",0):0;
    }
    private void exitChallengeToCampaign(){
        int back=Math.max(1,Math.min(MAX_LEVEL,normalLevelBeforeChallenge));
        dailyChallenge=false;weeklyChallenge=false;challengeDay=-1L;challengeWeek=-1L;
        startLevel(back);
    }
    private void leaveChallenge(){exitChallengeToCampaign();host.openHome();}

    private boolean handleMapGesture(MotionEvent e) {
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mapDownY=mapLastY=y;mapDragging=false;return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(y-mapDownY)>dp(8))mapDragging=true;
                if(mapDragging&&mapDownY>=mapViewportTop&&mapDownY<=mapViewportBottom){
                    mapScroll+=y-mapLastY;clampMapScroll();invalidate();
                }
                mapLastY=y;return true;
            case MotionEvent.ACTION_UP:
                if(!mapDragging){performClick();handleLevelsTouch(x,y);}mapDragging=false;return true;
            case MotionEvent.ACTION_CANCEL:mapDragging=false;return true;
            default:return true;
        }
    }
    private boolean handleLevelsTouch(float x,float y) {
        float top=insetTop+dp(12);
        if(walletHit.contains(x,y)){host.openWallet();return true;}
        if(x>getWidth()-dp(65)&&y<top+dp(55)){host.openSettings();return true;}
        if(mapCurrentHit.contains(x,y)){if(finished||failed)startLevel(currentLevelNumber());else openPlay();return true;}
        if(mapPrevHit.contains(x,y)&&levelPage>0){levelPage--;mapScroll=0;invalidate();return true;}
        if(mapNextHit.contains(x,y)&&levelPage<(MAX_LEVEL-1)/20){levelPage++;mapScroll=0;invalidate();return true;}
        if(y>=mapViewportTop&&y<=mapViewportBottom)for(int i=0;i<20;i++){
            if(mapNodeHits[i]!=null&&mapNodeHits[i].contains(x,y)){
                int lv=levelPage*20+i+1;
                if(lv<=maxUnlocked&&lv<=MAX_LEVEL)startLevel(lv);
                else showToast("Clear earlier levels to unlock this puzzle");
                return true;
            }
        }
        return true;
    }

    private Piece findPieceAt(float x,float y){Piece best=null;float bestD=Math.max(dp(16),cell*.42f);for(Piece p:pieces){if(p.removed||p.moving)continue;for(int i=0;i<p.pts.size()-1;i++){Point a=p.pts.get(i),b=p.pts.get(i+1);float ax=boardLeft+a.x*cell,ay=boardTop+a.y*cell,bx=boardLeft+b.x*cell,by=boardTop+b.y*cell;float d=pointSegDist(x,y,ax,ay,bx,by);if(d<bestD){bestD=d;best=p;}}}return best;}



    private float pointSegDist(float px,float py,float ax,float ay,float bx,float by){float vx=bx-ax,vy=by-ay,wx=px-ax,wy=py-ay;float c1=vx*wx+vy*wy;if(c1<=0)return dist(px,py,ax,ay);float c2=vx*vx+vy*vy;if(c2<=c1)return dist(px,py,bx,by);float t=c1/c2;return dist(px,py,ax+t*vx,ay+t*vy);}
    private float dist(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return (float)Math.sqrt(dx*dx+dy*dy);}

    private void tapPiece(Piece p) {
        if(p.removed||p.moving||failed||finished)return;
        String specialBlock = specialBlockedMessage(p);
        boolean selfBlocked = !hasSelfClearance(p);
        if(isClear(p)) {
            long now=SystemClock.elapsedRealtime();
            if(settings.getBoolean("haptics",true))performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            p.moving=true;p.moveT=0;p.moveSteps=snakeTravelSteps(p);
            combo=(lastCorrectTapAt>0L && now-lastCorrectTapAt<=2400L)?combo+1:1;
            lastCorrectTapAt=now;
            bestCombo=Math.max(bestCombo,combo);comboFlashUntil=now+1400L;
            if(combo==3)showToast("FLOW x3 · keep moving");
            else if(combo==5){
                flowShield=true;
                showToast("FLOW SHIELD READY · next mistake is protected");
                buzz(42);
            } else if(combo==8)showToast("RUSH x8 · arrows escaping faster");
            else if(combo>0&&combo%10==0)showToast("MASTER FLOW · x"+combo);
            playSound(ToneGenerator.TONE_PROP_BEEP,Math.min(130,65+combo*5));buzz(combo>=5?28:20);
        } else {
            boolean protectedTap=flowShield;
            flowShield=false;combo=0;lastCorrectTapAt=0L;
            p.flashUntil=SystemClock.elapsedRealtime()+390;
            if(!protectedTap)hearts--;
            mistakes++;
            playSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,110);buzz(protectedTap?32:55);
            if(protectedTap)showToast("FLOW SHIELD SAVED A HEART");
            else showToast(specialBlock != null ? specialBlock : (selfBlocked ? "Tail blocks this escape" : "Blocked by another arrow"));
            if(hearts<=0)failed=true;
        }
        saveProgress();invalidate();
    }

    private void useHint() {
        long now=SystemClock.elapsedRealtime();ArrayList<Piece> safe=new ArrayList<>();
        for(Piece p:pieces){if(!p.removed&&!p.moving&&p.hintUntil>now)return;if(!p.removed&&!p.moving&&isClear(p))safe.add(p);}
        if(safe.isEmpty()){showToast("Wait for the moving arrows");return;}
        if(hints>0)hints--;else if(!wallet.spend(Wallet.HINT_COST)){host.openWallet();return;}
        assistsUsed++;combo=0;
        Piece p=safe.get(rng.nextInt(safe.size()));p.hintUntil=now+2400;
        saveProgress();showToast("Safe arrow highlighted");invalidate();
    }

    private void useEraser(){if(erasers<=0){host.requestRewardedErase();return;}ArrayList<Piece> active=new ArrayList<>();for(Piece p:pieces)if(!p.removed&&!p.moving)active.add(p);if(active.isEmpty())return;Piece p=active.get(rng.nextInt(active.size()));erasers--;assistsUsed++;combo=0;p.moving=true;p.moveT=0;p.moveSteps=snakeTravelSteps(p);showToast("One arrow removed");saveProgress();invalidate();}

    public void beginBooster(int mode) {
        if(mode<1||mode>3||failed||finished||tutorial||screen!=Screen.PLAY)return;
        if(mode==1&&erasers<=0){host.requestRewardedErase();return;}
        boosterMode=mode;boosterTargeted=false;boosterTargets.clear();previewPiece=null;pressPiece=null;
        showToast(mode==1?"Choose one arrow":mode==2?"Area Bomb · radius 4 cells":"Row Sweep · choose a row");invalidate();
    }
    public void cancelBooster(){boosterMode=0;boosterTargeted=false;boosterTargets.clear();invalidate();}
    private void selectBoosterTarget(float screenX,float screenY) {
        if(cell<=0)return;
        boosterX=Math.max(0,Math.min(gridW,(screenX-boardLeft)/cell));
        boosterY=Math.max(0,Math.min(gridH,(screenY-boardTop)/cell));
        boosterTargeted=true;boosterTargets.clear();
        if(boosterMode==1){Piece p=findPieceAt(screenX,screenY);if(p!=null)boosterTargets.add(p);}
        else for(Piece p:pieces)if(!p.removed&&!p.moving){
            for(int i=1;i<p.pts.size();i++){
                Point a=p.pts.get(i-1),b=p.pts.get(i);
                boolean hit=boosterMode==2?BoosterGeometry.circleTouchesSegment(boosterX,boosterY,4,a.x,a.y,b.x,b.y)
                    :BoosterGeometry.rowTouchesSegment(boosterY,.5f,a.y,b.y);
                if(hit){boosterTargets.add(p);break;}
            }
        }
        invalidate();
    }
    private void confirmBooster() {
        boosterTargets.removeIf(p->p.removed||p.moving);
        if(!boosterTargeted||boosterTargets.isEmpty()){showToast("Choose an area with arrows first");return;}
        int cost=boosterMode==2?BOMB_COST:boosterMode==3?ROW_COST:0;
        if(boosterMode==1&&erasers<=0){showToast("No eraser available");return;}
        if(cost>0&&!wallet.spend(cost)){showToast("Not enough coins · nothing used");return;}
        if(boosterMode==1)erasers--;
        int count=boosterTargets.size();
        for(Piece p:boosterTargets){p.removed=true;p.moving=false;p.hintUntil=0;}
        assistsUsed++;combo=0;flowShield=false;lastCorrectTapAt=0;
        blastX=boosterX;blastY=boosterY;blastUntil=SystemClock.elapsedRealtime()+500;
        cancelBooster();playSound(ToneGenerator.TONE_PROP_ACK,120);buzz(50);
        saveProgress();showToast(count+" arrows removed");invalidate();
    }
    private void drawBoosterPreview(Canvas c,long now) {
        if(boosterMode!=0&&boosterTargeted){
            paint.setColor(Color.argb(70,255,193,30));
            float x=boardLeft+boosterX*cell,y=boardTop+boosterY*cell;
            if(boosterMode==2)c.drawCircle(x,y,cell*4,paint);
            else if(boosterMode==3)c.drawRect(boardLeft,y-cell*.5f,boardLeft+boardW,y+cell*.5f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));paint.setColor(BOSS_GOLD);
            for(Piece p:boosterTargets)for(int i=1;i<p.pts.size();i++){
                Point a=p.pts.get(i-1),b=p.pts.get(i);
                c.drawLine(boardLeft+a.x*cell,boardTop+a.y*cell,boardLeft+b.x*cell,boardTop+b.y*cell,paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
        if(blastUntil>now){
            float t=1-(blastUntil-now)/500f;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(Color.argb((int)(210*(1-t)),255,193,30));
            c.drawCircle(boardLeft+blastX*cell,boardTop+blastY*cell,cell*(1+t*5),paint);
            paint.setStyle(Paint.Style.FILL);postInvalidateOnAnimation();
        }
    }

    public void grantEraser(){erasers++;saveProgress();showToast("Eraser added");invalidate();}
    public void grantRevive(){if(!failed)return;failed=false;hearts=2;assistsUsed++;saveProgress();showToast("Revived with 2 hearts");invalidate();}

    private int remaining(){int n=0;for(Piece p:pieces)if(!p.removed)n++;return n;}
    private int chapterNumber(int lv){if(lv<=20)return 1;if(lv<=40)return 2;if(lv<=60)return 3;if(lv<=80)return 4;if(lv<=100)return 5;if(lv<=140)return 6;if(lv<=180)return 7;if(lv<=200)return 8;return 9+(lv-201)/40;}
    private String chapterName(int lv){switch(chapterNumber(lv)){case 1:return "First Escape";case 2:return "Twisted Paths";case 3:return "Tail Trouble";case 4:return "Locked Logic";case 5:return "Chain Reaction";case 6:return "Master Escape";case 7:return "Impossible Maze";case 8:return "Grandmaster";case 9:return "Neon Heights";case 10:return "Crystal Vault";case 11:return "Ember Citadel";case 12:return "Storm Summit";default:return "Final Escape";}}
    private int selectedArrowColor(){int s=Math.floorMod(settings.getInt("arrow_style",0),4);if(s==1)return Color.rgb(51,190,255);if(s==2)return Color.rgb(178,105,255);if(s==3)return Color.rgb(255,204,57);return Color.rgb(80,224,255);}
    private int currentThemeIndex(){int t=Math.floorMod(settings.getInt("board_theme",0),6);return wallet.ownsTheme(t)?t:0;}
    private int boardBackground(){switch(currentThemeIndex()){case 1:return Color.rgb(5,25,47);case 2:return Color.rgb(34,13,31);case 3:return Color.rgb(5,31,29);case 4:return Color.rgb(5,7,24);case 5:return Color.rgb(38,12,10);default:return Color.rgb(5,11,30);}}
    private int themeDotColor(){switch(currentThemeIndex()){case 1:return Color.rgb(35,79,115);case 2:return Color.rgb(100,57,82);case 3:return Color.rgb(35,83,75);case 4:return Color.rgb(47,50,94);case 5:return Color.rgb(104,45,39);default:return DOT;}}
    private int themeAccent(){switch(currentThemeIndex()){case 1:return Color.rgb(70,201,255);case 2:return Color.rgb(255,126,174);case 3:return Color.rgb(61,224,177);case 4:return Color.rgb(157,105,255);case 5:return Color.rgb(255,94,61);default:return BLUE;}}
    private String arrowStyleName(int s){switch(Math.floorMod(s,4)){case 1:return "Ocean";case 2:return "Galaxy";case 3:return "Gold";default:return "Classic";}}
    private String boardThemeName(int t){switch(Math.floorMod(t,6)){case 1:return "Ice";case 2:return "Sunset";case 3:return "Mint";case 4:return "Midnight";case 5:return "Lava";default:return "Clean";}}
    public int boardThemePrice(int t){switch(Math.floorMod(t,6)){case 1:return 250;case 2:return 300;case 3:return 350;case 4:return 500;case 5:return 650;default:return 0;}}
    public String boardThemeFeature(int t){switch(Math.floorMod(t,6)){case 1:return "Cool frost atmosphere";case 2:return "Warm sunset rings";case 3:return "Soft mint particles";case 4:return "Dark star field";case 5:return "Ember sparks";default:return "Minimal clean board";}}
    public String[] boardThemeNames(){return new String[]{"Clean","Ice","Sunset","Mint","Midnight","Lava"};}
    public String[] boardThemeOptionLabels(){String[] out=new String[6];for(int i=0;i<6;i++)out[i]=boardThemeName(i)+" · "+(wallet.ownsTheme(i)?"OWNED":boardThemePrice(i)+" coins")+" · "+boardThemeFeature(i);return out;}
    public boolean isBoardThemeOwned(int t){return wallet.ownsTheme(Math.floorMod(t,6));}
    public int currentBoardThemeIndex(){return currentThemeIndex();}
    public boolean unlockAndSelectBoardTheme(int t){t=Math.floorMod(t,6);if(!wallet.ownsTheme(t)&&!wallet.purchaseTheme(t,boardThemePrice(t)))return false;settings.edit().putInt("board_theme",t).apply();invalidate();return true;}
    public String cycleArrowStyle(){int next=Math.floorMod(settings.getInt("arrow_style",0)+1,4);settings.edit().putInt("arrow_style",next).apply();invalidate();return arrowStyleName(next);}
    public String currentArrowStyle(){return arrowStyleName(settings.getInt("arrow_style",0));}
    public String currentBoardTheme(){return boardThemeName(currentThemeIndex());}
    private String arrowTypeName(int type){
        switch(Math.floorMod(type,5)){case 1:return "Slim";case 2:return "Bold";case 3:return "Chevron";case 4:return "Neon";default:return "Classic";}
    }
    public String arrowTypeFeature(int type){
        switch(Math.floorMod(type,5)){case 1:return "Comet trail";case 2:return "Impact pulse";case 3:return "Echo chevrons";case 4:return "Glow + sparks";default:return "Clean classic";}
    }
    public int arrowTypePrice(int type){
        switch(Math.floorMod(type,5)){case 1:return 200;case 2:return 350;case 3:return 500;case 4:return 800;default:return 0;}
    }
    public String[] arrowTypeNames(){return new String[]{"Classic","Slim","Bold","Chevron","Neon"};}
    public String[] arrowTypeOptionLabels(){
        String[] labels=new String[5];
        for(int i=0;i<5;i++){
            boolean owned=wallet.ownsArrowType(i);
            labels[i]=arrowTypeName(i)+" · "+(owned?"OWNED":arrowTypePrice(i)+" coins")+" · "+arrowTypeFeature(i);
        }
        return labels;
    }
    public boolean isArrowTypeOwned(int type){return wallet.ownsArrowType(Math.floorMod(type,5));}
    public int currentArrowTypeIndex(){return arrowType();}
    public String currentArrowType(){return arrowTypeName(arrowType());}
    public boolean unlockAndSelectArrowType(int type){
        type=Math.floorMod(type,5);
        int price=arrowTypePrice(type);
        if(!wallet.ownsArrowType(type) && !wallet.purchaseArrowType(type,price))return false;
        settings.edit().putInt("arrow_type",type).apply();
        invalidate();
        return true;
    }
    private boolean isSuperHard(int lv){return CampaignRules.tier(lv)>=2;}
    private boolean isBoss(int lv){return CampaignRules.tier(lv)==3;}
    private String phaseName(int lv){return CampaignRules.label(lv);}
    private String difficulty(){return phaseName(level);}
    private int computeStars(){if(mistakes==0&&assistsUsed==0)return 3;if(mistakes<=1&&assistsUsed<=1)return 2;return 1;}
    private int bestStars(int lv){return Math.max(0,Math.min(3,prefs.getInt("stars_"+lv,0)));}
    private void saveBestStars(int lv,int stars){if(stars>bestStars(lv))prefs.edit().putInt("stars_"+lv,stars).apply();}
    private int totalStars(){int total=0;for(int lv=1;lv<=MAX_LEVEL;lv++)total+=bestStars(lv);return total;}
    private String starString(int stars){return (stars>=1?"★":"☆")+(stars>=2?"★":"☆")+(stars>=3?"★":"☆");}
    private void saveDailyChallengeStars(long day,int stars){
        long savedDay=prefs.getLong("challenge_star_day",-1L);int best=savedDay==day?prefs.getInt("challenge_best_stars",0):0;
        if(savedDay!=day||stars>best)prefs.edit().putLong("challenge_star_day",day).putInt("challenge_best_stars",Math.max(stars,best)).apply();
    }
    private void saveWeeklyChallengeStars(long week,int stars){
        long savedWeek=prefs.getLong("weekly_challenge_star_week",-1L);int best=savedWeek==week?prefs.getInt("weekly_challenge_best_stars",0):0;
        if(savedWeek!=week||stars>best)prefs.edit().putLong("weekly_challenge_star_week",week).putInt("weekly_challenge_best_stars",Math.max(stars,best)).apply();
    }

    private int chapterStart(int chapter){if(chapter>=9)return 201+(chapter-9)*40;switch(chapter){case 1:return 1;case 2:return 21;case 3:return 41;case 4:return 61;case 5:return 81;case 6:return 101;case 7:return 141;default:return 181;}}
    private int chapterEnd(int chapter){if(chapter>=9)return Math.min(MAX_LEVEL,240+(chapter-9)*40);switch(chapter){case 1:return 20;case 2:return 40;case 3:return 60;case 4:return 80;case 5:return 100;case 6:return 140;case 7:return 180;default:return 200;}}
    private int chapterStars(int chapter){int total=0;for(int lv=chapterStart(chapter);lv<=chapterEnd(chapter);lv++)total+=bestStars(lv);return total;}
    private int chapterCompleted(int chapter){int n=0;for(int lv=chapterStart(chapter);lv<=chapterEnd(chapter);lv++)if(bestStars(lv)>0)n++;return n;}
    private String chapterTrophyTier(int chapter){
        int count=chapterEnd(chapter)-chapterStart(chapter)+1,stars=chapterStars(chapter),done=chapterCompleted(chapter);
        if(stars==count*3)return "GOLD TROPHY";
        if(done==count&&stars>=count*2)return "SILVER TROPHY";
        if(done==count)return "BRONZE TROPHY";
        return "Trophy "+stars+"/"+(count*3)+"★";
    }
    private boolean allBossesCleared(){
        if(prefs.getBoolean("legacy_boss_hunter",false))return true;
        for(int lv=30;lv<=MAX_LEVEL;lv+=30)if(bestStars(lv)==0)return false;
        return true;
    }
    private int completedLevels(){int n=0;for(int lv=1;lv<=MAX_LEVEL;lv++)if(bestStars(lv)>0)n++;return n;}
    private int perfectLevels(){int n=0;for(int lv=1;lv<=MAX_LEVEL;lv++)if(bestStars(lv)==3)n++;return n;}
    public int achievementCount(){
        int complete=completedLevels(),perfect=perfectLevels(),stars=totalStars(),n=0;
        if(complete>=1)n++;if(perfect>=1)n++;if(complete>=25)n++;if(complete>=60)n++;if(complete>=100)n++;
        if(complete>=140)n++;if(complete>=180)n++;if(complete>=200)n++;if(stars>=300)n++;if(perfect>=50)n++;if(allBossesCleared())n++;
        return n;
    }
    public String[][] chapterAwardCards(){
        String[][] cards=new String[13][4];
        for(int ch=1;ch<=13;ch++){int first=chapterStart(ch),count=chapterEnd(ch)-first+1,done=chapterCompleted(ch);
            cards[ch-1]=new String[]{"CH "+ch+" · "+chapterName(first),"Levels "+first+"–"+chapterEnd(ch),done+" / "+count+" cleared · "+chapterStars(ch)+" stars",String.valueOf(done==count)};
        }
        return cards;
    }
    public int trophyCount(){int n=0;for(int ch=1;ch<=13;ch++)if(chapterCompleted(ch)==chapterEnd(ch)-chapterStart(ch)+1)n++;return n;}
    public String progressSummary(){return totalStars()+"/1200★  ·  "+trophyCount()+"/13 chapter trophies  ·  "+achievementCount()+"/11 achievements";}
    public String progressDetails(){
        StringBuilder b=new StringBuilder("CHAPTER TROPHIES\n");
        for(int ch=1;ch<=13;ch++){int first=chapterStart(ch);b.append("CH ").append(ch).append(" · ").append(chapterName(first)).append(" — ").append(chapterTrophyTier(ch)).append("\n");}
        int complete=completedLevels(),perfect=perfectLevels(),stars=totalStars();
        b.append("\nACHIEVEMENTS\n");
        addAchievement(b,complete>=1,"First Escape","Clear your first maze");
        addAchievement(b,perfect>=1,"Perfect Start","Earn your first 3★");
        addAchievement(b,complete>=25,"Maze Explorer","Clear 25 levels");
        addAchievement(b,complete>=60,"Key Master","Clear 60 levels");
        addAchievement(b,complete>=100,"Ice Breaker","Clear 100 levels");
        addAchievement(b,complete>=140,"Gate Runner","Clear 140 levels");
        addAchievement(b,complete>=180,"Chain Master","Clear 180 levels");
        addAchievement(b,complete>=200,"Grandmaster","Clear the first 200 levels");
        addAchievement(b,stars>=300,"Star Collector","Earn 300 stars");
        addAchievement(b,perfect>=50,"Perfectionist","Get 3★ on 50 levels");
        addAchievement(b,allBossesCleared(),"Boss Hunter","Clear all 13 Extreme Hard checkpoints");
        return b.toString();
    }
    private void addAchievement(StringBuilder b,boolean unlocked,String name,String goal){b.append(unlocked?"✓ ":"○ ").append(name).append(" — ").append(goal).append("\n");}

    private void loadPackedLevels(){
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getContext().getAssets().open("levels.txt")));
            ArrayList<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) if (!line.trim().isEmpty()) lines.add(line.trim());
            br.close();
            packedLevels = lines.toArray(new String[0]);
        } catch (Exception ignored) {
            packedLevels = null;
        }
    }

    private void loadPackedDailyChallenges(){
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getContext().getAssets().open("daily_challenges.txt")));
            ArrayList<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) if (!line.trim().isEmpty()) lines.add(line.trim());
            br.close();
            packedDailyChallenges = lines.toArray(new String[0]);
        } catch (Exception ignored) {
            packedDailyChallenges = null;
        }
    }

    private void generateDailyChallenge(long day){
        if(packedDailyChallenges==null||packedDailyChallenges.length==0){
            pieces.clear();gridW=24;gridH=30;return;
        }
        String[] normalPack=packedLevels;
        try {
            packedLevels=packedDailyChallenges;
            generateLevel(dailyChallengeIndex(day)+1);
            polishDailyChallengeLayout(day);
        } finally {
            packedLevels=normalPack;
        }
    }

    /**
     * Daily puzzles are deliberately difficult, but the original 62–70 arrow pack
     * could spread a dependency chain across a very large board. On phones this
     * made arrows tiny and the board look like disconnected random islands.
     *
     * Keep a long contiguous dependency window, choose the visually densest window,
     * then crop away unused space. The underlying blocker order is preserved, so the
     * challenge stays strategic while every arrow is substantially easier to read.
     */
    private void polishDailyChallengeLayout(long day){
        if(pieces.size()<36)return;

        HashMap<Integer,ArrayList<Piece>> blockersById=new HashMap<>();
        for(Piece p:pieces)blockersById.put(p.id,physicalBlockers(p));

        HashSet<Integer> pending=new HashSet<>();
        for(Piece p:pieces)pending.add(p.id);
        HashMap<Integer,Integer> depth=new HashMap<>();
        HashMap<Integer,Integer> parent=new HashMap<>();

        while(!pending.isEmpty()){
            ArrayList<Integer> ready=new ArrayList<>();
            for(int id:pending){
                boolean blocked=false;
                ArrayList<Piece> blockers=blockersById.get(id);
                if(blockers!=null)for(Piece blocker:blockers){
                    if(pending.contains(blocker.id)){blocked=true;break;}
                }
                if(!blocked)ready.add(id);
            }
            if(ready.isEmpty())return; // Defensive: never reshape a cyclic/corrupt pack.

            for(int id:ready){
                int bestDepth=0,bestParent=-1;
                ArrayList<Piece> blockers=blockersById.get(id);
                if(blockers!=null)for(Piece blocker:blockers){
                    int d=depth.containsKey(blocker.id)?depth.get(blocker.id):0;
                    if(d>bestDepth){bestDepth=d;bestParent=blocker.id;}
                }
                depth.put(id,bestDepth+1);
                parent.put(id,bestParent);
            }
            pending.removeAll(ready);
        }

        int deepest=-1,maxDepth=0;
        for(Piece p:pieces){
            int d=depth.containsKey(p.id)?depth.get(p.id):0;
            if(d>maxDepth){maxDepth=d;deepest=p.id;}
        }
        if(deepest<0)return;

        ArrayList<Integer> chain=new ArrayList<>();
        int cursor=deepest;
        while(cursor>=0){
            chain.add(cursor);
            cursor=parent.containsKey(cursor)?parent.get(cursor):-1;
        }
        Collections.reverse(chain);

        int target=46+Math.floorMod(dailyChallengeIndex(day),4)*2; // 46/48/50/52 readable arrows.
        int keepCount=Math.min(target,chain.size());
        if(keepCount<36)return;

        // Pick the compact contiguous section of the dependency chain. A phone board
        // is taller than it is wide, so gently favour a ~0.78 width/height ratio.
        int bestStart=0;
        double bestScore=Double.MAX_VALUE;
        for(int start=0;start+keepCount<=chain.size();start++){
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
            int occupied=0;
            for(int i=start;i<start+keepCount;i++){
                Piece p=pieceById(chain.get(i));
                if(p==null)continue;
                occupied+=p.nodes.size();
                for(long n:p.nodes){
                    int x=nodeX(n),y=nodeY(n);
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
            }
            if(minX==Integer.MAX_VALUE)continue;
            int bw=maxX-minX+3,bh=maxY-minY+3;
            double density=occupied/(double)Math.max(1,bw*bh);
            double aspect=bw/(double)Math.max(1,bh);
            double aspectPenalty=Math.abs(Math.log(Math.max(0.05,aspect/0.78)));
            double score=(bw*bh)*(1.0+0.35*aspectPenalty)/Math.max(0.18,density);
            if(score<bestScore){bestScore=score;bestStart=start;}
        }

        HashSet<Integer> keepIds=new HashSet<>();
        for(int i=bestStart;i<bestStart+keepCount;i++)keepIds.add(chain.get(i));

        ArrayList<Piece> kept=new ArrayList<>();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(int id:chain){
            if(!keepIds.contains(id))continue;
            Piece p=pieceById(id);
            if(p==null)continue;
            kept.add(p);
            for(Point q:p.pts){
                minX=Math.min(minX,q.x);maxX=Math.max(maxX,q.x);
                minY=Math.min(minY,q.y);maxY=Math.max(maxY,q.y);
            }
        }
        if(kept.size()<36||minX==Integer.MAX_VALUE)return;

        int shiftX=minX-1,shiftY=minY-1;
        for(Piece p:kept){
            ArrayList<Point> shifted=new ArrayList<>();
            for(Point q:p.pts)shifted.add(new Point(q.x-shiftX,q.y-shiftY));
            p.pts=shifted;
            p.removed=false;p.moving=false;p.moveT=0f;p.moveSteps=0;
            p.specialType=0;p.prereqA=-1;p.prereqB=-1;p.specialTarget=-1;
            rebuildOccupancy(p);
        }

        pieces.clear();
        pieces.addAll(kept);
        for(int i=0;i<pieces.size();i++)pieces.get(i).id=i;
        gridW=Math.max(18,maxX-minX+2);
        gridH=Math.max(22,maxY-minY+2);
    }

    private void generateLevel(int lv){
        if (packedLevels != null && lv >= 1 && lv <= packedLevels.length) {
            try {
                String line = packedLevels[lv-1];
                String[] half = line.split("\\|", 2);
                String[] dims = half[0].split(",");
                gridW = Integer.parseInt(dims[0]);
                gridH = Integer.parseInt(dims[1]);
                pieces.clear();
                if (half.length > 1 && !half[1].isEmpty()) {
                    String[] defs = half[1].split(";");
                    for (int i=0; i<defs.length; i++) {
                        String[] v = defs[i].split(",");
                        if (v.length < 6) continue;
                        Piece p = new Piece();
                        p.id = i;
                        p.dx = Integer.parseInt(v[0]);
                        p.dy = Integer.parseInt(v[1]);
                        for (int k=2; k+1<v.length; k+=2) {
                            p.pts.add(new Point(Integer.parseInt(v[k]), Integer.parseInt(v[k+1])));
                        }
                        rebuildOccupancy(p);
                        pieces.add(p);
                    }
                }
                return;
            } catch (Exception ignored) { }
        }
        generateFallbackLevel(lv);
    }

    private void generateFallbackLevel(int lv){
        if(lv<=20){gridW=18;gridH=24;}else if(lv<=60){gridW=24;gridH=30;}else if(lv<=120){gridW=28;gridH=34;}else{gridW=30;gridH=36;}
        int target;
        if(lv<=43) target=Math.min(72,Math.round(12+lv*1.4f));
        else target=Math.min(100,72+(lv-43)/4);
        target=Math.max(12,target);
        rng.setSeed(0x5A17BEEFL + lv*104729L);
        pieces.clear();
        HashSet<Long> occupiedNodes=new HashSet<>();
        HashSet<Long> occupiedEdges=new HashSet<>();
        for(int i=0;i<target;i++){
            Piece chosen=null;
            int attempts=0;
            while(attempts++<2800){
                Piece cand=randomPiece(i);
                if(cand==null)continue;
                if(intersects(cand,occupiedNodes,occupiedEdges,0))continue;
                if(clearAgainst(cand,occupiedNodes,occupiedEdges)) {chosen=cand;break;}
            }
            if(chosen==null) break;
            pieces.add(chosen);
            occupiedNodes.addAll(chosen.nodes); occupiedEdges.addAll(chosen.edges);
        }
        Collections.reverse(pieces);
        for(int i=0;i<pieces.size();i++)pieces.get(i).id=i;
    }

    private Piece randomPiece(int id){
        int[] dxs={1,-1,0,0}, dys={0,0,1,-1};
        int d=rng.nextInt(4),fdx=dxs[d],fdy=dys[d];
        int tipX=1+rng.nextInt(Math.max(1,gridW-1));
        int tipY=1+rng.nextInt(Math.max(1,gridH-1));
        ArrayList<Point> rev=new ArrayList<>();
        int cx=tipX,cy=tipY;rev.add(new Point(cx,cy));
        int backX=-fdx,backY=-fdy;
        int len=1+rng.nextInt(3);cx+=backX*len;cy+=backY*len;rev.add(new Point(cx,cy));
        int segs=1+weightedSegments();
        int pdx=backX,pdy=backY;
        for(int s=1;s<segs;s++){
            int ndx,ndy;
            if(pdx!=0){ndx=0;ndy=rng.nextBoolean()?1:-1;}else{ndy=0;ndx=rng.nextBoolean()?1:-1;}
            len=1+rng.nextInt(3);cx+=ndx*len;cy+=ndy*len;rev.add(new Point(cx,cy));pdx=ndx;pdy=ndy;
        }
        for(Point q:rev)if(q.x<0||q.x>gridW||q.y<0||q.y>gridH)return null;
        Collections.reverse(rev);
        Piece p=new Piece();p.id=id;p.dx=fdx;p.dy=fdy;p.pts=rev;
        rebuildOccupancy(p);
        int expected=1;for(int i=0;i<p.pts.size()-1;i++){Point a=p.pts.get(i),b=p.pts.get(i+1);expected+=Math.abs(b.x-a.x)+Math.abs(b.y-a.y);}if(p.nodes.size()!=expected)return null;
        if(!hasSelfClearance(p))return null;
        return p;
    }

    private int weightedSegments(){int v=rng.nextInt(100);if(v<20)return 0;if(v<60)return 1;if(v<88)return 2;return 3;}

    private void rebuildOccupancy(Piece p){p.nodes.clear();p.edges.clear();for(Point q:p.pts)p.nodes.add(nodeKey(q.x,q.y));for(int i=0;i<p.pts.size()-1;i++){Point a=p.pts.get(i),b=p.pts.get(i+1);int dx=Integer.compare(b.x,a.x),dy=Integer.compare(b.y,a.y);int x=a.x,y=a.y;while(x!=b.x||y!=b.y){int nx=x+dx,ny=y+dy;p.nodes.add(nodeKey(nx,ny));if(dx!=0)p.edges.add(edgeKey(Math.min(x,nx),y,1));else p.edges.add(edgeKey(x,Math.min(y,ny),2));x=nx;y=ny;}}}

    private boolean intersects(Piece p,HashSet<Long> nodes,HashSet<Long> edges,int shift){for(long n:p.nodes){int x=nodeX(n)+p.dx*shift,y=nodeY(n)+p.dy*shift;if(nodes.contains(nodeKey(x,y)))return true;}for(long e:p.edges){int x=edgeX(e)+p.dx*shift;int y=edgeY(e)+p.dy*shift;int intOri=(int)(e&3);if(edges.contains(edgeKey(x,y,intOri)))return true;}return false;}

    private boolean clearAgainst(Piece p,HashSet<Long> nodes,HashSet<Long> edges){int max=gridW+gridH+12;for(int k=1;k<=max;k++){if(intersects(p,nodes,edges,k))return false;if(allOutside(p,k))return true;}return false;}

    private boolean allOutside(Piece p,int k){for(Point q:p.pts){int x=q.x+p.dx*k,y=q.y+p.dy*k;if(x>=0&&x<=gridW&&y>=0&&y<=gridH)return false;}return true;}


    private int snakeTravelSteps(Piece p) {
        int length = 0;

        for (int i = 0;
   i < p.pts.size() - 1;
   i++) {

  Point a = p.pts.get(i);
  Point b = p.pts.get(i + 1);

  length +=
          Math.abs(b.x - a.x)
          + Math.abs(b.y - a.y);
        }

        Point tip =
      p.pts.get(
              p.pts.size() - 1
      );

        int exitDistance;

        if (p.dx > 0) {
  exitDistance =
          gridW - tip.x + 3;
        } else if (p.dx < 0) {
  exitDistance =
          tip.x + 3;
        } else if (p.dy > 0) {
  exitDistance =
          gridH - tip.y + 3;
        } else {
  exitDistance =
          tip.y + 3;
        }

        return length
      + Math.max(3, exitDistance);
    }

    private int exitSteps(Piece p){for(int k=1;k<gridW+gridH+20;k++)if(allOutside(p,k))return k+1;return gridW+gridH;}


    private Piece pieceById(int id) {
        for (Piece p : pieces) if (p.id == id) return p;
        return null;
    }

    private boolean specialUnlocked(Piece p) {
        if (p.specialType == 2 || p.specialType == 5 || p.specialType == 7) {
            Piece a = pieceById(p.prereqA);
            return a == null || a.removed;
        }
        if (p.specialType == 3) {
            Piece a = pieceById(p.prereqA), b = pieceById(p.prereqB);
            return (a == null || a.removed) && (b == null || b.removed);
        }
        return true;
    }

    private String specialBlockedMessage(Piece p) {
        if (specialUnlocked(p)) return null;
        if (p.specialType == 2) return "Locked · clear the key arrow first";
        if (p.specialType == 3) return "Frozen · clear both blocking arrows";
        if (p.specialType == 5) return "Gate closed · activate the switch";
        if (p.specialType == 7) return "Linked · clear the partner arrow first";
        return null;
    }

    /**
     * Finds only physical head-path blockers and deliberately ignores V18
     * special requirements. We use this once at level setup to place special
     * mechanics on dependency edges that already exist in the original puzzle.
     */
    private ArrayList<Piece> physicalBlockers(Piece target) {
        ArrayList<Piece> blockers = new ArrayList<>();
        if (target == null || target.pts.isEmpty()) return blockers;
        Point tip = target.pts.get(target.pts.size()-1);
        int x = tip.x, y = tip.y;
        int max = gridW + gridH + 20;
        HashSet<Integer> seen = new HashSet<>();
        for (int step=1; step<=max; step++) {
            int nx=x+target.dx*step, ny=y+target.dy*step;
            int px=x+target.dx*(step-1), py=y+target.dy*(step-1);
            if (nx<0 || nx>gridW || ny<0 || ny>gridH) break;
            for (Piece other : pieces) {
                if (other == target || seen.contains(other.id)) continue;
                boolean hit = other.nodes.contains(nodeKey(nx,ny));
                if (!hit) {
                    if (target.dx != 0) hit = other.edges.contains(edgeKey(Math.min(px,nx),py,1));
                    else hit = other.edges.contains(edgeKey(px,Math.min(py,ny),2));
                }
                if (hit) {
                    blockers.add(other);
                    seen.add(other.id);
                }
            }
        }
        blockers.sort((a,b)->Integer.compare(a.id,b.id));
        return blockers;
    }

    private boolean specialFree(Piece p) {
        return p != null && p.specialType == 0;
    }

    private void configureSpecialMechanics() {
        for (Piece p : pieces) {
            p.specialType=0; p.prereqA=-1; p.prereqB=-1; p.specialTarget=-1;
        }

        ArrayList<Piece> targets = new ArrayList<>(pieces);
        if (level < 41 || pieces.size() < 8) return;


        targets.sort((a,b)->Integer.compare(a.id,b.id));
        int rotate = Math.floorMod(level * 17, Math.max(1, targets.size()));
        Collections.rotate(targets, rotate);
        // Daily Challenge uses one rotating secondary mechanic instead of stacking
        // every late-game mechanic at once. This keeps special icons readable.
        int dailyMechanic=dailyChallenge?Math.floorMod(dailyChallengeNumberForDay(challengeDay),3):-1;

        // Chapter 3+: Key + Lock. The key is already a physical blocker.
        for (Piece target : targets) {
            ArrayList<Piece> blockers=physicalBlockers(target);
            if (specialFree(target) && !blockers.isEmpty()) {
                for (Piece key : blockers) if (specialFree(key)) {
                    key.specialType=1; key.specialTarget=target.id;
                    target.specialType=2; target.prereqA=key.id;
                    blockers=null;
                    break;
                }
            }
            if (target.specialType==2) break;
        }

        // Chapter 5+: Frozen arrow needs two blockers that already sit in its path.
        if (level >= 81 && (!dailyChallenge || dailyMechanic==0)) {
            for (Piece target : targets) {
                if (!specialFree(target)) continue;
                ArrayList<Piece> blockers=physicalBlockers(target);
                Piece a=null,b=null;
                for (Piece q:blockers) if (specialFree(q)) {
                    if(a==null)a=q; else {b=q;break;}
                }
                if(a!=null&&b!=null){
                    target.specialType=3;target.prereqA=a.id;target.prereqB=b.id;
                    break;
                }
            }
        }

        // Chapter 6+: Switch + Gate, also placed on an existing blocker edge.
        if (level >= 121 && (!dailyChallenge || dailyMechanic==1)) {
            for (Piece target : targets) {
                if (!specialFree(target)) continue;
                ArrayList<Piece> blockers=physicalBlockers(target);
                for (Piece sw:blockers) if (specialFree(sw)) {
                    sw.specialType=4;sw.specialTarget=target.id;
                    target.specialType=5;target.prereqA=sw.id;
                    blockers=null;
                    break;
                }
                if(target.specialType==5)break;
            }
        }

        // Chapter 7+: Linked pair. The source is an existing physical blocker,
        // so the link adds readable chain-order strategy without changing solvability.
        if (level >= 161 && (!dailyChallenge || dailyMechanic==2)) {
            for (Piece target : targets) {
                if (!specialFree(target)) continue;
                ArrayList<Piece> blockers=physicalBlockers(target);
                for (Piece source:blockers) if (specialFree(source)) {
                    source.specialType=6;source.specialTarget=target.id;
                    target.specialType=7;target.prereqA=source.id;
                    break;
                }
                if(target.specialType==7)break;
            }
        }
    }

    /**
     * Simulates the geometry of our approved snake/path-following motion against
     * the arrow's own body. The head travels straight while the tail advances
     * along the existing route. If the head reaches one of its own cells before
     * that cell has been vacated by the tail, the arrow is self-blocked.
     */
    private boolean hasSelfClearance(Piece target) {
        HashMap<Long,Integer> pathDistance = new HashMap<>();
        int walked = 0;
        if(target.pts.isEmpty())return false;
        Point first=target.pts.get(0);
        pathDistance.put(nodeKey(first.x,first.y),0);
        for(int i=0;i<target.pts.size()-1;i++){
            Point a=target.pts.get(i),b=target.pts.get(i+1);
            int sx=Integer.compare(b.x,a.x),sy=Integer.compare(b.y,a.y);
            int len=Math.abs(b.x-a.x)+Math.abs(b.y-a.y);
            for(int k=1;k<=len;k++){
                walked++;
                long key=nodeKey(a.x+sx*k,a.y+sy*k);
                Integer previous=pathDistance.get(key);
                if(previous==null||walked>previous)pathDistance.put(key,walked);
            }
        }

        Point tip=target.pts.get(target.pts.size()-1);
        int max=gridW+gridH+20;
        for(int step=1;step<=max;step++){
            int nx=tip.x+target.dx*step,ny=tip.y+target.dy*step;
            if(nx<0||nx>gridW||ny<0||ny>gridH)return true;
            Integer ownDistance=pathDistance.get(nodeKey(nx,ny));
            if(ownDistance!=null&&ownDistance>=step)return false;
        }
        return true;
    }

    private void addMovingOccupancy(Piece p,HashSet<Long> nodes,HashSet<Long> edges) {
        float total=piecePathLength(p);
        float advance=p.moveT*Math.max(0,p.moveSteps);
        float end=advance+total;
        ArrayList<Float> stops=new ArrayList<>();
        stops.add(advance);
        float walked=0f;
        for(int i=0;i<p.pts.size()-1;i++){
            Point a=p.pts.get(i),b=p.pts.get(i+1);
            walked+=Math.abs(b.x-a.x)+Math.abs(b.y-a.y);
            if(walked>advance+0.0001f&&walked<end-0.0001f)stops.add(walked);
        }
        stops.add(end);
        for(int i=0;i<stops.size()-1;i++){
            android.graphics.PointF a=routePoint(p,stops.get(i));
            android.graphics.PointF b=routePoint(p,stops.get(i+1));
            addMovingSegmentOccupancy(a,b,nodes,edges);
        }
    }

    private void addMovingSegmentOccupancy(android.graphics.PointF a,android.graphics.PointF b,HashSet<Long> nodes,HashSet<Long> edges) {
        final float eps=0.001f;
        if(Math.abs(a.y-b.y)<=eps){
            int y=Math.round((a.y+b.y)*0.5f);
            float lo=Math.min(a.x,b.x),hi=Math.max(a.x,b.x);
            if(hi-lo<=eps)return;
            int firstNode=(int)Math.ceil(lo-eps),lastNode=(int)Math.floor(hi+eps);
            for(int x=firstNode;x<=lastNode;x++)if(x>=0&&x<=gridW&&y>=0&&y<=gridH)nodes.add(nodeKey(x,y));
            int firstEdge=(int)Math.floor(lo),lastEdge=(int)Math.ceil(hi)-1;
            for(int x=firstEdge;x<=lastEdge;x++){
                float overlap=Math.min(hi,x+1f)-Math.max(lo,x);
                if(overlap>eps&&x>=0&&x<gridW&&y>=0&&y<=gridH)edges.add(edgeKey(x,y,1));
            }
        }else if(Math.abs(a.x-b.x)<=eps){
            int x=Math.round((a.x+b.x)*0.5f);
            float lo=Math.min(a.y,b.y),hi=Math.max(a.y,b.y);
            if(hi-lo<=eps)return;
            int firstNode=(int)Math.ceil(lo-eps),lastNode=(int)Math.floor(hi+eps);
            for(int y=firstNode;y<=lastNode;y++)if(x>=0&&x<=gridW&&y>=0&&y<=gridH)nodes.add(nodeKey(x,y));
            int firstEdge=(int)Math.floor(lo),lastEdge=(int)Math.ceil(hi)-1;
            for(int y=firstEdge;y<=lastEdge;y++){
                float overlap=Math.min(hi,y+1f)-Math.max(lo,y);
                if(overlap>eps&&x>=0&&x<=gridW&&y>=0&&y<gridH)edges.add(edgeKey(x,y,2));
            }
        }
    }


    private float predictedTravelSeconds(int steps) {
        float c=cell>0.001f?cell:1f;
        float widthPx=getWidth()>0?getWidth():(gridW+2f)*c;
        long now=SystemClock.elapsedRealtime();
        int projectedCombo=(lastCorrectTapAt>0L&&now-lastCorrectTapAt<=2400L)?combo+1:1;
        float flowBoost=1f+Math.min(10,Math.max(1,projectedCombo))*.035f;
        float speedPxPerSec=Math.max(widthPx*1.30f,c*9f)*flowBoost;
        float travelPx=Math.max(c,Math.max(1,steps)*c);
        return Math.max(.30f,Math.min(1.45f,travelPx/Math.max(.001f,speedPxPerSec)));
    }

    private float pointSegmentDistance(android.graphics.PointF q,android.graphics.PointF a,android.graphics.PointF b) {
        float vx=b.x-a.x,vy=b.y-a.y,wx=q.x-a.x,wy=q.y-a.y;
        float vv=vx*vx+vy*vy;
        if(vv<=.000001f)return (float)Math.hypot(q.x-a.x,q.y-a.y);
        float t=Math.max(0f,Math.min(1f,(wx*vx+wy*vy)/vv));
        float px=a.x+t*vx,py=a.y+t*vy;
        return (float)Math.hypot(q.x-px,q.y-py);
    }

    private float distanceToSnake(Piece p,float advance,android.graphics.PointF q) {
        float total=piecePathLength(p);
        float from=Math.max(0f,advance),to=from+total;
        android.graphics.PointF prev=routePoint(p,from);
        float best=(float)Math.hypot(q.x-prev.x,q.y-prev.y);
        float walked=0f;
        for(int i=0;i<p.pts.size()-1;i++){
            Point a=p.pts.get(i),b=p.pts.get(i+1);
            walked+=Math.abs(b.x-a.x)+Math.abs(b.y-a.y);
            if(walked>from+.0001f&&walked<to-.0001f){
                android.graphics.PointF cur=routePoint(p,walked);
                best=Math.min(best,pointSegmentDistance(q,prev,cur));
                prev=cur;
            }
        }
        android.graphics.PointF end=routePoint(p,to);
        return Math.min(best,pointSegmentDistance(q,prev,end));
    }

    /**
     * A moving arrow must not reserve its whole lane. We compare both arrows at
     * the same future instants and block only if their live snake geometries
     * would actually meet while they are escaping.
     */
    private boolean willCollideWithMoving(Piece target,Piece moving) {
        if(moving==null||moving.removed||!moving.moving)return false;
        int targetSteps=Math.max(1,snakeTravelSteps(target));
        int movingSteps=Math.max(1,moving.moveSteps>0?moving.moveSteps:snakeTravelSteps(moving));
        float targetSec=predictedTravelSeconds(targetSteps);
        float movingSec=predictedTravelSeconds(movingSteps);
        float movingStart=Math.max(0f,Math.min(1f,moving.moveT))*movingSteps;
        float targetRate=targetSteps/Math.max(.001f,targetSec);
        float movingRate=movingSteps/Math.max(.001f,movingSec);
        float movingRemaining=(movingSteps-movingStart)/Math.max(.001f,movingRate);
        float horizon=Math.min(targetSec,movingRemaining);
        if(horizon<=0f)return false;

        float targetTotal=piecePathLength(target);
        float movingTotal=piecePathLength(moving);
        float maxRate=Math.max(targetRate,movingRate);
        int samples=Math.max(12,Math.min(1200,(int)Math.ceil(horizon*maxRate*10f)));
        final float collisionRadius=.12f;
        for(int i=0;i<=samples;i++){
            float t=horizon*i/(float)samples;
            float targetAdvance=Math.min(targetSteps,targetRate*t);
            float movingAdvance=Math.min(movingSteps,movingStart+movingRate*t);
            android.graphics.PointF targetHead=routePoint(target,targetTotal+targetAdvance);
            android.graphics.PointF movingHead=routePoint(moving,movingTotal+movingAdvance);
            if(distanceToSnake(moving,movingAdvance,targetHead)<=collisionRadius)return true;
            if(distanceToSnake(target,targetAdvance,movingHead)<=collisionRadius)return true;
        }
        return false;
    }

    private boolean isClear(Piece target) {
        if(!specialUnlocked(target))return false;
        if(!hasSelfClearance(target))return false;

        HashSet<Long> nodes =
      new HashSet<>();

        HashSet<Long> edges =
      new HashSet<>();

        for (Piece p : pieces) {
  if (p == target || p.removed) continue;
  // Accepted escaping arrows are logically removed immediately; animation is visual only.
  if (p.moving) continue;
  nodes.addAll(p.nodes);
  edges.addAll(p.edges);
        }

        Point tip =
      target.pts.get(
              target.pts.size() - 1
      );

        int x = tip.x;
        int y = tip.y;

        int max =
      gridW + gridH + 20;

        for (int step = 1;
   step <= max;
   step++) {

  int nx =
          x + target.dx * step;

  int ny =
          y + target.dy * step;

  int px =
          x + target.dx * (step - 1);

  int py =
          y + target.dy * (step - 1);

  if (nx < 0
          || nx > gridW
          || ny < 0
          || ny > gridH) {

      return true;
  }

  if (nodes.contains(
          nodeKey(nx, ny))) {

      return false;
  }

  if (target.dx != 0) {

      if (edges.contains(
              edgeKey(
                      Math.min(px, nx),
                      py,
                      1))) {

          return false;
      }

  } else {

      if (edges.contains(
              edgeKey(
                      px,
                      Math.min(py, ny),
                      2))) {

          return false;
      }
  }
        }

        return true;
    }


    private long nodeKey(int x,int y){return (((long)(x+128)&0xFFFFL)<<16)|((long)(y+128)&0xFFFFL);}
    private int nodeX(long key){return (int)((key>>>16)&0xFFFFL)-128;}
    private int nodeY(long key){return (int)(key&0xFFFFL)-128;}
    private long edgeKey(int x,int y,int o){return (((long)(x+128)&0xFFFFL)<<20)|(((long)(y+128)&0xFFFFL)<<4)|(o&3);}
    private int edgeX(long key){return (int)((key>>>20)&0xFFFFL)-128;}
    private int edgeY(long key){return (int)((key>>>4)&0xFFFFL)-128;}

    private void showToast(String s){toast=s;toastUntil=SystemClock.elapsedRealtime()+1200;invalidate();}
    private void buzz(long ms){try{if(vibrator==null||!settings.getBoolean("haptics",true))return;if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,80));else vibrator.vibrate(ms);}catch(Exception ignored){}}

    @Override public boolean performClick(){super.performClick();return true;}
    public void setPaused(boolean value){paused=value;lastFrame=0;invalidate();}
    public void release(){tone.release();}
    private void playSound(int id,int duration){if(settings.getBoolean("sound",true))tone.startTone(id,duration);}
    public boolean canBuyHeart(){return !finished&&!failed&&hearts<3;}
    public boolean needsRevive(){return failed;}
    public boolean buyHeart(){if(!canBuyHeart()||!wallet.spend(Wallet.HEART_COST))return false;hearts++;assistsUsed++;saveProgress();invalidate();return true;}
    public boolean buyContinue(){if(!failed||!wallet.spend(Wallet.CONTINUE_COST))return false;grantRevive();return true;}
    public void restartCurrentLevel(){if(challengeActive())setupLevel(level,false);else startLevel(level);}
    public void showTutorialAgain(){screen=Screen.PLAY;tutorial=true;invalidate();}
    public void finishGuidedTutorial(){tutorial=false;}
    private void showLevelSelect(){cancelBooster();tutorial=false;screen=Screen.LEVELS;centerMapOnCurrent();saveProgress();host.onRouteOpened();invalidate();}
    public void openLevels(){
        if(challengeActive())exitChallengeToCampaign();
        showLevelSelect();
    }
    public void openPlay(){
        // The Home screen PLAY button always means the normal 1–400 campaign.
        // Daily/weekly challenges have their own entry points and must never trap
        // the player in the internal difficulty-profile level used by a challenge.
        if(challengeActive())exitChallengeToCampaign();
        screen=Screen.PLAY;host.onGameplayStarted();invalidate();
    }
    public int currentLevelNumber(){return challengeActive()?Math.max(1,Math.min(MAX_LEVEL,normalLevelBeforeChallenge)):level;}
    public int dailyPuzzleNumber(){return dailyChallengeNumberForDay(System.currentTimeMillis()/Wallet.DAY_MS);}
    public int totalStarCount(){return totalStars();}
    public int completedLevelCount(){return completedLevels();}
    public int perfectLevelCount(){return perfectLevels();}
    public boolean allBossesComplete(){return allBossesCleared();}
    public boolean handleBack(){
        if(boosterMode!=0){cancelBooster();return true;}
        if(screen==Screen.LEVELS)return false;
        if(tutorial){tutorial=false;prefs.edit().putBoolean("tutorialSeen",true).apply();invalidate();return true;}
        if(challengeActive()){leaveChallenge();invalidate();return true;}
        host.openHome();return true;
    }
    public void saveProgress(){
        if(runId==null)return;
        try {
            org.json.JSONObject state=new org.json.JSONObject();org.json.JSONArray removed=new org.json.JSONArray();
            for(Piece p:pieces)if(p.removed||p.moving)removed.put(p.id);
            state.put("packVersion",LEVEL_PACK_VERSION).put("level",level).put("run",runId).put("hearts",hearts).put("hints",hints).put("erasers",erasers)
                 .put("mistakes",mistakes).put("assists",assistsUsed).put("earnedStars",earnedStars).put("combo",combo).put("bestCombo",bestCombo).put("flowShield",flowShield)
                 .put("dailyChallenge",dailyChallenge).put("challengeDay",challengeDay).put("weeklyChallenge",weeklyChallenge).put("challengeWeek",challengeWeek).put("normalLevel",normalLevelBeforeChallenge)
                 .put("finished",finished).put("failed",failed).put("winReward",winReward).put("removed",removed);
            prefs.edit().putString("v16_progress",state.toString()).commit();
        }catch(org.json.JSONException ignored){}
    }
    private void restoreProgress(String checkpoint){
        if(checkpoint==null||checkpoint.isEmpty())return;
        try {
            org.json.JSONObject state=new org.json.JSONObject(checkpoint);int savedLevel=state.getInt("level");
            if(savedLevel<1||savedLevel>MAX_LEVEL)return;
            // Piece IDs changed with regenerated levels. Preserve wallet, stars
            // and unlocked levels, but restart an incompatible in-level board.
            if(!LEVEL_PACK_VERSION.equals(state.optString("packVersion","")))return;
            boolean savedWeekly=state.optBoolean("weeklyChallenge",false);
            boolean savedDaily=state.optBoolean("dailyChallenge",false);
            if(savedWeekly){
                weeklyChallenge=true;dailyChallenge=false;challengeWeek=state.optLong("challengeWeek",(System.currentTimeMillis()/Wallet.DAY_MS)/7L);normalLevelBeforeChallenge=state.optInt("normalLevel",prefs.getInt("lastLevel",1));setupLevel(savedLevel,false);
            } else if(savedDaily){
                dailyChallenge=true;weeklyChallenge=false;challengeDay=state.optLong("challengeDay",System.currentTimeMillis()/Wallet.DAY_MS);normalLevelBeforeChallenge=state.optInt("normalLevel",prefs.getInt("lastLevel",1));setupLevel(MAX_LEVEL,false);
            } else startLevel(savedLevel);
            runId=state.getString("run");hearts=Math.max(0,Math.min(3,state.getInt("hearts")));
            hints=Math.max(0,Math.min(2,state.getInt("hints")));erasers=Math.max(0,state.getInt("erasers"));mistakes=state.optInt("mistakes",0);
            assistsUsed=state.optInt("assists",0);earnedStars=state.optInt("earnedStars",0);combo=Math.max(0,state.optInt("combo",0));bestCombo=Math.max(combo,state.optInt("bestCombo",combo));flowShield=state.optBoolean("flowShield",false);lastCorrectTapAt=0L;
            finished=state.optBoolean("finished",false);failed=state.optBoolean("failed",false);winReward=state.optInt("winReward",0);
            org.json.JSONArray removed=state.getJSONArray("removed");java.util.HashSet<Integer> ids=new java.util.HashSet<>();
            for(int i=0;i<removed.length();i++)ids.add(removed.getInt(i));for(Piece p:pieces)p.removed=ids.contains(p.id);
            saveProgress();invalidate();
        }catch(org.json.JSONException ignored){startLevel(level);}
    }
    private void label(Canvas c,String value,float x,float y,float size,int color,boolean bold){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(size));paint.setFakeBoldText(bold);
        c.drawText(value,x,y,paint);paint.setFakeBoldText(false);
    }
    private void drawCoin(Canvas c,float x,float y,float radius){
        paint.setColor(Color.rgb(230,150,10));c.drawCircle(x,y+dp(2),radius,paint);
        paint.setColor(Color.rgb(255,204,57));c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1),radius*.08f));paint.setColor(Color.rgb(255,238,154));c.drawCircle(x,y,radius*.78f,paint);paint.setStyle(Paint.Style.FILL);
        Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?radius*.53f:radius*.24f;float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)star.moveTo(px,py);else star.lineTo(px,py);}star.close();paint.setColor(Color.rgb(255,249,211));c.drawPath(star,paint);
    }
    private RectF modal(Canvas c,float height){
        paint.setColor(Color.argb(205,2,7,20));c.drawRect(0,0,getWidth(),getHeight(),paint);
        float top=Math.max(insetTop+dp(12),(getHeight()-dp(height))/2);
        RectF r=new RectF(dp(20),top,getWidth()-dp(20),top+dp(height));
        pill(c,r,Color.rgb(10,25,55));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.2f));paint.setColor(Color.rgb(34,88,142));c.drawRoundRect(r,dp(16),dp(16),paint);paint.setStyle(Paint.Style.FILL);
        return r;
    }
    private void action(Canvas c,RectF r,String value,int background,int foreground){pill(c,r,background);label(c,value,r.centerX(),r.centerY()+dp(5),15,foreground,true);}
}
