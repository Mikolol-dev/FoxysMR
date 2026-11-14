package com.foxmr.app
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.foxmr.tracker.FoxMRTracker

/** Side-by-side test view for VR Box: a world grid that follows your head, plus your tracked hands/lights. */
class TestView(ctx:Context):View(ctx){
 @Volatile var yaw=0f;@Volatile var pitch=0f;@Volatile var ipd=0f
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val HC=arrayOf(intArrayOf(0,1),intArrayOf(1,2),intArrayOf(2,3),intArrayOf(3,4),intArrayOf(0,5),intArrayOf(5,6),intArrayOf(6,7),intArrayOf(7,8),intArrayOf(5,9),intArrayOf(9,10),intArrayOf(10,11),intArrayOf(11,12),intArrayOf(9,13),intArrayOf(13,14),intArrayOf(14,15),intArrayOf(15,16),intArrayOf(13,17),intArrayOf(17,18),intArrayOf(18,19),intArrayOf(19,20),intArrayOf(0,17))
 override fun onDraw(c:Canvas){
  c.drawColor(0xFF10131a.toInt());val w=width/2f;val h=height.toFloat();val s=FoxMRTracker.snapshot()
  for(eye in 0..1){
   c.save();c.clipRect(eye*w,0f,(eye+1)*w,h);c.translate(eye*w+(if(eye==0)ipd else -ipd),0f)
   p.strokeWidth=2f;p.color=0xFF2a3350.toInt()
   for(i in -12..12){val x=w/2f+i*90f-yaw*700f;c.drawLine(x,0f,x,h,p)}
   for(i in -8..8){val y=h/2f+i*90f+pitch*700f;c.drawLine(0f,y,w,y,p)}
   p.color=0xFF3a4670.toInt();c.drawLine(w/2f,0f,w/2f,h,p)
   for(k in 0..1){val o=FoxMRTracker.HDR+k*FoxMRTracker.SLOT;if(s[o]<.5f)continue
    val col=if(k==0)0xFF37e6c0.toInt() else 0xFFff8a5c.toInt()
    if(s[o+6]==1f){p.color=col;p.strokeWidth=3f
     for(e in HC)c.drawLine(s[o+8+e[0]*3]*w,s[o+9+e[0]*3]*h,s[o+8+e[1]*3]*w,s[o+9+e[1]*3]*h,p)}
    p.style=Paint.Style.FILL;p.color=col;c.drawCircle(s[o+1]*w,s[o+2]*h,if(s[o+4]>.5f||s[o+6]==2f)18f else 30f,p)}
   c.restore()}
  p.color=0xFFFFFFFF.toInt();p.textSize=26f;c.drawText("camera ${"%.0f".format(s[3])} fps",20f,36f,p)
  postInvalidateDelayed(33)}
}
