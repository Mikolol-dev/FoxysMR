package com.foxmr.app
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.foxmr.tracker.FoxMRTracker
import kotlin.math.asin
import kotlin.math.atan2

class MainActivity:Activity(){
 private val ui=Handler(Looper.getMainLooper());private var tick:Runnable?=null
 private lateinit var root:FrameLayout
 private val prefs by lazy{getSharedPreferences("foxmr_app",0)}
 private val axisRaw=FloatArray(6);private val axisLo=FloatArray(6);private val axisHi=FloatArray(6);private var capturing=false
 private var padType="Generic";private var padName="(none)";private val pressed=sortedSetOf<String>()
 private val sm by lazy{getSystemService(SENSOR_SERVICE) as SensorManager};private var testView:TestView?=null;private val rotM=FloatArray(9);private var yaw0=0f

 override fun onCreate(b:Bundle?){super.onCreate(b);root=FrameLayout(this);setContentView(root)
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),1) else FoxMRTracker.start(this)
  showMenu()}
 override fun onRequestPermissionsResult(rc:Int,p:Array<out String>,g:IntArray){if(g.isNotEmpty()&&g[0]==PackageManager.PERMISSION_GRANTED)FoxMRTracker.start(this)}
 override fun onDestroy(){FoxMRTracker.stop();super.onDestroy()}
 override fun onPause(){leaveScreens();super.onPause()}

 private fun leaveScreens(){FoxMRTracker.setCalibrating(false);sm.unregisterListener(head)}
 private fun screen(v:View){tick?.let{ui.removeCallbacks(it)};tick=null;leaveScreens();root.removeAllViews();root.addView(ScrollView(this).apply{addView(v)})}
 private fun every(ms:Long,f:()->Unit){val r=object:Runnable{override fun run(){f();ui.postDelayed(this,ms)}};tick=r;ui.post(r)}
 private fun col()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,32,48,32)}
 private fun LinearLayout.btn(t:String,f:()->Unit){addView(Button(context).apply{text=t;setOnClickListener{f()}})}
 private fun LinearLayout.row(vararg bs:Pair<String,()->Unit>){addView(LinearLayout(context).apply{for((t,f) in bs)addView(Button(context).apply{text=t;setOnClickListener{f()}},LinearLayout.LayoutParams(0,-2,1f))})}
 private fun LinearLayout.label(t:String)=TextView(context).apply{text=t;textSize=15f;setPadding(0,12,0,4)}.also{addView(it)}
 private fun LinearLayout.slider(t:String,k:String,d:Float,mn:Float,mx:Float,onSet:()->Unit={}){
  val tv=label(t);val s=SeekBar(context).apply{max=100;progress=((FoxMRTracker.getFloat(k,d)-mn)/(mx-mn)*100).toInt()};addView(s)
  fun u(){tv.text="$t: %.2f".format(mn+(mx-mn)*s.progress/100f)};u()
  s.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(x:SeekBar?,p:Int,f:Boolean){FoxMRTracker.setFloat(k,mn+(mx-mn)*p/100f);u();onSet()};override fun onStartTrackingTouch(x:SeekBar?){};override fun onStopTrackingTouch(x:SeekBar?){}})}

 private fun showMenu(){val c=col()
  c.addView(TextView(this).apply{text="Fox MR";textSize=30f})
  val st=c.label("")
  c.btn("Tracking settings (mode, framerate, presets)"){showTracking()}
  c.btn("Color calibration (PS4 light bars)"){showColor()}
  c.btn("Controller calibration (PS4 / Xbox / Nintendo)"){showControllers()}
  c.btn("Test view (VR Box side-by-side + head gyro)"){showTest()}
  screen(c);every(500){val s=FoxMRTracker.snapshot();st.text="Camera ${"%.0f".format(s[3])} fps | left ${if(s[FoxMRTracker.HDR]>.5f)"tracked" else "-"} | right ${if(s[FoxMRTracker.HDR+FoxMRTracker.SLOT]>.5f)"tracked" else "-"}"}}

 private fun showTracking(){val c=col()
  c.btn("< Back"){showMenu()}
  c.label("Tracking mode");c.row("Hands" to {FoxMRTracker.setMode(0)},"Color lights" to {FoxMRTracker.setMode(1)},"Both" to {FoxMRTracker.setMode(2)})
  c.label("Preset");c.row("Battery saver" to {FoxMRTracker.setPreset("saver")},"Balanced" to {FoxMRTracker.setPreset("balanced")},"Quality" to {FoxMRTracker.setPreset("quality")})
  c.label("Tracking framerate");c.row("15" to {FoxMRTracker.setInt("fps",15)},"30" to {FoxMRTracker.setInt("fps",30)},"60" to {FoxMRTracker.setInt("fps",60)})
  c.label("Camera resolution (applies on Restart)");c.row("480p" to {FoxMRTracker.setInt("resH",480)},"720p" to {FoxMRTracker.setInt("resH",720)})
  c.label("Camera");c.row("Back (VR Box)" to {FoxMRTracker.setInt("facing",0)},"Front" to {FoxMRTracker.setInt("facing",1)})
  c.slider("Smoothing (lower = smoother)","minC",1.2f,.1f,6f);c.slider("Speed response (higher = less lag)","beta",25f,0f,80f)
  c.btn("Restart tracking to apply"){FoxMRTracker.restart()}
  screen(c)}

 private fun showColor(){val c=col();val prev=ImageView(this).apply{adjustViewBounds=true};val sw=View(this);val info=TextView(this)
  c.btn("< Back"){showMenu()};c.label("Point a light at the camera, fill the middle of the picture with it, then save.")
  c.addView(prev,LinearLayout.LayoutParams(640,-2));c.addView(sw,LinearLayout.LayoutParams(160,60));c.addView(info)
  c.row("Save LEFT light" to {FoxMRTracker.saveColor(0)},"Save RIGHT light" to {FoxMRTracker.saveColor(1)})
  c.slider("Hue tolerance","tolH",16f,4f,40f);c.slider("Minimum saturation","minS",.35f,0f,1f);c.slider("Minimum brightness","minV",.35f,0f,1f)
  c.label("Tip: dim the room, turn the light bar to a strong color, set the mode to Color lights or Both.")
  screen(c);FoxMRTracker.setCalibrating(true)
  every(200){val l=FoxMRTracker.liveColor();sw.setBackgroundColor(Color.rgb((l[0]*255).toInt(),(l[1]*255).toInt(),(l[2]*255).toInt()))
   info.text="hue ${"%.0f".format(l[3])}  sat ${"%.2f".format(l[4])}  val ${"%.2f".format(l[5])}   saved: left ${FoxMRTracker.colorCalibrated(0)}, right ${FoxMRTracker.colorCalibrated(1)}"
   FoxMRTracker.previewBitmap()?.let{prev.setImageBitmap(it)}}}

 private fun showControllers(){val c=col();val live=TextView(this).apply{typeface=android.graphics.Typeface.MONOSPACE}
  c.btn("< Back"){showMenu()};c.label("Connect a PS4, Xbox or Nintendo controller, then move it so it shows up below.")
  c.addView(live)
  c.label("1. Let go of the sticks and triggers, then:");c.btn("Save centers"){ControllerCal.saveCenters(prefs,padType,axisRaw.copyOf())}
  c.label("2. Press this, then roll both sticks around fully and press both triggers for 5 seconds:")
  c.btn("Capture range"){capturing=true;for(i in 0 until 6){axisLo[i]=0f;axisHi[i]=0f};ui.postDelayed({capturing=false;ControllerCal.saveRange(prefs,padType,axisLo.copyOf(),axisHi.copyOf())},5000)}
  c.btn("Reset this controller type"){ControllerCal.reset(prefs,padType)}
  screen(c);every(100){val sb=StringBuilder("Controller: $padName\nType: $padType${if(capturing)"   (capturing...)" else ""}\n\n")
   for(i in 0 until 6)sb.append("%-10s raw %+.2f   calibrated %+.2f\n".format(ControllerCal.NAMES[i],axisRaw[i],ControllerCal.apply(prefs,padType,i,axisRaw[i])))
   sb.append("\nButtons: ").append(pressed.joinToString(" "));live.text=sb}}

 private val head=object:SensorEventListener{
  override fun onSensorChanged(e:SensorEvent){SensorManager.getRotationMatrixFromVector(rotM,e.values)
   val y=atan2(-rotM[2],-rotM[5]);val p=asin((-rotM[8]).coerceIn(-1f,1f));testView?.yaw=y-yaw0;testView?.pitch=p}
  override fun onAccuracyChanged(s:Sensor?,a:Int){}}
 private fun showTest(){val v=TestView(this);testView=v
  val panel=col();panel.btn("< Back"){showMenu()};panel.btn("Recenter"){yaw0=v.yaw+yaw0}
  panel.slider("Eye separation","__ipd",0f,-60f,60f){v.ipd=FoxMRTracker.getFloat("__ipd",0f)}
  root.removeAllViews();leaveScreens();tick?.let{ui.removeCallbacks(it)}
  val f=FrameLayout(this);f.addView(v);panel.setBackgroundColor(0xAA000000.toInt());f.addView(panel,FrameLayout.LayoutParams(520,-2))
  root.addView(f);sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let{sm.registerListener(head,it,SensorManager.SENSOR_DELAY_GAME)}
  ui.postDelayed({panel.visibility=View.GONE},8000);v.setOnClickListener{panel.visibility=View.VISIBLE}}

 override fun dispatchGenericMotionEvent(e:MotionEvent):Boolean{
  if(e.source and InputDevice.SOURCE_JOYSTICK!=0){padType=ControllerCal.type(e.device);padName=e.device?.name?:"?"
   for(i in 0 until 6){val v=e.getAxisValue(ControllerCal.AXES[i]);axisRaw[i]=v;if(capturing){if(v<axisLo[i])axisLo[i]=v;if(v>axisHi[i])axisHi[i]=v}}
   return true}
  return super.dispatchGenericMotionEvent(e)}
 override fun dispatchKeyEvent(e:KeyEvent):Boolean{
  if(e.source and InputDevice.SOURCE_GAMEPAD!=0){padType=ControllerCal.type(e.device);padName=e.device?.name?:"?";val n=KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_BUTTON_").removePrefix("KEYCODE_")
   if(e.action==KeyEvent.ACTION_DOWN)pressed.add(n) else pressed.remove(n);return true}
  return super.dispatchKeyEvent(e)}
}
