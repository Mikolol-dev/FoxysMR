package com.foxmr.tracker
import android.content.Context

/** Settings stored on the device. Keys: mode(0 hands,1 color,2 both) fps resH(480|720) conf minC beta tolH minS minV facing(0 back,1 front) cal0 cal1 */
class Config(ctx:Context){
 private val p=ctx.getSharedPreferences("foxmr_tracker",Context.MODE_PRIVATE)
 fun getInt(k:String,d:Int)=p.getInt(k,d)
 fun getFloat(k:String,d:Float)=p.getFloat(k,d)
 fun getString(k:String,d:String):String=p.getString(k,d)?:d
 fun setInt(k:String,v:Int){p.edit().putInt(k,v).apply()}
 fun setFloat(k:String,v:Float){p.edit().putFloat(k,v).apply()}
 fun setString(k:String,v:String){p.edit().putString(k,v).apply()}
 /** Presets trade speed for quality. */
 fun preset(name:String){when(name.lowercase()){
  "saver"->{setInt("fps",15);setInt("resH",480);setFloat("conf",.5f)}
  "quality"->{setInt("fps",60);setInt("resH",720);setFloat("conf",.6f)}
  else->{setInt("fps",30);setInt("resH",480);setFloat("conf",.5f)}}}
}
