package com.snowglobe.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Small, original vector illustrations. Static: no extra animation loop or image downloads. */
@Composable internal fun HolidayArtwork(kind:Int,modifier:Modifier=Modifier){
    Canvas(modifier){
        val w=size.width;val h=size.height
        val backgrounds=listOf(Color(0xFF571D2E),Color(0xFF20394B),Color(0xFF49341E),Color(0xFF382B4A))
        val base=backgrounds[kind.coerceIn(0,3)]
        drawRect(Brush.verticalGradient(listOf(base,Color(0xFF101C20))))
        val gold=Color(0xFFFFDA95);val cream=Color(0xFFFFF0D7)
        // Restrained falling stars and snow, intentionally deterministic.
        for(i in 0..23){
            val x=((i*.618034f+.12f)%1f)*w;val y=((i*.381966f+.08f)%1f)*h*.78f
            drawCircle(cream.copy(alpha=.18f+(i%3)*.12f),radius=(.7f+i%2)*density,center=Offset(x,y))
        }
        val cable=Path().apply{moveTo(-w*.05f,h*.07f);cubicTo(w*.25f,h*.36f,w*.72f,-h*.05f,w*1.08f,h*.18f)}
        drawPath(cable,Color(0xFF809A73),style=Stroke(1.3f*density))
        val bulbColors=when(kind){1->listOf(Color(0xFFC4EDFF),Color(0xFFACC0FF));2->listOf(gold);else->listOf(Color(0xFFFF8C84),Color(0xFF96C99C),gold)}
        for(i in 0..7){
            val x=(i+.5f)*w/8f;val y=h*(.12f+.035f*kotlin.math.sin(i*.9f))
            val color=bulbColors[i%bulbColors.size]
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.35f),Color.Transparent),Offset(x,y),h*.14f),h*.14f,Offset(x,y))
            drawOval(color,Offset(x-h*.018f,y),Size(h*.036f,h*.064f))
        }
        if(kind==3){
            // Gift boxes: ribbon details, not emoji glyphs.
            for(i in 0..2){
                val x=w*(.23f+i*.21f);val y=h*(.55f+(i%2)*.11f);val s=h*(.21f+(i%2)*.04f)
                rotate(if(i%2==0)-12f else 9f,Offset(x,y)){
                    drawRoundRect(if(i%2==0)Color(0xFFBF475C)else Color(0xFF4D8467),Offset(x-s/2,y-s/2),Size(s,s),androidx.compose.ui.geometry.CornerRadius(4*density))
                    drawRect(gold,Offset(x-s*.07f,y-s/2),Size(s*.14f,s))
                    drawRect(gold,Offset(x-s/2,y-s*.08f),Size(s,s*.12f))
                    drawOval(gold,Offset(x-s*.28f,y-s*.68f),Size(s*.28f,s*.22f),style=Stroke(1.4f*density))
                    drawOval(gold,Offset(x,y-s*.68f),Size(s*.28f,s*.22f),style=Stroke(1.4f*density))
                }
            }
        }else{
            // Layered evergreens with a warm star at the top.
            for(i in 0..4){
                val x=w*(.1f+i*.2f);val top=h*(.44f+(i%2)*.14f);val height=h-top+8*density;val width=height*.53f
                val path=Path().apply{moveTo(x,top);lineTo(x-width*.5f,top+height*.76f);lineTo(x-width*.36f,top+height*.76f);lineTo(x-width*.65f,top+height);lineTo(x+width*.65f,top+height);lineTo(x+width*.36f,top+height*.76f);lineTo(x+width*.5f,top+height*.76f);close()}
                drawPath(path,if(i%2==0)Color(0xFF3F7158)else Color(0xFF254B3C))
                if(i==2){drawCircle(gold,2.4f*density,Offset(x,top));drawLine(gold,Offset(x-5*density,top),Offset(x+5*density,top),1*density);drawLine(gold,Offset(x,top-5*density),Offset(x,top+5*density),1*density)}
            }
        }
        val snow=Path().apply{moveTo(0,h*.93f);quadraticTo(w*.3f,h*.84f,w*.55f,h*.96f);quadraticTo(w*.8f,h*.87f,w,h*.94f);lineTo(w,h);lineTo(0,h);close()}
        drawPath(snow,cream.copy(alpha=.88f))
    }
}

/** Static garland accent kept away from text/touch targets. */
@Composable internal fun HolidaySprig(modifier:Modifier,tint:Color){
    Canvas(modifier){
        val w=size.width;val h=size.height
        drawLine(tint.copy(alpha=.6f),Offset(0,h*.85f),Offset(w,h*.2f),1.2f*density,StrokeCap.Round)
        for(i in 1..7){
            val x=w*i/8f;val y=h*(.85f-.65f*i/8f)
            drawLine(tint.copy(alpha=.65f),Offset(x,y),Offset(x-w*.14f,y-h*.42f),1.5f*density,StrokeCap.Round)
            drawLine(tint.copy(alpha=.65f),Offset(x,y),Offset(x+w*.03f,y+h*.32f),1.5f*density,StrokeCap.Round)
        }
        drawCircle(Color(0xFFD65A68),2.6f*density,Offset(w*.72f,h*.48f))
        drawCircle(Color(0xFFED9691),2.2f*density,Offset(w*.83f,h*.55f))
    }
}
