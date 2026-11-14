package com.foxmr.tracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Cal{@Volatile var h=0f;@Volatile var s=0f;@Volatile var v=0f;@Volatile var ok=false}
class Det{@Volatile var ok=false;@Volatile var x=0f;@Volatile var y=0f;@Volatile var s=0f}

/** Finds the two calibrated colored lights (PS4 light bars, LEDs) in the camera image. */
class ColorTracker{
 val cal=arrayOf(Cal(),Cal())
 @Volatile var tolH=16f;@Volatile var minS=.35f;@Volatile var minV=.35f;@Volatile var calibrating=false
 val live=Cal();val liveRgb=FloatArray(3)
 val det=arrayOf(Det(),Det())
 private val tmp=FloatArray(3);private val uvo=FloatArray(2)

 private fun hsv(r:Float,g:Float,b:Float,o:FloatArray){
  val mx=max(r,max(g,b));val mn=min(r,min(g,b));val d=mx-mn
  o[2]=mx;o[1]=if(mx<=0f)0f else d/mx
  if(d<=0f){o[0]=0f;return}
  var h=(if(mx==r)((g-b)/d)%6f else if(mx==g)(b-r)/d+2f else (r-g)/d+4f)*60f
  if(h<0f)h+=360f;o[0]=h}

 private fun uv(x:Int,y:Int,w:Int,h:Int,rot:Int,mirror:Boolean){
  var u:Float;var v:Float
  when(rot){
   90->{u=1f-y.toFloat()/h;v=x.toFloat()/w}
   180->{u=1f-x.toFloat()/w;v=1f-y.toFloat()/h}
   270->{u=y.toFloat()/h;v=1f-x.toFloat()/w}
   else->{u=x.toFloat()/w;v=y.toFloat()/h}}
  if(mirror)u=1f-u
  uvo[0]=u;uvo[1]=v}

 /** px = RGBA bytes (Android ARGB_8888 bitmap memory order). */
 fun process(px:ByteArray,w:Int,h:Int,rot:Int,mirror:Boolean){
  if(calibrating){
   var sr=0.0;var sg=0.0;var sb=0.0;var n=0;var y=0
   while(y<h){var x=0
    while(x<w){uv(x,y,w,h,rot,mirror)
     if(abs(uvo[0]-.5f)<.04f&&abs(uvo[1]-.5f)<.1f){val i=(y*w+x)*4;sr+=px[i].toInt() and 255;sg+=px[i+1].toInt() and 255;sb+=px[i+2].toInt() and 255;n++}
     x+=4}
    y+=4}
   if(n>0){liveRgb[0]=(sr/n/255).toFloat();liveRgb[1]=(sg/n/255).toFloat();liveRgb[2]=(sb/n/255).toFloat();hsv(liveRgb[0],liveRgb[1],liveRgb[2],tmp);live.h=tmp[0];live.s=tmp[1];live.v=tmp[2]}
   return}
  val sx=FloatArray(2);val sy=FloatArray(2);val cnt=IntArray(2)
  var y=0
  while(y<h){var x=0
   while(x<w){val i=(y*w+x)*4
    hsv((px[i].toInt() and 255)/255f,(px[i+1].toInt() and 255)/255f,(px[i+2].toInt() and 255)/255f,tmp)
    for(k in 0..1){val c=cal[k];if(!c.ok)continue
     var dh=abs(tmp[0]-c.h);if(dh>180f)dh=360f-dh
     if(dh<tolH&&tmp[1]>=max(minS,c.s*.6f)&&tmp[2]>=max(minV,c.v*.6f)){uv(x,y,w,h,rot,mirror);sx[k]+=uvo[0];sy[k]+=uvo[1];cnt[k]++}}
    x+=4}
   y+=4}
  for(k in 0..1){val d=det[k]
   if(cnt[k]>=6){d.x=sx[k]/cnt[k];d.y=sy[k]/cnt[k];d.s=min(.4f,max(.05f,sqrt(cnt[k]*16f/(w.toFloat()*h))*2.5f));d.ok=true}else d.ok=false}}

 fun saveLive(slot:Int){val c=cal[slot];c.h=live.h;c.s=live.s;c.v=live.v;c.ok=true}
}
