package com.foxmr.tracker
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max

/**
 * Camera hand tracking + color tracking, smoothed, no prediction. Runs inside the app/game process (Android only lets the
 * app in front use the camera). Call start(activity) after the CAMERA permission is granted, then poll snapshot().
 * snapshot() layout (floats): [0]=version [1]=time(s) [2]=mode [3]=camera fps, then 2 slots (0=left,1=right) of SLOT floats:
 *  +0 valid +1 x +2 y +3 size +4 pinch +5 closed +6 source(1 hand,2 color) +7 reserved +8.. 21 landmarks x,y,z
 */
object FoxMRTracker{
 const val HDR=4;const val SLOT=71
 private class Slot{
  val f=Array(63){OneEuro()};val fs=OneEuro();val fx=OneEuro();val fy=OneEuro();val fz=OneEuro()
  var valid=false;var x=.5f;var y=.5f;var size=.1f;var pinch=false;var closed=false;var src=0;var t=0.0
  val p=FloatArray(63)
  fun reset(){for(o in f)o.reset();fs.reset();fx.reset();fy.reset();fz.reset()}}
 private class Life:LifecycleOwner{
  private val reg=LifecycleRegistry(this)
  override val lifecycle:Lifecycle get()=reg
  fun up(){reg.currentState=Lifecycle.State.STARTED}
  fun down(){reg.currentState=Lifecycle.State.DESTROYED}}

 private var cfg:Config?=null
 private var activity:Activity?=null
 private var landmarker:HandLandmarker?=null
 private var life:Life?=null
 private val exec=Executors.newSingleThreadExecutor()
 private val color=ColorTracker()
 private val slots=arrayOf(Slot(),Slot())
 private val lock=Any()
 @Volatile private var fpsMeasured=0f
 private var lastFrame=0.0;private var lastTs=0L
 private var buf:ByteBuffer?=null
 @Volatile private var previewOn=false
 @Volatile private var preview:Bitmap?=null
 private var lastPrev=0.0

 @JvmStatic fun start(a:Activity){
  activity=a;val c=Config(a.applicationContext);cfg=c
  for(k in 0..1){val s=c.getString("cal$k","");val p=s.split(",");if(p.size==3){color.cal[k].h=p[0].toFloat();color.cal[k].s=p[1].toFloat();color.cal[k].v=p[2].toFloat();color.cal[k].ok=true}}
  color.tolH=c.getFloat("tolH",16f);color.minS=c.getFloat("minS",.35f);color.minV=c.getFloat("minV",.35f)
  a.runOnUiThread{bind()}}
 @JvmStatic fun stop(){val a=activity;a?.runOnUiThread{try{ProcessCameraProvider.getInstance(a.applicationContext).get().unbindAll()}catch(e:Exception){};life?.down();life=null};landmarker?.close();landmarker=null}
 /** Re-applies camera/model settings (call after changing fps, resolution, confidence or camera). */
 @JvmStatic fun restart(){activity?.runOnUiThread{bind()}}

