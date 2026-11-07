package com.snowglobe.overlay;

import android.graphics.*;
import java.util.Random;

/** Original offline winter illustrations, rendered once for preview or a full 2160 × 3840 export. */
public final class WallpaperArt {
    public static final int WIDTH=2160, HEIGHT=3840;
    public static final String[] NAMES={"Winter Lodge","Moonlit Pines","Candlelight Village","Aurora Night"};
    private WallpaperArt() {}
    public static Bitmap render(int design,int width,int height){
        if(design<0||design>=NAMES.length||width<1||height<1||width>WIDTH||height>HEIGHT)
            throw new IllegalArgumentException("Unsupported wallpaper dimensions or design");
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(width/1080f,height/1920f);
        draw(canvas,design);return bitmap;
    }
    private static void draw(Canvas c,int design){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);Path path=new Path();
        int[][] skies={{0xff071926,0xff244451},{0xff081d21,0xff35554d},{0xff201321,0xff674957},{0xff081329,0xff25465e}};
        int[] sky=skies[design];
        p.setShader(new LinearGradient(0,0,0,1740,sky[0],sky[1],Shader.TileMode.CLAMP));c.drawRect(0,0,1080,1920,p);p.setShader(null);
        Random random=new Random(9401+design);
        for(int i=0;i<100;i++){
            p.setColor(0xffe5f3ec);p.setAlpha(35+random.nextInt(140));
            c.drawCircle(random.nextFloat()*1080,random.nextFloat()*1200,1+random.nextFloat()*2.1f,p);
        }
        p.setAlpha(255);
        if(design==3){
            for(int i=0;i<5;i++){
                path.reset();path.moveTo(-100,210+i*42);path.cubicTo(260,80+i*40,380,540+i*30,1180,190+i*65);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(40+i*18);
                p.setShader(new LinearGradient(0,100,1080,700,new int[]{0x0043ddad,0x5062e3bc,0x246ca7db,0x0043ddad},null,Shader.TileMode.CLAMP));c.drawPath(path,p);
            }
            p.setShader(null);p.setStyle(Paint.Style.FILL);
        }
        float mx=design==2?215:810,my=design==2?380:310;
        p.setShader(new RadialGradient(mx,my,210,new int[]{0x35fff0cc,0x10fff0cc,0x00fff0cc},new float[]{0,.45f,1},Shader.TileMode.CLAMP));c.drawCircle(mx,my,210,p);p.setShader(null);
        p.setColor(0xfff7edcf);c.drawCircle(mx,my,design==1?58:42,p);
        p.setColor(0x22d1b981);c.drawCircle(mx+11,my-6,12,p);c.drawCircle(mx-13,my+17,7,p);
        for(int layer=0;layer<3;layer++){
            int count=layer==0?15:10;
            for(int i=0;i<count;i++){
                float x=(i+.15f)*120+(layer%2)*55;
                float y=1100+layer*145+(float)Math.sin(i*1.7+layer)*72;
                tree(c,p,path,x,y,150+layer*60+(i%3)*24,layer==0?0xff304b50:layer==1?0xff1d3e3d:0xff17352f,layer>0);
            }
        }
        path.reset();path.moveTo(0,1330);path.cubicTo(260,1180,535,1315,1080,1210);path.lineTo(1080,1920);path.lineTo(0,1920);path.close();
        p.setShader(new LinearGradient(0,1250,0,1920,0xffe6efed,0xffa6bdc9,Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);
        if(design==0||design==2){
            cabin(c,p,path,design==2?185:305,design==2?1070:1095,design==2?.74f:1f,design==2);
            if(design==2)cabin(c,p,path,610,1160,.63f,false);
            snowman(c,p,path,design==2?860:805,1460,design==2?.8f:1f);
        }else{
            tree(c,p,path,260,1470,430,0xff17362e,true);
            tree(c,p,path,700,1390,310,0xff20483d,true);
            lights(c,p,path,260,1125,140,280);
            if(design==3)snowman(c,p,path,850,1530,.85f);
        }
        path.reset();path.moveTo(-20,1660);path.cubicTo(270,1500,650,1740,1120,1560);path.lineTo(1120,1950);path.lineTo(-20,1950);path.close();
        p.setShader(new LinearGradient(0,1600,0,1920,0xffeef4ed,0xffc3d3d8,Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);
        tree(c,p,path,75,1810,530,0xff17382f,true);tree(c,p,path,1040,1750,480,0xff1c4036,true);
        random.setSeed(7190+design);
        for(int i=0;i<120;i++){
            float x=random.nextFloat()*1080,y=random.nextFloat()*1900;
            p.setColor(0xfff7fbf5);p.setAlpha(70+random.nextInt(145));
            float radius=i%7==0?4.6f:1.6f+random.nextFloat()*1.8f;
            c.drawCircle(x,y,radius,p);
            if(i%17==0){p.setStrokeWidth(1.6f);c.drawLine(x-8,y,x+8,y,p);c.drawLine(x,y-8,x,y+8,p);}
        }
        p.setAlpha(255);
    }
    private static void tree(Canvas c,Paint p,Path path,float x,float ground,float h,int color,boolean snow){
        p.setColor(0xff263b34);c.drawRoundRect(x-h*.027f,ground-h*.42f,x+h*.027f,ground+8,4,4,p);
        for(int i=0;i<4;i++){
            float top=ground-h+i*h*.16f,half=h*(.13f+i*.049f),bottom=top+h*(.35f+i*.035f);
            path.reset();path.moveTo(x,top);path.lineTo(x-half,bottom);path.quadTo(x,bottom-h*.045f,x+half,bottom);path.close();p.setColor(color);c.drawPath(path,p);
            if(snow){path.reset();path.moveTo(x,top);path.lineTo(x-half*.82f,bottom-h*.04f);path.quadTo(x-half*.22f,bottom-h*.08f,x,bottom-h*.15f);path.quadTo(x+half*.2f,bottom-h*.07f,x+half*.76f,bottom-h*.04f);path.close();p.setColor(0xffd8e5df);c.drawPath(path,p);}
        }
    }
    private static void cabin(Canvas c,Paint p,Path path,float x,float y,float scale,boolean red){
        c.save();c.translate(x,y);c.scale(scale,scale);
        p.setColor(red?0xff8a4850:0xffeae7dc);c.drawRoundRect(0,0,340,250,5,5,p);
        p.setColor(0xff15262a);path.reset();path.moveTo(-35,6);path.lineTo(168,-155);path.lineTo(375,6);path.close();c.drawPath(path,p);
        p.setColor(0xfff3f3e6);p.setStrokeWidth(22);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(-35,6,168,-155,p);c.drawLine(168,-155,375,6,p);
        p.setColor(0xff283739);c.drawRect(248,-140,280,-70,p);p.setColor(0xfff3f3e6);c.drawRoundRect(240,-150,288,-135,6,6,p);
        p.setColor(0xff243134);c.drawRoundRect(133,99,205,250,8,8,p);
        for(int i=0;i<2;i++){
            float wx=32+i*220;
            p.setShader(new RadialGradient(wx+29,82,85,new int[]{0x65ffcf74,0x00ffcf74},null,Shader.TileMode.CLAMP));c.drawCircle(wx+29,82,85,p);p.setShader(null);
            p.setColor(0xff172b2d);c.drawRoundRect(wx-6,47,wx+62,128,5,5,p);
            p.setShader(new LinearGradient(0,52,0,122,0xffffe7a0,0xfff4b363,Shader.TileMode.CLAMP));c.drawRect(wx,53,wx+56,122,p);p.setShader(null);
            p.setColor(0xff243134);c.drawRect(wx+25,53,wx+31,122,p);c.drawRect(wx,84,wx+56,90,p);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(11);p.setColor(0xff396c48);c.drawCircle(169,151,19,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xffb74b59);c.drawCircle(169,171,7,p);p.setColor(0xffd8b66e);c.drawCircle(192,195,3,p);
        lights(c,p,path,170,4,180,30);
        p.setColor(0xffeff1e8);c.drawRoundRect(-12,237,352,264,12,12,p);c.restore();
    }
    private static void lights(Canvas c,Paint p,Path path,float x,float y,float width,float drop){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);p.setColor(0xff345448);
        path.reset();path.moveTo(x-width,y);path.quadTo(x,y+drop,x+width,y);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        int[] colors={0xfff2c76d,0xffe98c80,0xffa6d6aa,0xffedda9b};
        for(int i=0;i<13;i++){
            float t=(i+.5f)/13f,bx=x-width+2*width*t,by=y+2*drop*t*(1-t);
            int color=colors[i%colors.length];p.setShader(new RadialGradient(bx,by,16,new int[]{(color&0xffffff)|0x55000000,color&0xffffff},null,Shader.TileMode.CLAMP));c.drawCircle(bx,by,16,p);p.setShader(null);
            p.setColor(color);c.drawOval(bx-3.5f,by-1,bx+3.5f,by+9,p);
        }
    }
    private static void snowman(Canvas c,Paint p,Path path,float x,float ground,float scale){
        c.save();c.translate(x,ground);c.scale(scale,scale);
        p.setColor(0xffd3e1df);c.drawOval(-65,-25,70,12,p);p.setColor(0xfff5f4e8);c.drawCircle(0,-63,59,p);c.drawCircle(0,-142,42,p);
        p.setColor(0xff1b3030);c.drawRoundRect(-30,-211,29,-175,5,5,p);c.drawRoundRect(-48,-181,48,-170,5,5,p);
        c.drawCircle(-13,-147,3.2f,p);c.drawCircle(11,-147,3.2f,p);c.drawCircle(0,-74,4,p);c.drawCircle(0,-49,4,p);
        p.setColor(0xffd69a64);path.reset();path.moveTo(0,-139);path.lineTo(29,-134);path.lineTo(0,-130);path.close();c.drawPath(path,p);
        p.setColor(0xffa94251);c.drawRoundRect(-35,-112,35,-96,6,6,p);c.drawRoundRect(13,-102,30,-61,5,5,p);
        p.setColor(0xff654c3d);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(-42,-78,-87,-111,p);c.drawLine(42,-78,79,-104,p);c.restore();
    }
}
