package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
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
        void onContinueAfterWin(Runnable proceed);
    }

    private static final int MAX_LEVEL = 200;
    private static final String LEVEL_PACK_VERSION = "v17-chains-1";
    private static final int NAVY = Color.rgb(8, 29, 73);
    private static final int BLUE = Color.rgb(47, 149, 235);
    private static final int PALE = Color.rgb(242, 246, 252);
    private static final int DOT = Color.rgb(211, 219, 230);
    private static final int RED = Color.rgb(255, 76, 83);
    private static final int LOST_HEART = Color.rgb(229, 235, 243);
    private static final int TEXT = Color.rgb(20, 24, 31);
    private static final int SUPER_HARD = Color.rgb(218, 72, 55);
    private static final int BOSS_GOLD = Color.rgb(185, 116, 9);
    private static final int DAILY_PURPLE = Color.rgb(116, 78, 194);

    private enum Screen { PLAY, LEVELS }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Host host;
    private final SharedPreferences prefs;
    private final SharedPreferences settings;
    private final Wallet wallet;
    private boolean paused;
    private int mistakes, winReward, earnedStars, assistsUsed;
    private boolean dailyChallenge;
    private long challengeDay = -1L, milestoneIntroUntil = 0L;
    private int normalLevelBeforeChallenge = 1;
    private String runId;
    private final RectF walletHit = new RectF(), hintHit = new RectF(), eraseHit = new RectF(), rewardHit = new RectF();
    private final RectF primaryHit = new RectF(), secondaryHit = new RectF(), tertiaryHit = new RectF();
    private final Vibrator vibrator;
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 58);
    private final Random rng = new Random();
    private final ArrayList<Piece> pieces = new ArrayList<>();
    private String[] packedLevels;

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
    private int insetTop = 0, insetBottom = 0;
    private long lastFrame = 0L;
    private long touchRippleUntil = 0L;
    private float rippleX, rippleY;
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
        setBackgroundColor(Color.WHITE);
        setFocusable(true);
        loadPackedLevels();
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
        float speedPxPerSec =
                Math.max(getWidth() * 1.30f, cell * 9f);

        float travelSec =
                Math.max(0.30f,
                Math.min(1.45f,
                travelPx / speedPxPerSec));

        p.moveT += dt / travelSec;
                if (p.moveT >= 1f) {
                    p.moveT = 1f;
                    p.moving = false;
                    p.removed = true;
                } else any = true;
            }
            if (p.flashUntil > now || p.hintUntil > now) any = true;
        }
        if (!finished && !failed && remaining() == 0) {
            finished = true;
            earnedStars = computeStars();
            if (dailyChallenge) {
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
        float w = getWidth(), h = getHeight(), top = insetTop + dp(8);
        drawBack(c, dp(28), top + dp(30));
        String title = dailyChallenge ? "Daily Challenge" : (isBoss(level) ? "Boss Level " + level : "Level " + level);
        label(c, title, w/2, top + dp(29), 23, NAVY, true);
        int difficultyColor = dailyChallenge ? DAILY_PURPLE : (isBoss(level) ? BOSS_GOLD : isSuperHard(level) ? SUPER_HARD : Color.rgb(100,116,139));
        String sub = dailyChallenge ? "SUPER HARD DAILY  ·  puzzle " + level : difficulty() + "  ·  " + level + " / 200";
        label(c, sub, w/2, top + dp(49), 11, difficultyColor, dailyChallenge || isSuperHard(level));
        drawGear(c, w-dp(31), top+dp(30));
        float statY = top + dp(84);
        pill(c,new RectF(dp(16),statY-dp(21),dp(94),statY+dp(21)),PALE);
        drawMiniArrow(c,dp(32),statY,dp(12),NAVY,-45);
        label(c,String.valueOf(remaining()),dp(65),statY+dp(6),18,NAVY,true);
        for(int i=0;i<3;i++) drawHeart(c,w/2-dp(30)+i*dp(30),statY,dp(11),i<hearts?RED:LOST_HEART);
        walletHit.set(w-dp(112),statY-dp(21),w-dp(16),statY+dp(21));
        pill(c,walletHit,Color.rgb(255,247,224)); drawCoin(c,walletHit.left+dp(19),statY,dp(10));
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
        c.save();c.clipRect(dp(8),areaTop,w-dp(8),areaBottom);drawDottedGrid(c);drawPieces(c,now);c.restore();
        pill(c,hintHit,PALE);pill(c,eraseHit,PALE);pill(c,rewardHit,Color.rgb(255,247,224));
        drawBulb(c,hintHit.centerX(),controlY-dp(8),dp(12));
        label(c,hints>0?"Hint · "+hints+" free":"Hint · 25 coins",hintHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawEraser(c,eraseHit.centerX(),controlY-dp(8),dp(12));
        label(c,erasers>0?"Erase · "+erasers+" free":"Erase · watch ad",eraseHit.centerX(),controlY+dp(18),11,NAVY,true);
        drawCoin(c,rewardHit.centerX(),controlY-dp(8),dp(11));
        label(c,"Get coins",rewardHit.centerX(),controlY+dp(18),11,NAVY,true);
        label(c,"ARROW ESCAPE  /  PUZZLE MAZE",w/2,h-insetBottom-dp(10),9,Color.rgb(115,130,153),false);
        if(tutorial)drawTutorial(c);else if(finished)drawWin(c);else if(failed)drawFail(c);else if(milestoneIntroUntil>now)drawMilestoneIntro(c);
    }

    private void drawDottedGrid(Canvas c) {
        paint.setColor(DOT);
        for (int y=0; y<=gridH; y++) {
            float py = boardTop + y*cell;
            for (int x=0; x<=gridW; x++) {
                c.drawCircle(boardLeft+x*cell, py, Math.max(0.8f, cell*0.045f), paint);
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

    private void drawArrowHeadAt(
  Canvas c,
  float gx,
  float gy,
  int dx,
  int dy,
  int col,
  int alpha
    ) {
        float tx = boardLeft + gx * cell;
        float ty = boardTop + gy * cell;
        drawClearArrowHead(c, tx, ty, dx, dy, col, alpha);
    }

    /**
     * Draws a recognisable arrow-head rather than a plain triangle.
     * The short neck overlaps the route line so the head reads as part of
     * the arrow even on dense boards and while the snake is moving.
     */
    private void drawClearArrowHead(Canvas c, float tx, float ty, int dx, int dy, int col, int alpha) {
        float s = Math.max(dp(6.2f), cell * 0.34f);
        float px = -dy;
        float py = dx;

        Path a = new Path();
        a.moveTo(tx + dx*s*0.78f, ty + dy*s*0.78f);                         // sharp tip
        a.lineTo(tx - dx*s*0.50f + px*s*0.72f, ty - dy*s*0.50f + py*s*0.72f); // upper wing
        a.lineTo(tx - dx*s*0.28f + px*s*0.25f, ty - dy*s*0.28f + py*s*0.25f); // upper neck
        a.lineTo(tx - dx*s*0.92f + px*s*0.25f, ty - dy*s*0.92f + py*s*0.25f); // neck back
        a.lineTo(tx - dx*s*0.92f - px*s*0.25f, ty - dy*s*0.92f - py*s*0.25f);
        a.lineTo(tx - dx*s*0.28f - px*s*0.25f, ty - dy*s*0.28f - py*s*0.25f); // lower neck
        a.lineTo(tx - dx*s*0.50f - px*s*0.72f, ty - dy*s*0.50f - py*s*0.72f); // lower wing
        a.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)));
        c.drawPath(a, paint);
    }

    private void drawPieces(Canvas c, long now) {
        float stroke =
      Math.max(dp(2.2f), cell * 0.12f);

        for (Piece p : pieces) {
  if (p.removed) continue;

  float shake = 0f;

  if (p.flashUntil > now) {
      shake =
              (float)Math.sin(now * 0.085)
              * dp(3.5f);
  }

  int col =
          p.flashUntil > now
          ? Color.rgb(228, 65, 71)
          : (
              p.hintUntil > now
              ? Color.rgb(236, 167, 28)
              : (settings.getBoolean("contrast",false)?Color.BLACK:NAVY)
          );

  int alpha = 255;

  paint.setColor(col);
  paint.setStyle(Paint.Style.STROKE);
  paint.setStrokeWidth(stroke);
  paint.setStrokeCap(Paint.Cap.SQUARE);
  paint.setStrokeJoin(Paint.Join.ROUND);

  Path path = new Path();

  if (p.moving) {

      float advance =
              p.moveT
              * p.moveSteps;

      buildMovingSnakePath(
              path,
              p,
              advance
      );

      c.drawPath(path, paint);

      float total =
              piecePathLength(p);

      android.graphics.PointF head =
              routePoint(
                      p,
                      advance + total
              );

      paint.setStyle(Paint.Style.FILL);

      drawArrowHeadAt(
              c,
              head.x,
              head.y,
              p.dx,
              p.dy,
              col,
              alpha
      );

  } else {

      for (int i = 0;
           i < p.pts.size();
           i++) {

          Point q = p.pts.get(i);

          float px =
                  boardLeft
                  + q.x * cell
                  + (p.dy != 0 ? shake : 0);

          float py =
                  boardTop
                  + q.y * cell
                  + (p.dx != 0 ? shake : 0);

          if (i == 0) {
              path.moveTo(px, py);
          } else {
              path.lineTo(px, py);
          }
      }

      c.drawPath(path, paint);

      paint.setStyle(Paint.Style.FILL);

      drawArrowHead(
              c,
              p,
              0f,
              0f,
              shake,
              col,
              alpha
      );
  }
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawArrowHead(Canvas c, Piece p, float sx, float sy, float shake, int col, int alpha) {
        Point tip = p.pts.get(p.pts.size()-1);
        float tx = boardLeft + tip.x*cell + sx + (p.dy!=0?shake:0);
        float ty = boardTop + tip.y*cell + sy + (p.dx!=0?shake:0);
        drawClearArrowHead(c, tx, ty, p.dx, p.dy, col, alpha);
    }

    private void drawMilestoneIntro(Canvas c) {
        RectF r=modal(c,dailyChallenge?250:270);
        int accent=dailyChallenge?DAILY_PURPLE:(isBoss(level)?BOSS_GOLD:SUPER_HARD);
        label(c,dailyChallenge?"DAILY CHALLENGE":(isBoss(level)?"BOSS MILESTONE":"SUPER HARD"),r.centerX(),r.top+dp(52),28,accent,true);
        label(c,dailyChallenge?"Same challenge for everyone today":"Level "+level+" is a milestone",r.centerX(),r.top+dp(89),15,NAVY,true);
        label(c,isBoss(level)&&!dailyChallenge?"The toughest checkpoint in this chapter":"Expect deeper blocking chains and fewer obvious exits.",r.centerX(),r.top+dp(121),13,TEXT,false);
        label(c,dailyChallenge?"Reward: 100–150 coins based on stars":(isBoss(level)?"Boss bonus: +150 coins":"Milestone bonus: +75 coins"),r.centerX(),r.top+dp(153),13,accent,true);
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
        c.drawColor(NAVY);
        float w=getWidth(),h=getHeight();
        paint.setColor(Color.argb(18,255,255,255));
        c.drawCircle(w*.85f,h*.12f,dp(145),paint);c.drawCircle(w*.05f,h*.80f,dp(125),paint);
        float top=Math.max(insetTop+dp(18),(h-dp(520))/2);
        String headline=dailyChallenge?"Daily challenge cleared!":(level==200?"All 200 mazes cleared!":isBoss(level)?"Boss escaped!":"Maze escaped!");
        label(c,headline,w/2,top+dp(32),26,Color.WHITE,true);
        label(c,dailyChallenge?"Puzzle of the day complete":"Level "+level+" complete",w/2,top+dp(61),16,Color.rgb(176,206,246),false);
        label(c,starString(earnedStars),w/2,top+dp(108),31,Color.rgb(255,204,57),true);
        label(c,earnedStars==3?"Perfect clear":earnedStars==2?"Strong clear":"Cleared",w/2,top+dp(136),13,Color.rgb(205,220,242),false);
        RectF card=new RectF(dp(26),top+dp(166),w-dp(26),top+dp(326));pill(c,card,Color.WHITE);
        label(c,winReward>0?"+"+winReward+" coins":"Reward already claimed",w/2,card.top+dp(38),26,NAVY,true);
        String rewardLine;
        if(dailyChallenge) rewardLine="Daily challenge · "+(earnedStars==3?"+150":earnedStars==2?"+125":"+100");
        else if(isBoss(level)) rewardLine="Clear +15 · Star bonus · Boss bonus +150";
        else if(isSuperHard(level)) rewardLine="Clear +15 · Star bonus · Milestone +75";
        else rewardLine="Clear +15 · 2★ +5 · 3★ +10";
        label(c,rewardLine,w/2,card.top+dp(72),13,TEXT,false);
        label(c,dailyChallenge?"Replay anytime to improve your stars":(earnedStars==3?"Best rating saved":"Replay later to earn 3★"),w/2,card.top+dp(101),13,Color.rgb(111,124,145),false);
        label(c,"Wallet: "+wallet.balance()+" coins",w/2,card.top+dp(132),13,NAVY,true);
        primaryHit.set(dp(26),top+dp(347),w-dp(26),top+dp(399));
        secondaryHit.set(dp(26),top+dp(411),w-dp(26),top+dp(461));
        tertiaryHit.set(dp(26),top+dp(466),w-dp(26),top+dp(504));
        action(c,primaryHit,dailyChallenge?"Back to levels":(level<200?"Next level":"View all levels"),BLUE,Color.WHITE);
        action(c,secondaryHit,"Watch ad · +75 coins",Color.rgb(255,202,69),NAVY);
        label(c,dailyChallenge?"Replay from Daily Challenge menu":"Level select",w/2,tertiaryHit.centerY()+dp(5),13,Color.WHITE,false);
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
        action(c,tertiaryHit,"Continue · 60 coins",Color.rgb(255,247,224),NAVY);
        walletHit.set(r.left+dp(20),r.top+dp(291),r.right-dp(20),r.bottom-dp(8));
        label(c,"Wallet: "+wallet.balance()+"  ·  Get coins",r.centerX(),walletHit.centerY()+dp(5),13,NAVY,false);
    }

    private void drawLevels(Canvas c) {
        float w=getWidth(),h=getHeight(),top=insetTop+dp(12);
        paint.setColor(TEXT);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(27));paint.setFakeBoldText(true);c.drawText("Levels",w/2,top+dp(31),paint);paint.setFakeBoldText(false);
        label(c,totalStars()+" / 600 stars",w/2,top+dp(55),12,Color.rgb(100,116,139),false);
        drawBack(c,dp(28),top+dp(28));
        int start=levelPage*20+1;
        float gap=dp(10), left=dp(20), bw=(w-left*2-gap*3)/4f, bh=dp(66), y0=top+dp(82);
        for(int i=0;i<20;i++){
            int lv=start+i;if(lv>MAX_LEVEL)break;int col=i%4,row=i/4;RectF r=new RectF(left+col*(bw+gap),y0+row*(bh+gap),left+col*(bw+gap)+bw,y0+row*(bh+gap)+bh);
            boolean open=lv<=maxUnlocked, milestone=isSuperHard(lv), boss=isBoss(lv);
            int cardColor=!open?Color.rgb(244,246,249):(boss?Color.rgb(255,232,176):milestone?Color.rgb(255,240,207):(lv==level?Color.rgb(223,240,255):PALE));
            paint.setColor(cardColor);c.drawRoundRect(r,dp(16),dp(16),paint);
            if(milestone){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(boss?2.7f:2));paint.setColor(open?(boss?BOSS_GOLD:SUPER_HARD):Color.rgb(210,190,170));c.drawRoundRect(r,dp(16),dp(16),paint);paint.setStyle(Paint.Style.FILL);}
            int stars=open?bestStars(lv):0;
            paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(true);paint.setTextSize(dp(18));paint.setColor(open?(boss?BOSS_GOLD:milestone?SUPER_HARD:TEXT):Color.rgb(171,180,194));c.drawText(open?String.valueOf(lv):"•",r.centerX(),r.top+dp(30),paint);paint.setFakeBoldText(false);
            if(open&&stars>0)label(c,starString(stars),r.centerX(),r.bottom-dp(8),9,Color.rgb(225,161,20),true);
            if(milestone)label(c,boss?"BOSS":"★",r.right-dp(boss?22:13),r.top+dp(15),boss?7:10,open?(boss?BOSS_GOLD:SUPER_HARD):Color.rgb(190,180,170),true);
        }
        float y=h-insetBottom-dp(72);RectF prev=new RectF(dp(24),y,w*.45f,y+dp(48));RectF next=new RectF(w*.55f,y,w-dp(24),y+dp(48));
        paint.setColor(PALE);c.drawRoundRect(prev,dp(22),dp(22),paint);c.drawRoundRect(next,dp(22),dp(22),paint);paint.setColor(TEXT);paint.setTextSize(dp(15));paint.setFakeBoldText(true);c.drawText("‹ PREV",prev.centerX(),prev.centerY()+dp(5),paint);c.drawText("NEXT ›",next.centerX(),next.centerY()+dp(5),paint);paint.setFakeBoldText(false);
    }

    private void drawBack(Canvas c,float x,float y){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3.5f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(BLUE);Path p=new Path();p.moveTo(x+dp(8),y-dp(13));p.lineTo(x-dp(5),y);p.lineTo(x+dp(8),y+dp(13));c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}

    private void drawGear(Canvas c,float x,float y){paint.setColor(BLUE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));c.drawCircle(x,y,dp(10),paint);c.drawCircle(x,y,dp(4),paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=(float)(x+Math.cos(a)*dp(12)),y1=(float)(y+Math.sin(a)*dp(12));float x2=(float)(x+Math.cos(a)*dp(16)),y2=(float)(y+Math.sin(a)*dp(16));c.drawLine(x1,y1,x2,y2,paint);}paint.setStyle(Paint.Style.FILL);}

    private void drawHeart(Canvas c,float x,float y,float s,int color){Path p=new Path();p.moveTo(x,y+s*.9f);p.cubicTo(x-s*1.25f,y+s*.1f,x-s*1.15f,y-s*.75f,x-s*.55f,y-s*.85f);p.cubicTo(x-s*.1f,y-s*.95f,x,y-s*.55f,x,y-s*.35f);p.cubicTo(x,y-s*.55f,x+s*.1f,y-s*.95f,x+s*.55f,y-s*.85f);p.cubicTo(x+s*1.15f,y-s*.75f,x+s*1.25f,y+s*.1f,x,y+s*.9f);p.close();paint.setColor(color);c.drawPath(p,paint);}

    private void pill(Canvas c,RectF r,int color){paint.setColor(color);c.drawRoundRect(r,dp(16),dp(16),paint);}

    private void drawRoundControl(Canvas c,float x,float y,float r){paint.setColor(Color.rgb(239,243,248));c.drawCircle(x,y,r+dp(3),paint);paint.setColor(Color.WHITE);c.drawCircle(x,y,r,paint);}

    private void drawBulb(Canvas c,float x,float y,float s){paint.setColor(Color.rgb(255,193,30));c.drawCircle(x,y-s*.25f,s*.62f,paint);paint.setColor(Color.rgb(255,218,72));c.drawCircle(x-s*.2f,y-s*.45f,s*.2f,paint);paint.setColor(Color.rgb(139,157,191));RectF base=new RectF(x-s*.35f,y+s*.30f,x+s*.35f,y+s*.82f);c.drawRoundRect(base,s*.13f,s*.13f,paint);paint.setColor(Color.rgb(95,113,151));c.drawRect(x-s*.28f,y+s*.62f,x+s*.28f,y+s*.78f,paint);}

    private void drawEraser(Canvas c,float x,float y,float s){c.save();c.rotate(-28,x,y);RectF body=new RectF(x-s*.55f,y-s*.82f,x+s*.55f,y+s*.82f);paint.setColor(Color.rgb(107,82,224));c.drawRoundRect(body,s*.18f,s*.18f,paint);RectF stripe=new RectF(body.left+s*.12f,body.top+s*.23f,body.right-s*.12f,body.top+s*.42f);paint.setColor(Color.rgb(159,140,244));c.drawRoundRect(stripe,s*.08f,s*.08f,paint);paint.setColor(Color.rgb(229,232,245));c.drawRoundRect(new RectF(body.left,body.bottom-s*.23f,body.right,body.bottom),s*.12f,s*.12f,paint);c.restore();}

    private void drawMiniArrow(Canvas c,float x,float y,float s,int color,float degrees){c.save();c.rotate(degrees,x,y);paint.setColor(color);Path a=new Path();a.moveTo(x+s*.7f,y);a.lineTo(x-s*.35f,y-s*.45f);a.lineTo(x-s*.18f,y);a.lineTo(x-s*.35f,y+s*.45f);a.close();c.drawPath(a,paint);c.restore();}

    private void drawToast(Canvas c,String msg){paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);float tw=paint.measureText(msg);RectF r=new RectF((getWidth()-tw)/2-dp(18),insetTop+dp(130),(getWidth()+tw)/2+dp(18),insetTop+dp(172));paint.setColor(Color.argb(225,31,38,50));c.drawRoundRect(r,dp(20),dp(20),paint);paint.setColor(Color.WHITE);c.drawText(msg,r.centerX(),r.centerY()+dp(5),paint);}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(paused)return true;
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();
        float x=e.getX(),y=e.getY();rippleX=x;rippleY=y;touchRippleUntil=SystemClock.elapsedRealtime()+360;invalidate();
        if(screen==Screen.LEVELS)return handleLevelsTouch(x,y);
        if(milestoneIntroUntil>SystemClock.elapsedRealtime()){milestoneIntroUntil=0L;invalidate();return true;}
        if(tutorial){if(primaryHit.contains(x,y)){tutorial=false;prefs.edit().putBoolean("tutorialSeen",true).apply();invalidate();}return true;}
        if(finished){
            if(primaryHit.contains(x,y)){
                if(dailyChallenge)leaveDailyChallenge();
                else host.onContinueAfterWin(()->{if(level<MAX_LEVEL)startLevel(level+1);else showLevelSelect();});
            } else if(secondaryHit.contains(x,y))host.requestRewardedCoins();
            else if(tertiaryHit.contains(x,y)){if(dailyChallenge)leaveDailyChallenge();else showLevelSelect();}
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
        if(x<dp(65)&&y<top+dp(60)){showLevelSelect();return true;}
        if(x>getWidth()-dp(65)&&y<top+dp(60)){host.openSettings();return true;}
        if(walletHit.contains(x,y)||rewardHit.contains(x,y)){host.openWallet();return true;}
        if(hintHit.contains(x,y)){useHint();return true;}
        if(eraseHit.contains(x,y)){useEraser();return true;}
        Piece piece=findPieceAt(x,y);if(piece!=null)tapPiece(piece);return true;
    }

    private void startLevel(int lv) {
        dailyChallenge=false;challengeDay=-1L;
        setupLevel(lv,true);
    }

    private void setupLevel(int lv,boolean persistNormal) {
        level=Math.max(1,Math.min(MAX_LEVEL,lv));
        if(persistNormal){prefs.edit().putInt("lastLevel",level).apply();normalLevelBeforeChallenge=level;}
        screen=Screen.PLAY;settingsOpen=false;finished=false;failed=false;hearts=3;hints=2;erasers=1;
        mistakes=0;assistsUsed=0;earnedStars=0;winReward=0;runId=java.util.UUID.randomUUID().toString();lastFrame=0;
        generateLevel(level);
        milestoneIntroUntil=(dailyChallenge||isSuperHard(level))?SystemClock.elapsedRealtime()+1600L:0L;
        saveProgress();invalidate();
    }

    public void startDailyChallenge() {
        normalLevelBeforeChallenge=Math.max(1,Math.min(MAX_LEVEL,prefs.getInt("lastLevel",level)));
        challengeDay=System.currentTimeMillis()/Wallet.DAY_MS;
        dailyChallenge=true;
        setupLevel(dailyLevelForDay(challengeDay),false);
    }

    private int dailyLevelForDay(long day){return 5+(int)Math.floorMod(day*73L+19L,40L)*5;}
    public int bestDailyStarsToday(){
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        return prefs.getLong("challenge_star_day",-1L)==day?prefs.getInt("challenge_best_stars",0):0;
    }
    private void leaveDailyChallenge(){int back=normalLevelBeforeChallenge;dailyChallenge=false;challengeDay=-1L;startLevel(back);showLevelSelect();}

    private boolean handleLevelsTouch(float x,float y) {
        float w=getWidth(),h=getHeight(),top=insetTop+dp(12);
        if(x<dp(70)&&y<top+dp(65)){screen=Screen.PLAY;invalidate();return true;}
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
        boolean selfBlocked = !hasSelfClearance(p);
        if(isClear(p)) {
            if(settings.getBoolean("haptics",true))performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            p.moving=true;p.moveT=0;p.moveSteps=snakeTravelSteps(p);
            playSound(ToneGenerator.TONE_PROP_BEEP,70);buzz(20);
        } else {
            p.flashUntil=SystemClock.elapsedRealtime()+390;hearts--;mistakes++;
            playSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,110);buzz(55);
            showToast(selfBlocked ? "Tail blocks this escape" : "Blocked by another arrow");
            if(hearts<=0)failed=true;
        }
        saveProgress();invalidate();
    }

    private void useHint() {
        long now=SystemClock.elapsedRealtime();ArrayList<Piece> safe=new ArrayList<>();
        for(Piece p:pieces){if(!p.removed&&!p.moving&&p.hintUntil>now)return;if(!p.removed&&!p.moving&&isClear(p))safe.add(p);}
        if(safe.isEmpty()){showToast("Wait for the moving arrows");return;}
        if(hints>0)hints--;else if(!wallet.spend(Wallet.HINT_COST)){host.openWallet();return;}
        assistsUsed++;
        Piece p=safe.get(rng.nextInt(safe.size()));p.hintUntil=now+2400;
        saveProgress();showToast("Safe arrow highlighted");invalidate();
    }

    private void useEraser(){if(erasers<=0){host.requestRewardedErase();return;}ArrayList<Piece> active=new ArrayList<>();for(Piece p:pieces)if(!p.removed&&!p.moving)active.add(p);if(active.isEmpty())return;Piece p=active.get(rng.nextInt(active.size()));erasers--;assistsUsed++;p.moving=true;p.moveT=0;p.moveSteps=snakeTravelSteps(p);showToast("One arrow removed");saveProgress();invalidate();}

    public void grantEraser(){erasers++;saveProgress();showToast("Eraser added");invalidate();}
    public void grantRevive(){if(!failed)return;failed=false;hearts=2;assistsUsed++;saveProgress();showToast("Revived with 2 hearts");invalidate();}

    private int remaining(){int n=0;for(Piece p:pieces)if(!p.removed)n++;return n;}
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

    private boolean isClear(Piece target) {
        if(!hasSelfClearance(target))return false;

        HashSet<Long> nodes =
      new HashSet<>();

        HashSet<Long> edges =
      new HashSet<>();

        for (Piece p : pieces) {
  // Reserve a moving arrow's route until its tail has fully exited. Otherwise
  // rapid taps can release a dependent arrow into a body still on the board.
  if (p == target
          || p.removed) {
      continue;
  }

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
    public void restartCurrentLevel(){if(dailyChallenge)setupLevel(level,false);else startLevel(level);}
    public void showTutorialAgain(){screen=Screen.PLAY;tutorial=true;invalidate();}
    private void showLevelSelect(){screen=Screen.LEVELS;levelPage=(level-1)/20;saveProgress();invalidate();}
    public boolean handleBack(){if(screen==Screen.LEVELS){screen=Screen.PLAY;invalidate();return true;}if(tutorial){tutorial=false;prefs.edit().putBoolean("tutorialSeen",true).apply();invalidate();return true;}if(dailyChallenge){leaveDailyChallenge();return true;}host.openSettings();return true;}
    public void saveProgress(){
        if(runId==null)return;
        try {
            org.json.JSONObject state=new org.json.JSONObject();org.json.JSONArray removed=new org.json.JSONArray();
            for(Piece p:pieces)if(p.removed||p.moving)removed.put(p.id);
            state.put("packVersion",LEVEL_PACK_VERSION).put("level",level).put("run",runId).put("hearts",hearts).put("hints",hints).put("erasers",erasers)
                 .put("mistakes",mistakes).put("assists",assistsUsed).put("earnedStars",earnedStars)
                 .put("dailyChallenge",dailyChallenge).put("challengeDay",challengeDay).put("normalLevel",normalLevelBeforeChallenge)
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
            boolean savedDaily=state.optBoolean("dailyChallenge",false);
            if(savedDaily){dailyChallenge=true;challengeDay=state.optLong("challengeDay",System.currentTimeMillis()/Wallet.DAY_MS);normalLevelBeforeChallenge=state.optInt("normalLevel",prefs.getInt("lastLevel",1));setupLevel(savedLevel,false);}
            else startLevel(savedLevel);
            runId=state.getString("run");hearts=Math.max(0,Math.min(3,state.getInt("hearts")));
            hints=Math.max(0,Math.min(2,state.getInt("hints")));erasers=Math.max(0,state.getInt("erasers"));mistakes=state.optInt("mistakes",0);
            assistsUsed=state.optInt("assists",0);earnedStars=state.optInt("earnedStars",0);
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
        paint.setColor(Color.argb(170,8,29,73));c.drawRect(0,0,getWidth(),getHeight(),paint);
        float top=Math.max(insetTop+dp(12),(getHeight()-dp(height))/2);
        RectF r=new RectF(dp(20),top,getWidth()-dp(20),top+dp(height));pill(c,r,Color.WHITE);return r;
    }
    private void action(Canvas c,RectF r,String value,int background,int foreground){pill(c,r,background);label(c,value,r.centerX(),r.centerY()+dp(5),15,foreground,true);}
}
