package com.foxmr.app
import android.content.SharedPreferences
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.max

/** Stick/trigger calibration per controller family (PS4 / Xbox / Nintendo / Generic). */
object ControllerCal{
 val AXES=intArrayOf(MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER)
 val NAMES=arrayOf("Left X","Left Y","Right X","Right Y","L trigger","R trigger")
 fun type(d:InputDevice?):String=when(d?.vendorId){0x054c->"PS4";0x045e->"Xbox";0x057e->"Nintendo";else->"Generic"}
 fun saveCenters(p:SharedPreferences,t:String,raw:FloatArray){val e=p.edit();for(i in raw.indices)e.putFloat("${t}_c$i",raw[i]);e.apply()}
 fun saveRange(p:SharedPreferences,t:String,lo:FloatArray,hi:FloatArray){val e=p.edit();for(i in lo.indices){e.putFloat("${t}_lo$i",lo[i]);e.putFloat("${t}_hi$i",hi[i])};e.apply()}
 fun reset(p:SharedPreferences,t:String){val e=p.edit();for(i in 0 until 6){e.remove("${t}_c$i");e.remove("${t}_lo$i");e.remove("${t}_hi$i")};e.apply()}
 fun apply(p:SharedPreferences,t:String,i:Int,raw:Float):Float{
  val c=p.getFloat("${t}_c$i",0f);val lo=p.getFloat("${t}_lo$i",-1f);val hi=p.getFloat("${t}_hi$i",1f)
  val v=if(raw>=c)(raw-c)/max(hi-c,.05f) else (raw-c)/max(c-lo,.05f)
  val dz=if(Math.abs(v)<.08f)0f else v
  return dz.coerceIn(-1f,1f)}
}
