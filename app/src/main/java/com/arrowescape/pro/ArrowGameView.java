package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
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
        void requestRewardedRevive();
        void requestRewardedCoins();
        void openWallet();
        void openSettings();
        default void openHome() {}
        void onContinueAfterWin(Runnable proceed);
    }

    private static final int MAX_LEVEL = 200;
    private static final String LEVEL_PACK_VERSION = "v24-daily-separate-1";
    private static final int NAVY = Color.rgb(239, 247, 255);
    private static final int BLUE = Color.rgb(23, 199, 255);
    private static final int PALE = Color.rgb(13, 30, 64);
    private static final int DOT = Color.rgb(35, 63, 110);
    private static final int RED = Color.rgb(255, 82, 112);
    private static final int LOST_HEART = Color.rgb(55, 76, 112);
    private static final int TEXT = Color.rgb(190, 211, 239);
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
    private int gridW = 24, gridH = 30;
    private boolean finished = false;
    private boolean failed = false;
    private boolean tutorial;
    private boolean settingsOpen = false;
    private int levelPage = 0;

    private float boardLeft, boardTop, cell, boardW, boardH;
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
        settings = context.getSharedPreferences("arrow_escape_pro", Context.MODE_PRIVATE);
        prefs = context.getSharedPreferences("arrow_puzzle_faithful", Context.MODE_PRIVATE);
        maxUnlocked = Math.max(1, prefs.getInt("maxUnlocked", 1));
        level = Math.min(MAX_LEVEL, Math.max(1, prefs.getInt("lastLevel", 1)));
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
        if (toastUntil > now) drawToast(c, toast);
        if (previewPiece != null && previewPiece.removed) previewPiece = null;
        if (touchRippleUntil > now) {
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
        drawBack(c, dp(28), top + dp(30));
        String title = weeklyChallenge ? "Weekly Challenge" : dailyChallenge ? "Daily Challenge" : (isBoss(level) ? "Boss Level " + level : "Level " + level);
        label(c, title, w/2, top + dp(29), 23, NAVY, true);
        int difficultyColor = challengeActive() ? DAILY_PURPLE : (isBoss(level) ? BOSS_GOLD : isSuperHard(level) ? SUPER_HARD : Color.rgb(100,116,139));
        String sub = weeklyChallenge
            ? "ELITE WEEKLY  ·  puzzle " + level
            : dailyChallenge
            ? "SUPER HARD DAILY  ·  puzzle " + dailyChallengeNumberForDay(challengeDay)
            : "CH " + chapterNumber(level) + " · " + chapterName(level) + " · " + difficulty();
        if (!challengeActive() && combo >= 2) sub += " · STREAK x" + combo;
        label(c, sub, w/2, top + dp(49), 11, difficultyColor, challengeActive() || isSuperHard(level) || combo >= 3);
        drawFlowMeter(c,w/2,top+dp(65));
        drawGear(c, w-dp(31), top+dp(30));
        float statY = top + dp(84);
        pill(c,new RectF(dp(16),statY-dp(21),dp(94),statY+dp(21)),PALE);
        drawMiniArrow(c,dp(32),statY,dp(12),NAVY,-45);
        label(c,String.valueOf(remaining()),dp(65),statY+dp(6),18,NAVY,true);
        for(int i=0;i<3;i++) drawHeart(c,w/2-dp(30)+i*dp(30),statY,dp(11),i<hearts?RED:LOST_HEART);
        walletHit.set(w-dp(112),statY-dp(21),w-dp(16),statY+dp(21));
        pill(c,walletHit,Color.rgb(55,43,14)); drawCoin(c,walletHit.left+dp(19),statY,dp(10));
        String amount = wallet.balance()>99999 ? (wallet.balance()/1000)+"k" : String.valueOf(wallet.balance());
        label(c,amount,walletHit.centerX()+dp(10),statY+dp(6),16,NAVY,true);
        float controlY=h-insetBottom-dp(58),gap=dp(10),controlW=(w-dp(32)-2*gap)/3;
        hintHit.set(dp(16),controlY-dp(28),dp(16)+controlW,controlY+dp(28));
        eraseHit.set(hintHit.right+gap,hintHit.top,hintHit.right+gap+controlW,hintHit.bottom);
        rewardHit.set(eraseHit.right+gap,hintHit.top,w-dp(16),hintHit.bottom);
        float areaTop=statY+dp(40), areaBottom=hintHit.top-dp(23);
        float availW=w-dp(30),availH=Math.max(dp(80),areaBottom-areaTop);
        cell=Math.min(availW/(gridW+2f),availH/(gridH+2f));
        boardW=gridW*cell;boardH=gridH*cell;boardLeft=(w-boardW)/2;boardTop=areaTop+(availH-boardH)/2;
        c.save();c.clipRect(dp(8),areaTop,w-dp(8),areaBottom);drawDottedGrid(c);drawPieces(c,now);drawPathPreview(c);c.restore();
        pill(c,hintHit,PALE);pill(c,eraseHit,PALE);pill(c,rewardHit,Color.rgb(55,43,14));
        drawBulb(c,hintHit.centerX(),controlY-dp(8),dp(12));
        label(c,hints>0?"Hint · "+hints+" free":"Hint · 25 coins",hintHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawEraser(c,eraseHit.centerX(),controlY-dp(8),dp(12));
        label(c,erasers>0?"Erase · "+erasers+" free":"Erase · watch ad",eraseHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawCoin(c,rewardHit.centerX(),controlY-dp(8),dp(11));
        label(c,"Store",rewardHit.centerX(),controlY+dp(18),11,NAVY,true);
        label(c,"THINK  ·  TAP  ·  ESCAPE",w/2,h-insetBottom-dp(10),9,Color.rgb(102,145,194),false);
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
    public String currentShapeName(){return shapeNameForLevel(level);}
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
        float stroke=Math.max(dp(2.0f),cell*arrowStroke());
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
        label(c,weeklyChallenge?"WEEKLY CHALLENGE":dailyChallenge?"DAILY CHALLENGE":(isBoss(level)?"BOSS MILESTONE":"SUPER HARD"),r.centerX(),r.top+dp(52),28,accent,true);
        label(c,weeklyChallenge?"One elite puzzle for the whole UTC week":dailyChallenge?"Same challenge for everyone today":"Level "+level+" is a milestone",r.centerX(),r.top+dp(89),15,NAVY,true);
        label(c,isBoss(level)&&!challengeActive()?"The toughest checkpoint in this chapter":weeklyChallenge?"A fixed high-level milestone puzzle. Best stars are saved all week.":"Expect deeper blocking chains and fewer obvious exits.",r.centerX(),r.top+dp(121),13,TEXT,false);
        label(c,weeklyChallenge?"Reward: 250–350 coins based on stars":dailyChallenge?"Reward: 100–150 coins based on stars":(isBoss(level)?"Boss bonus: +150 coins":"Milestone bonus: +75 coins"),r.centerX(),r.top+dp(153),13,accent,true);
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
        c.drawColor(Color.rgb(5,11,30));
        float w=getWidth(),h=getHeight();
        paint.setColor(Color.argb(18,255,255,255));
        c.drawCircle(w*.85f,h*.12f,dp(145),paint);c.drawCircle(w*.05f,h*.80f,dp(125),paint);
        float top=Math.max(insetTop+dp(18),(h-dp(520))/2);
        String headline=weeklyChallenge?"Weekly challenge cleared!":dailyChallenge?"Daily challenge cleared!":(level==200?"All 200 mazes cleared!":isBoss(level)?"Boss escaped!":"Maze escaped!");
        label(c,headline,w/2,top+dp(32),26,Color.WHITE,true);
        label(c,weeklyChallenge?"Elite puzzle of the week complete":dailyChallenge?"Puzzle of the day complete":"Level "+level+" complete",w/2,top+dp(61),16,Color.rgb(176,206,246),false);
        label(c,starString(earnedStars),w/2,top+dp(108),31,Color.rgb(255,204,57),true);
        label(c,earnedStars==3?"Perfect clear":earnedStars==2?"Strong clear":"Cleared",w/2,top+dp(136),13,Color.rgb(205,220,242),false);
        RectF card=new RectF(dp(26),top+dp(166),w-dp(26),top+dp(326));pill(c,card,Color.rgb(12,27,58));
        label(c,winReward>0?"+"+winReward+" coins":"Reward already claimed",w/2,card.top+dp(38),26,NAVY,true);
        String rewardLine;
        if(weeklyChallenge) rewardLine="Weekly challenge · "+(earnedStars==3?"+350":earnedStars==2?"+300":"+250");
        else if(dailyChallenge) rewardLine="Daily challenge · +200 coins · once per day";
        else if(isBoss(level)) rewardLine="Clear +15 · Star bonus · Boss bonus +150";
        else if(isSuperHard(level)) rewardLine="Clear +15 · Star bonus · Milestone +75";
        else rewardLine="Clear +15 · 2★ +5 · 3★ +10";
        label(c,rewardLine,w/2,card.top+dp(72),13,TEXT,false);
        label(c,challengeActive()?"Replay during this challenge window to improve your stars":(earnedStars==3?"Best rating saved":"Replay later to earn 3★"),w/2,card.top+dp(101),13,Color.rgb(151,179,216),false);
        label(c,"Wallet: "+wallet.balance()+" coins",w/2,card.top+dp(132),13,NAVY,true);
        primaryHit.set(dp(26),top+dp(347),w-dp(26),top+dp(399));
        secondaryHit.set(dp(26),top+dp(411),w-dp(26),top+dp(461));
        tertiaryHit.set(dp(26),top+dp(466),w-dp(26),top+dp(504));
        action(c,primaryHit,challengeActive()?"Back to Home":(level<200?"Next level":"View all levels"),BLUE,Color.WHITE);
        action(c,secondaryHit,"Watch ad · +75 coins",Color.rgb(255,202,69),Color.rgb(27,31,43));
        label(c,challengeActive()?"Replay challenge":"Level select",w/2,tertiaryHit.centerY()+dp(5),13,Color.WHITE,false);
    }

    private void drawFail(Canvas c) {
        RectF r=modal(c,365);
        label(c,"A fresh way out",r.centerX(),r.top+dp(43),25,NAVY,true);
        label(c,"No hearts left. Your coins are safe.",r.centerX(),r.top+dp(78),14,TEXT,false);
        primaryHit.set(r.left+dp(20),r.top+dp(104),r.right-dp(20),r.top+dp(154));
        secondaryHit.set(r.left+dp(20),r.top+dp(165),r.right-dp(20),r.top+dp(215));
        tertiaryHit.set(r.left+dp(20),r.top+dp(226),r.right-dp(20),r.top+dp(276));
        action(c,primaryHit,"Restart level",BLUE,Color.WHITE);
        action(c,secondaryHit,"Watch ad · revive",PALE,NAVY);
        action(c,tertiaryHit,"Continue · 60 coins",Color.rgb(86,62,19),Color.rgb(255,223,130));
        walletHit.set(r.left+dp(20),r.top+dp(291),r.right-dp(20),r.bottom-dp(8));
        label(c,"Wallet: "+wallet.balance()+"  ·  Open Store",r.centerX(),walletHit.centerY()+dp(5),13,NAVY,false);
    }

    private void drawLevels(Canvas c) {
        c.drawColor(boardBackground());
        float w=getWidth(),h=getHeight(),top=insetTop+dp(12);
        drawThemeAtmosphere(c,w,h);
        label(c,"SELECT LEVEL",w/2,top+dp(31),25,NAVY,true);
        int pageLevel=Math.min(MAX_LEVEL,levelPage*20+1);
        int pageChapter=chapterNumber(pageLevel);
        label(c,"CH "+pageChapter+" · "+chapterName(pageLevel)+"   |   "+totalStars()+"/600★",w/2,top+dp(56),11,Color.rgb(142,177,218),false);
        drawBack(c,dp(28),top+dp(28));

        RectF range=new RectF(dp(18),top+dp(70),w-dp(18),top+dp(112));
        pill(c,range,Color.rgb(10,24,54));
        label(c,(levelPage*20+1)+"–"+Math.min(200,levelPage*20+20)+"  ·  "+chapterTrophyTier(pageChapter),range.centerX(),range.centerY()+dp(5),12,themeAccent(),true);

        int start=levelPage*20+1;
        float gap=dp(9),left=dp(18),bw=(w-left*2-gap*3)/4f,bh=dp(66),y0=top+dp(126);
        for(int i=0;i<20;i++){
            int lv=start+i;if(lv>MAX_LEVEL)break;
            int col=i%4,row=i/4;
            RectF r=new RectF(left+col*(bw+gap),y0+row*(bh+gap),left+col*(bw+gap)+bw,y0+row*(bh+gap)+bh);
            boolean open=lv<=maxUnlocked,milestone=isSuperHard(lv),boss=isBoss(lv),current=lv==level;
            int cardColor=!open?Color.rgb(8,19,42):(current?Color.rgb(13,53,91):Color.rgb(10,29,61));
            paint.setColor(cardColor);c.drawRoundRect(r,dp(15),dp(15),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(current?2.2f:1.2f));
            paint.setColor(!open?Color.rgb(25,46,76):(boss?BOSS_GOLD:milestone?SUPER_HARD:current?themeAccent():Color.rgb(31,72,116)));
            c.drawRoundRect(r,dp(15),dp(15),paint);paint.setStyle(Paint.Style.FILL);
            int stars=open?bestStars(lv):0;
            paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(true);paint.setTextSize(dp(17));
            paint.setColor(open?(boss?BOSS_GOLD:milestone?SUPER_HARD:NAVY):Color.rgb(70,91,123));
            c.drawText(open?String.valueOf(lv):"•",r.centerX(),r.top+dp(26),paint);paint.setFakeBoldText(false);
            if(open)label(c,starString(stars),r.centerX(),r.bottom-dp(8),8,stars>0?Color.rgb(255,204,57):Color.rgb(66,89,122),true);
            if(milestone)label(c,boss?"BOSS":"★",r.right-dp(boss?20:12),r.top+dp(14),boss?7:10,open?(boss?BOSS_GOLD:SUPER_HARD):Color.rgb(70,70,90),true);
        }
        float y=h-insetBottom-dp(72);
        RectF prev=new RectF(dp(22),y,w*.46f,y+dp(48)),next=new RectF(w*.54f,y,w-dp(22),y+dp(48));
        pill(c,prev,Color.rgb(10,29,61));pill(c,next,Color.rgb(10,29,61));
        label(c,"‹ PREV",prev.centerX(),prev.centerY()+dp(5),14,levelPage>0?NAVY:Color.rgb(69,87,116),true);
        label(c,"NEXT ›",next.centerX(),next.centerY()+dp(5),14,levelPage<9?NAVY:Color.rgb(69,87,116),true);
    }

    private void drawBack(Canvas c,float x,float y){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3.5f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(BLUE);Path p=new Path();p.moveTo(x+dp(8),y-dp(13));p.lineTo(x-dp(5),y);p.lineTo(x+dp(8),y+dp(13));c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}

    private void drawGear(Canvas c,float x,float y){paint.setColor(BLUE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));c.drawCircle(x,y,dp(10),paint);c.drawCircle(x,y,dp(4),paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=(float)(x+Math.cos(a)*dp(12)),y1=(float)(y+Math.sin(a)*dp(12));float x2=(float)(x+Math.cos(a)*dp(16)),y2=(float)(y+Math.sin(a)*dp(16));c.drawLine(x1,y1,x2,y2,paint);}paint.setStyle(Paint.Style.FILL);}

    private void drawHeart(Canvas c,float x,float y,float s,int color){Path p=new Path();p.moveTo(x,y+s*.9f);p.cubicTo(x-s*1.25f,y+s*.1f,x-s*1.15f,y-s*.75f,x-s*.55f,y-s*.85f);p.cubicTo(x-s*.1f,y-s*.95f,x,y-s*.55f,x,y-s*.35f);p.cubicTo(x,y-s*.55f,x+s*.1f,y-s*.95f,x+s*.55f,y-s*.85f);p.cubicTo(x+s*1.15f,y-s*.75f,x+s*1.25f,y+s*.1f,x,y+s*.9f);p.close();paint.setColor(color);c.drawPath(p,paint);}

    private void pill(Canvas c,RectF r,int color){paint.setColor(color);c.drawRoundRect(r,dp(16),dp(16),paint);}

    private void drawRoundControl(Canvas c,float x,float y,float r){paint.setColor(Color.rgb(20,51,90));c.drawCircle(x,y,r+dp(3),paint);paint.setColor(Color.rgb(10,29,61));c.drawCircle(x,y,r,paint);}

    private void drawBulb(Canvas c,float x,float y,float s){paint.setColor(Color.rgb(255,193,30));c.drawCircle(x,y-s*.25f,s*.62f,paint);paint.setColor(Color.rgb(255,218,72));c.drawCircle(x-s*.2f,y-s*.45f,s*.2f,paint);paint.setColor(Color.rgb(139,157,191));RectF base=new RectF(x-s*.35f,y+s*.30f,x+s*.35f,y+s*.82f);c.drawRoundRect(base,s*.13f,s*.13f,paint);paint.setColor(Color.rgb(95,113,151));c.drawRect(x-s*.28f,y+s*.62f,x+s*.28f,y+s*.78f,paint);}

    private void drawEraser(Canvas c,float x,float y,float s){c.save();c.rotate(-28,x,y);RectF body=new RectF(x-s*.55f,y-s*.82f,x+s*.55f,y+s*.82f);paint.setColor(Color.rgb(107,82,224));c.drawRoundRect(body,s*.18f,s*.18f,paint);RectF stripe=new RectF(body.left+s*.12f,body.top+s*.23f,body.right-s*.12f,body.top+s*.42f);paint.setColor(Color.rgb(159,140,244));c.drawRoundRect(stripe,s*.08f,s*.08f,paint);paint.setColor(Color.rgb(229,232,245));c.drawRoundRect(new RectF(body.left,body.bottom-s*.23f,body.right,body.bottom),s*.12f,s*.12f,paint);c.restore();}

    private void drawMiniArrow(Canvas c,float x,float y,float s,int color,float degrees){c.save();c.rotate(degrees,x,y);paint.setColor(color);Path a=new Path();a.moveTo(x+s*.7f,y);a.lineTo(x-s*.35f,y-s*.45f);a.lineTo(x-s*.18f,y);a.lineTo(x-s*.35f,y+s*.45f);a.close();c.drawPath(a,paint);c.restore();}

    private void drawToast(Canvas c,String msg){paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);float tw=paint.measureText(msg);RectF r=new RectF((getWidth()-tw)/2-dp(18),insetTop+dp(130),(getWidth()+tw)/2+dp(18),insetTop+dp(172));paint.setColor(Color.argb(225,31,38,50));c.drawRoundRect(r,dp(20),dp(20),paint);paint.setColor(Color.WHITE);c.drawText(msg,r.centerX(),r.centerY()+dp(5),paint);}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(paused)return true;
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            pressPiece=(screen==Screen.PLAY&&!tutorial&&!finished&&!failed)?findPieceAt(x,y):null;
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
            else if(tertiaryHit.contains(x,y)){if(!buyContinue())host.openWallet();}
            else if(walletHit.contains(x,y))host.openWallet();
            return true;
        }
        float top=insetTop+dp(8);
        if(x<dp(65)&&y<top+dp(60)){host.openHome();return true;}
        if(x>getWidth()-dp(65)&&y<top+dp(60)){host.openSettings();return true;}
        if(walletHit.contains(x,y)||rewardHit.contains(x,y)){host.openWallet();return true;}
        if(hintHit.contains(x,y)){useHint();return true;}
        if(eraseHit.contains(x,y)){useEraser();return true;}
        // A tap belongs to the arrow selected on ACTION_DOWN. Re-running hit testing
        // on ACTION_UP can switch to a neighbouring dense-grid arrow after normal finger
        // drift, causing a false blocked result and an incorrect heart penalty.
        Piece piece=(releasedPress!=null&&!releasedPress.removed&&!releasedPress.moving)
                ? releasedPress : findPieceAt(x,y);
        if(piece!=null)tapPiece(piece);return true;
    }

    private void startLevel(int lv) {
        dailyChallenge=false;weeklyChallenge=false;challengeDay=-1L;challengeWeek=-1L;
        setupLevel(lv,true);
    }

    private void setupLevel(int lv,boolean persistNormal) {
        level=Math.max(1,Math.min(MAX_LEVEL,lv));
        if(persistNormal){prefs.edit().putInt("lastLevel",level).apply();normalLevelBeforeChallenge=level;}
        screen=Screen.PLAY;settingsOpen=false;finished=false;failed=false;hearts=3;hints=2;erasers=1;
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
    private int weeklyLevelForWeek(long week){return 80+(int)Math.floorMod(week*97L+11L,25L)*5;}
    public int bestDailyStarsToday(){
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        return prefs.getLong("challenge_star_day",-1L)==day?prefs.getInt("challenge_best_stars",0):0;
    }
    public int bestWeeklyStarsThisWeek(){
        long week=(System.currentTimeMillis()/Wallet.DAY_MS)/7L;
        return prefs.getLong("weekly_challenge_star_week",-1L)==week?prefs.getInt("weekly_challenge_best_stars",0):0;
    }
    private void leaveChallenge(){int back=normalLevelBeforeChallenge;dailyChallenge=false;weeklyChallenge=false;challengeDay=-1L;challengeWeek=-1L;startLevel(back);host.openHome();}

    private boolean handleLevelsTouch(float x,float y) {
        float w=getWidth(),h=getHeight(),top=insetTop+dp(12);
        if(x<dp(70)&&y<top+dp(65)){screen=Screen.PLAY;host.openHome();invalidate();return true;}
        float gap=dp(10),left=dp(20),bw=(w-left*2-gap*3)/4f,bh=dp(62),y0=top+dp(78);
        if(x>=left&&y>=y0&&y<y0+5*(bh+gap)) {
            int col=(int)((x-left)/(bw+gap)),row=(int)((y-y0)/(bh+gap));
            if(col>=0&&col<4&&row>=0&&row<5) {
                int lv=levelPage*20+row*4+col+1;
                if(lv<=maxUnlocked&&lv<=MAX_LEVEL){startLevel(lv);return true;}
            }
        }
        if(y>h-insetBottom-dp(90)) {
            if(x<w/2&&levelPage>0)levelPage--;
            else if(x>=w/2&&levelPage<9)levelPage++;
            invalidate();
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

    public void grantEraser(){erasers++;saveProgress();showToast("Eraser added");invalidate();}
    public void grantRevive(){if(!failed)return;failed=false;hearts=2;assistsUsed++;saveProgress();showToast("Revived with 2 hearts");invalidate();}

    private int remaining(){int n=0;for(Piece p:pieces)if(!p.removed)n++;return n;}
    private int chapterNumber(int lv){if(lv<=20)return 1;if(lv<=40)return 2;if(lv<=60)return 3;if(lv<=80)return 4;if(lv<=100)return 5;if(lv<=140)return 6;if(lv<=180)return 7;return 8;}
    private String chapterName(int lv){switch(chapterNumber(lv)){case 1:return "First Escape";case 2:return "Twisted Paths";case 3:return "Tail Trouble";case 4:return "Locked Logic";case 5:return "Chain Reaction";case 6:return "Master Escape";case 7:return "Impossible Maze";default:return "Grandmaster";}}
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
    private boolean isSuperHard(int lv){return lv%5==0;}
    private boolean isBoss(int lv){return lv==25||lv==50||lv==100||lv==150||lv==200;}
    private String difficulty(){if(isBoss(level))return "BOSS MILESTONE";if(isSuperHard(level))return "SUPER HARD ★";if(level<=40)return "Hard";if(level<=120)return "Expert";return "Master";}
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

    private int chapterStart(int chapter){switch(chapter){case 1:return 1;case 2:return 21;case 3:return 41;case 4:return 61;case 5:return 81;case 6:return 101;case 7:return 141;default:return 181;}}
    private int chapterEnd(int chapter){switch(chapter){case 1:return 20;case 2:return 40;case 3:return 60;case 4:return 80;case 5:return 100;case 6:return 140;case 7:return 180;default:return 200;}}
    private int chapterStars(int chapter){int total=0;for(int lv=chapterStart(chapter);lv<=chapterEnd(chapter);lv++)total+=bestStars(lv);return total;}
    private int chapterCompleted(int chapter){int n=0;for(int lv=chapterStart(chapter);lv<=chapterEnd(chapter);lv++)if(bestStars(lv)>0)n++;return n;}
    private String chapterTrophyTier(int chapter){
        int count=chapterEnd(chapter)-chapterStart(chapter)+1,stars=chapterStars(chapter),done=chapterCompleted(chapter);
        if(stars==count*3)return "GOLD TROPHY";
        if(done==count&&stars>=count*2)return "SILVER TROPHY";
        if(done==count)return "BRONZE TROPHY";
        return "Trophy "+stars+"/"+(count*3)+"★";
    }
    private boolean allBossesCleared(){int[] bosses={25,50,100,150,200};for(int lv:bosses)if(bestStars(lv)==0)return false;return true;}
    private int completedLevels(){int n=0;for(int lv=1;lv<=MAX_LEVEL;lv++)if(bestStars(lv)>0)n++;return n;}
    private int perfectLevels(){int n=0;for(int lv=1;lv<=MAX_LEVEL;lv++)if(bestStars(lv)==3)n++;return n;}
    public int achievementCount(){
        int complete=completedLevels(),perfect=perfectLevels(),stars=totalStars(),n=0;
        if(complete>=1)n++;if(perfect>=1)n++;if(complete>=25)n++;if(complete>=60)n++;if(complete>=100)n++;
        if(complete>=140)n++;if(complete>=180)n++;if(complete>=200)n++;if(stars>=300)n++;if(perfect>=50)n++;if(allBossesCleared())n++;
        return n;
    }
    public int trophyCount(){int n=0;for(int ch=1;ch<=8;ch++)if(chapterCompleted(ch)==chapterEnd(ch)-chapterStart(ch)+1)n++;return n;}
    public String progressSummary(){return totalStars()+"/600★  ·  "+trophyCount()+"/8 chapter trophies  ·  "+achievementCount()+"/11 achievements";}
    public String progressDetails(){
        StringBuilder b=new StringBuilder("CHAPTER TROPHIES\n");
        for(int ch=1;ch<=8;ch++){int first=chapterStart(ch);b.append("CH ").append(ch).append(" · ").append(chapterName(first)).append(" — ").append(chapterTrophyTier(ch)).append("\n");}
        int complete=completedLevels(),perfect=perfectLevels(),stars=totalStars();
        b.append("\nACHIEVEMENTS\n");
        addAchievement(b,complete>=1,"First Escape","Clear your first maze");
        addAchievement(b,perfect>=1,"Perfect Start","Earn your first 3★");
        addAchievement(b,complete>=25,"Maze Explorer","Clear 25 levels");
        addAchievement(b,complete>=60,"Key Master","Clear 60 levels");
        addAchievement(b,complete>=100,"Ice Breaker","Clear 100 levels");
        addAchievement(b,complete>=140,"Gate Runner","Clear 140 levels");
        addAchievement(b,complete>=180,"Chain Master","Clear 180 levels");
        addAchievement(b,complete>=200,"Grandmaster","Clear all 200 levels");
        addAchievement(b,stars>=300,"Star Collector","Earn 300 stars");
        addAchievement(b,perfect>=50,"Perfectionist","Get 3★ on 50 levels");
        addAchievement(b,allBossesCleared(),"Boss Hunter","Clear all five boss milestones");
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
        } finally {
            packedLevels=normalPack;
        }
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
        if (level >= 81) {
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
        if (level >= 121) {
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
        if (level >= 161) {
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

    private boolean isClear(Piece target) {
        if(!specialUnlocked(target))return false;
        if(!hasSelfClearance(target))return false;

        HashSet<Long> nodes =
      new HashSet<>();

        HashSet<Long> edges =
      new HashSet<>();

        for (Piece p : pieces) {
  if (p == target || p.removed) continue;

  // A moving arrow blocks only where its snake body is visibly present now.
  // Cells already vacated by the animated tail must not cause false heart loss.
  if (p.moving) {
      addMovingOccupancy(p,nodes,edges);
  } else {
      nodes.addAll(p.nodes);
      edges.addAll(p.edges);
  }
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
    private void showLevelSelect(){screen=Screen.LEVELS;levelPage=(level-1)/20;saveProgress();invalidate();}
    public void openLevels(){showLevelSelect();}
    public void openPlay(){screen=Screen.PLAY;invalidate();}
    public int currentLevelNumber(){return level;}
    public int dailyPuzzleNumber(){return dailyChallengeNumberForDay(System.currentTimeMillis()/Wallet.DAY_MS);}
    public int totalStarCount(){return totalStars();}
    public int completedLevelCount(){return completedLevels();}
    public int perfectLevelCount(){return perfectLevels();}
    public boolean allBossesComplete(){return allBossesCleared();}
    public boolean handleBack(){
        if(screen==Screen.LEVELS){screen=Screen.PLAY;host.openHome();invalidate();return true;}
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