 private fun bind(){
  val a=activity?:return;val c=cfg?:return
  landmarker?.close();landmarker=null
  if(c.getInt("mode",0)!=1){
   val conf=c.getFloat("conf",.5f)
   fun make(d:Delegate)=HandLandmarker.createFromOptions(a.applicationContext,HandLandmarker.HandLandmarkerOptions.builder()
    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(d).build())
    .setRunningMode(RunningMode.LIVE_STREAM).setNumHands(2)
    .setMinHandDetectionConfidence(conf).setMinHandPresenceConfidence(conf).setMinTrackingConfidence(conf)
    .setResultListener{r,_->onHands(r)}.build())
   landmarker=try{make(Delegate.GPU)}catch(e:Exception){try{make(Delegate.CPU)}catch(e2:Exception){Log.e("FoxMR","hand model failed: "+e2.message);null}}}
  val f=ProcessCameraProvider.getInstance(a.applicationContext)
  f.addListener({
   try{
    val p=f.get();p.unbindAll()
    val res=if(c.getInt("resH",480)>=720)Size(1280,720) else Size(640,480)
    @Suppress("DEPRECATION") val rot=a.windowManager.defaultDisplay.rotation
    val an=ImageAnalysis.Builder().setTargetResolution(res).setTargetRotation(rot).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
     .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
    an.setAnalyzer(exec){ip->analyze(ip)}
    val sel=if(c.getInt("facing",0)==0)CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    life?.down();val l=Life();life=l;l.up()
    p.bindToLifecycle(l,sel,an)
   }catch(e:Exception){Log.e("FoxMR","camera failed: "+e.message)}
  },ContextCompat.getMainExecutor(a.applicationContext))}

 private fun analyze(ip:ImageProxy){
  try{
   val c=cfg?:return
   val now=SystemClock.elapsedRealtimeNanos()/1e9
   if(now-lastFrame<1.0/max(5,c.getInt("fps",30))-.003)return
   if(lastFrame>0)fpsMeasured=fpsMeasured*.8f+.2f*(1.0/max(now-lastFrame,1e-3)).toFloat();lastFrame=now
   val bmp=ip.toBitmap();val rot=ip.imageInfo.rotationDegrees;val mode=c.getInt("mode",0);val mirror=c.getInt("facing",0)==1
   if(mode!=1){lastTs=max(lastTs+1,SystemClock.uptimeMillis())
    landmarker?.detectAsync(BitmapImageBuilder(bmp).build(),ImageProcessingOptions.builder().setRotationDegrees(rot).build(),lastTs)}
   if(mode!=0||color.calibrating){
    val need=bmp.byteCount;var b=buf;if(b==null||b.capacity()!=need){b=ByteBuffer.allocate(need);buf=b}
    b!!.rewind();bmp.copyPixelsToBuffer(b)
    color.process(b.array(),bmp.width,bmp.height,rot,mirror)
    if(!color.calibrating)synchronized(lock){colorToSlots(now,c)}}
   if(previewOn&&now-lastPrev>.2){lastPrev=now;preview=Bitmap.createScaledBitmap(bmp,320,max(1,bmp.height*320/bmp.width),false)}
  }catch(e:Exception){Log.e("FoxMR","frame error: "+e.message)}finally{ip.close()}}

 private fun colorToSlots(t:Double,c:Config){
  val mc=c.getFloat("minC",1.2f).toDouble();val be=c.getFloat("beta",25f).toDouble()
  for(k in 0..1){val d=color.det[k];val s=slots[k]
   if(d.ok){if(s.src!=2||t-s.t>.3)s.reset()
    s.x=s.fx.f(d.x.toDouble(),t,mc,be).toFloat();s.y=s.fy.f(d.y.toDouble(),t,mc,be).toFloat();s.size=s.fz.f(d.s.toDouble(),t,mc,be).toFloat()
    s.valid=true;s.src=2;s.pinch=false;s.closed=true;s.t=t
    for(i in 0 until 21){s.p[i*3]=s.x;s.p[i*3+1]=s.y;s.p[i*3+2]=0f}}
   else if(s.src==2){s.valid=false;s.src=0;s.reset()}}}

 private fun onHands(r:HandLandmarkerResult){
  val c=cfg?:return;val t=SystemClock.elapsedRealtimeNanos()/1e9
  val mc=c.getFloat("minC",1.2f).toDouble();val be=c.getFloat("beta",25f).toDouble();val mirror=c.getInt("facing",0)==1
  val hs=r.landmarks().sortedBy{l->if(mirror)1f-l[0].x() else l[0].x()}
  synchronized(lock){
   val used=BooleanArray(2)
   for((n,lm) in hs.withIndex()){if(n>1)break
    val wx=if(mirror)1f-lm[0].x() else lm[0].x()
    val k=if(hs.size>=2)n else if(wx<.5f)0 else 1
    if(slots[k].src==2&&color.det[k].ok)continue
    used[k]=true;feedHand(slots[k],lm,t,mc,be,mirror)}
   for(k in 0..1)if(!used[k]&&slots[k].src==1){slots[k].valid=false;slots[k].src=0;slots[k].reset()}}}

 private fun feedHand(s:Slot,lm:List<NormalizedLandmark>,t:Double,mc:Double,be:Double,mirror:Boolean){
  if(t-s.t>.3)s.reset()
  for(i in 0 until 21){val x=lm[i].x();val y=lm[i].y()
   s.p[i*3]=s.f[i*3].f((if(mirror)1f-x else x).toDouble(),t,mc,be).toFloat()
   s.p[i*3+1]=s.f[i*3+1].f(y.toDouble(),t,mc,be).toFloat()
   s.p[i*3+2]=s.f[i*3+2].f(lm[i].z().toDouble(),t,mc,be).toFloat()}
  val ss=max(.01f,s.fs.f(hypot((s.p[0]-s.p[27]).toDouble(),(s.p[1]-s.p[28]).toDouble()),t,mc,be).toFloat())
  s.x=(s.p[12]+s.p[24])/2f;s.y=(s.p[13]+s.p[25])/2f;s.size=ss
  val pd=hypot((s.p[12]-s.p[24]).toDouble(),(s.p[13]-s.p[25]).toDouble()).toFloat()/ss
  s.pinch=if(s.pinch)pd<.55f else pd<.35f
  var r=0f;for(tip in intArrayOf(8,12,16,20))r+=hypot((s.p[tip*3]-s.p[0]).toDouble(),(s.p[tip*3+1]-s.p[1]).toDouble()).toFloat()
  r/=4f*ss;s.closed=if(s.closed)r<1.6f else r<1.3f
  s.valid=true;s.src=1;s.t=t}

 @JvmStatic fun snapshot():FloatArray{
  val a=FloatArray(HDR+2*SLOT);a[0]=1f;a[1]=(SystemClock.elapsedRealtimeNanos()/1e9).toFloat();a[2]=(cfg?.getInt("mode",0)?:0).toFloat();a[3]=fpsMeasured
  synchronized(lock){for(k in 0..1){val s=slots[k];val o=HDR+k*SLOT
   if(s.valid&&(SystemClock.elapsedRealtimeNanos()/1e9-s.t)>.4){s.valid=false;s.src=0}
   a[o]=if(s.valid)1f else 0f;a[o+1]=s.x;a[o+2]=s.y;a[o+3]=s.size;a[o+4]=if(s.pinch)1f else 0f;a[o+5]=if(s.closed)1f else 0f;a[o+6]=s.src.toFloat()
   System.arraycopy(s.p,0,a,o+8,63)}}
  return a}

 // ---- settings and color calibration (usable from the Fox MR app or from Unity)
 @JvmStatic fun setPreset(name:String){cfg?.preset(name);restart()}
 @JvmStatic fun setMode(m:Int){cfg?.setInt("mode",m);restart()}
 @JvmStatic fun setInt(k:String,v:Int){cfg?.setInt(k,v)}
 @JvmStatic fun setFloat(k:String,v:Float){cfg?.setFloat(k,v);if(k=="tolH")color.tolH=v;if(k=="minS")color.minS=v;if(k=="minV")color.minV=v}
 @JvmStatic fun getInt(k:String,d:Int):Int=cfg?.getInt(k,d)?:d
 @JvmStatic fun getFloat(k:String,d:Float):Float=cfg?.getFloat(k,d)?:d
 @JvmStatic fun setCalibrating(on:Boolean){color.calibrating=on;previewOn=on}
 /** Live color in the calibration box: [r,g,b,hue,sat,val] */
 @JvmStatic fun liveColor():FloatArray=floatArrayOf(color.liveRgb[0],color.liveRgb[1],color.liveRgb[2],color.live.h,color.live.s,color.live.v)
 @JvmStatic fun saveColor(slot:Int){color.saveLive(slot);val c=color.cal[slot];cfg?.setString("cal$slot","${c.h},${c.s},${c.v}")}
 @JvmStatic fun colorCalibrated(slot:Int):Boolean=color.cal[slot].ok
 @JvmStatic fun previewBitmap():Bitmap?=preview
}
