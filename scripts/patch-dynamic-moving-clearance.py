from pathlib import Path

src = Path('app/src/main/java/com/arrowescape/pro/ArrowGameView.java')
s = src.read_text()

helper = '''    private void addMovingOccupancy(Piece p,HashSet<Long> nodes,HashSet<Long> edges) {
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

'''
marker = '    private boolean isClear(Piece target) {'
if 'private void addMovingOccupancy(' not in s:
    if marker not in s:
        raise SystemExit('isClear marker not found')
    s = s.replace(marker, helper + marker, 1)

old = '''        for (Piece p : pieces) {
  // Reserve a moving arrow's route until its tail has fully exited. Otherwise
  // rapid taps can release a dependent arrow into a body still on the board.
  if (p == target
          || p.removed
          || p.moving) {
      continue;
  }

  nodes.addAll(p.nodes);
  edges.addAll(p.edges);
        }
'''
new = '''        for (Piece p : pieces) {
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
'''
if old not in s:
    raise SystemExit('broad moving-arrow skip block not found')
src.write_text(s.replace(old,new,1))

test = Path('app/src/androidTest/java/com/arrowescape/pro/V23TouchRegressionTest.java')
t = test.read_text()
old_test = '''            // Real gameplay state after the visible blockers have been tapped:
            // they are animating out (moving=true) but are not removed yet.
            set(blockerA,"moving",true); set(blockerA,"moveT",0.45f);
            set(blockerB,"moving",true); set(blockerB,"moveT",0.45f);

            assertTrue("Accepted/moving arrows must not keep a visually clear lane blocked",clear(g,target));
'''
new_test = '''            // Match real gameplay: moving arrows retain their full snake body until
            // their animated tail actually vacates a cell.
            int stepsA=(Integer)call(g,"snakeTravelSteps",new Class[]{blockerA.getClass()},blockerA);
            int stepsB=(Integer)call(g,"snakeTravelSteps",new Class[]{blockerB.getClass()},blockerB);
            set(blockerA,"moving",true); set(blockerA,"moveSteps",stepsA); set(blockerA,"moveT",0f);
            set(blockerB,"moving",true); set(blockerB,"moveSteps",stepsB); set(blockerB,"moveT",0f);
            assertFalse("Moving bodies must still block while visibly occupying the lane",clear(g,target));

            set(blockerA,"moveT",0.90f);
            set(blockerB,"moveT",0.90f);
            assertTrue("Once moving tails visibly clear the lane, the target must be allowed",clear(g,target));
'''
if old_test not in t:
    raise SystemExit('moving regression test block not found')
test.write_text(t.replace(old_test,new_test,1))

print('dynamic moving occupancy patch applied')
