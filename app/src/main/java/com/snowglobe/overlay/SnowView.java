package com.snowglobe.overlay;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.View;
import android.view.Choreographer;
import java.util.Random;

public class SnowView extends View {
 private final Paint p=new Paint(3);
 private final SharedPreferences prefs;
 private final Random rng=new Random(2026);
 private final float[] x=new float[100], y=new float[100], drift=new float[100], velocity=new float[100], scale=new float[100];
 private final float[] angle=new float[100], spin=new float[100], wind=new float[100];
 private long last=0L; private float clock=0;
 private boolean framePosted=false;
 private final Choreographer.FrameCallback frameCallback=new Choreographer.FrameCallback(){
  @Override public void doFrame(long frameTimeNanos){framePosted=false;if(!isShown())return;invalidate();scheduleFrame();}
 };
 private void scheduleFrame(){if(!framePosted&&isShown()){framePosted=true;Choreographer.getInstance().postFrameCallback(frameCallback);}}
 private void cancelFrame(){if(framePosted){Choreographer.getInstance().removeFrameCallback(frameCallback);framePosted=false;}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();last=0L;scheduleFrame();}
 @Override protected void onDetachedFromWindow(){cancelFrame();super.onDetachedFromWindow();}
 private static final int[][] COLORS={{0xfff44336,0xff42d66c,0xffffd65b,0xff50bbff},{0xff66d8ff,0xffdffaff,0xffaabaff,0xffffffff},{0xffffcc58,0xffffa15a,0xffffedb0,0xffffffff},{0xffff5050,0xff47e879,0xffffd45a,0xff54bfff,0xffff77d6}};
 public SnowView(Context c,SharedPreferences settings){super(c);prefs=settings;setLayerType(View.LAYER_TYPE_HARDWARE,null);for(int i=0;i<x.length;i++)reset(i,true);}
 @Override protected void onVisibilityChanged(View changedView,int visibility){super.onVisibilityChanged(changedView,visibility);last=0L;if(visibility==View.VISIBLE)scheduleFrame();else cancelFrame();}
 private void reset(int i,boolean initial){x[i]=rng.nextFloat();y[i]=initial?rng.nextFloat():-.03f-rng.nextFloat()*.25f;velocity[i]=.45f+rng.nextFloat()*1.3f;scale[i]=.65f+rng.nextFloat()*1.05f;drift[i]=rng.nextFloat()*6.28f;angle[i]=rng.nextFloat()*360f;spin[i]=(rng.nextFloat()*2f-1f)*(45f+rng.nextFloat()*95f);wind[i]=rng.nextFloat()*2f-1f;}
 private void color(int value){p.setStyle(Paint.Style.FILL);p.setColor(value);p.setAlpha(255);p.clearShadowLayer();}
 private void stroke(int value,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(value);p.setAlpha(255);p.clearShadowLayer();}
 private void snowflake(Canvas c,float cx,float cy,float r,int style){color(0xfff4fbff);if(style==0){c.drawCircle(cx,cy,r*.5f,p);return;}if(style==2){c.drawCircle(cx,cy,r*.35f,p);}stroke(0xfff4fbff,Math.max(1.2f,r*.19f));for(int a=0;a<6;a++){double angle=a*Math.PI/3;float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);c.drawLine(cx-dx*r,cy-dy*r,cx+dx*r,cy+dy*r,p);if(style==1){float bx=cx+dx*r*.65f,by=cy+dy*r*.65f;float px=-dy,py=dx;c.drawLine(bx,by,bx-dx*r*.22f+px*r*.18f,by-dy*r*.22f+py*r*.18f,p);c.drawLine(bx,by,bx-dx*r*.22f-px*r*.18f,by-dy*r*.22f-py*r*.18f,p);}}}
 private void ornament(Canvas c,int type,float px,float py,float s){c.save();c.translate(px,py);c.scale(s,s);switch(type){
 case 0: // candy cane
 stroke(0xffffffff,5);Path cane=new Path();cane.moveTo(-3,10);cane.lineTo(-3,-7);cane.cubicTo(-3,-17,11,-17,11,-7);cane.lineTo(11,-3);c.drawPath(cane,p);stroke(0xffed3547,3);for(int j=0;j<4;j++)c.drawLine(-6,7-j*5,0,4-j*5,p);c.drawLine(5,-14,9,-10,p);break;
 case 1: // gift
 color(0xffe83b50);c.drawRoundRect(-11,-7,11,10,2,2,p);color(0xffffd96d);c.drawRect(-2,-7,2,10,p);c.drawRect(-11,-2,11,2,p);stroke(0xffffd96d,2);c.drawOval(-9,-14,0,-6,p);c.drawOval(0,-14,9,-6,p);break;
 case 2: // tree
 color(0xff34b96a);Path tree=new Path();tree.moveTo(0,-15);tree.lineTo(-12,7);tree.lineTo(12,7);tree.close();c.drawPath(tree,p);color(0xff885331);c.drawRect(-3,7,3,13,p);color(0xffffd761);c.drawCircle(0,-15,3,p);break;
 case 3: // snowman
 color(0xffffffff);c.drawCircle(0,5,9,p);c.drawCircle(0,-8,7,p);color(0xff222d42);c.drawCircle(-3,-9,1,p);c.drawCircle(3,-9,1,p);color(0xffffa43d);Path nose=new Path();nose.moveTo(0,-6);nose.lineTo(7,-4);nose.lineTo(0,-3);nose.close();c.drawPath(nose,p);color(0xffe94955);c.drawRect(-7,-2,7,1,p);break;
 case 4: // Christmas bulb
 color(0xff54cfff);c.drawOval(-8,-8,8,10,p);color(0xffd9dfe7);c.drawRect(-5,-13,5,-7,p);stroke(0xffe5efff,1.5f);c.drawLine(0,-16,0,-13,p);break;
 case 5: // star
 color(0xffffdb6b);Path star=new Path();for(int j=0;j<10;j++){double a=-Math.PI/2+j*Math.PI/5;float r=j%2==0?13:6;float xx=(float)Math.cos(a)*r, yy=(float)Math.sin(a)*r;if(j==0)star.moveTo(xx,yy);else star.lineTo(xx,yy);}star.close();c.drawPath(star,p);break;
 default: // festive confetti
 color(0xffff78b6);c.rotate(25);c.drawRoundRect(-4,-9,4,9,2,2,p);
 }c.restore();}
 private void lights(Canvas c,int w,int[] colors,int design,float size){int count=Math.max(7,(int)(w/(42f*size)));float step=w/(float)count;float base=23*size;stroke(0xff255b43,Math.max(2,2.5f*size));Path wire=new Path();wire.moveTo(0,base);for(int i=0;i<=count;i++){float xx=i*step;float yy=base+(design==0?13:design==1?29:19)*size*(float)Math.sin(i*Math.PI/4f);wire.lineTo(xx,yy);}c.drawPath(wire,p);
 for(int i=0;i<count;i++){float bx=(i+.5f)*step;float by=base+(design==0?13:design==1?29:19)*size*(float)Math.sin((i+.5f)*Math.PI/4f);int col=colors[i%colors.length];float glow=prefs.getBoolean("twinkle",true)?(float)(.32+.68*Math.pow(Math.sin(clock*2.5+i*1.3),2)):1f;color(col);p.setAlpha((int)(255*glow));if(design==2){c.drawCircle(bx,by+12*size,8*size,p);}else{c.drawOval(bx-6*size,by+4*size,bx+6*size,by+21*size,p);}p.clearShadowLayer();color(0xffd9e3d5);c.drawRoundRect(bx-4*size,by,bx+4*size,by+5*size,2,2,p);}}
 @Override protected void onDraw(Canvas c){super.onDraw(c);long now=System.nanoTime();float dt=last==0L?0f:Math.max(0,Math.min(.05f,(now-last)/1e9f));last=now;clock+=dt;int w=getWidth(),h=getHeight();if(w==0||h==0)return;
 int palette=Math.max(0,Math.min(3,prefs.getInt("palette",0)));int[] colors=COLORS[palette];float sz=.65f+prefs.getInt("lightSize",55)/100f;int design=prefs.getInt("lightDesign",0);
 if(prefs.getBoolean("lights",true))lights(c,w,colors,design,sz);
 boolean snow=prefs.getBoolean("snow",true);boolean candy=prefs.getBoolean("candy",false),gifts=prefs.getBoolean("gifts",false),trees=prefs.getBoolean("trees",false),snowmen=prefs.getBoolean("snowmen",false),bulbs=prefs.getBoolean("bulbs",false),stars=prefs.getBoolean("stars",false),confetti=prefs.getBoolean("confetti",false);
 int density=prefs.getInt("density",55),speed=prefs.getInt("speed",50),size=prefs.getInt("size",50),snowStyle=prefs.getInt("snowStyle",1);
 int direction=Math.max(0,Math.min(4,prefs.getInt("particleDirection",0)));
 float spinAmount=prefs.getInt("particleRotation",35)/100f;
 float windAmount=prefs.getInt("particleWind",30)/100f;
 boolean gentle=prefs.getBoolean("gentleMotion",true);
 float speedMultiplier=.25f+speed/55f;float sizeMultiplier=.4f+size/65f;int visible=(int)(x.length*(.15f+density/120f));
 boolean[] enabled={candy,gifts,trees,snowmen,bulbs,stars,confetti};int total=0;for(boolean e:enabled)if(e)total++;
 for(int i=0;i<visible;i++){boolean decoration=total>0&&i%4==0;int selected=-1;if(decoration){int k=(i/4)%total;for(int t=0;t<enabled.length;t++)if(enabled[t]&&k--==0){selected=t;break;}}
 float rate=(decoration?.14f:.12f)*velocity[i]*speedMultiplier*(gentle?.72f:1f);
 float dx=0f,dy=1f;
 if(direction==1){dx=.65f;dy=1f;}
 else if(direction==2){dx=-.65f;dy=1f;}
 else if(direction==3){dx=1f;dy=.20f;}
 else if(direction==4){dx=-1f;dy=.20f;}
 float norm=(float)Math.sqrt(dx*dx+dy*dy);dx/=norm;dy/=norm;
 y[i]+=dt*rate*dy;
 x[i]+=dt*rate*dx*h/Math.max(1f,w)+dt*(wind[i]*.08f+(float)Math.sin(clock*.85f+drift[i])*.035f)*windAmount;
 angle[i]+=dt*spin[i]*spinAmount*(gentle?.55f:1f);
 if(y[i]>1.10f||x[i]<-.10f||x[i]>1.10f){
   reset(i,false);
   if(direction==3){x[i]=-.07f;y[i]=rng.nextFloat();}
   else if(direction==4){x[i]=1.07f;y[i]=rng.nextFloat();}
   else if(direction==1){x[i]=rng.nextFloat()*.8f-.1f;}
   else if(direction==2){x[i]=.2f+rng.nextFloat()*.9f;}
 }
 float px=x[i]*w,py=y[i]*h;
 c.save();c.rotate(angle[i],px,py);
 if(selected>=0)ornament(c,selected,px,py,(.6f+sizeMultiplier*.55f)*scale[i]);
 else if(snow)snowflake(c,px,py,(2.5f+sizeMultiplier*4.5f)*scale[i],snowStyle);
 c.restore();
 }
 // Display-synchronized frames are scheduled by Choreographer, not fixed-delay timers.
 }
}
