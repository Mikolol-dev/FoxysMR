package com.foxmr.tracker
import kotlin.math.abs
import kotlin.math.max

/** One Euro filter: adaptive smoothing that never predicts ahead of the measurement. */
class OneEuro{
 private var x=0.0;private var dx=0.0;private var t=0.0;private var init=false
 private fun al(c:Double,dt:Double):Double{val r=6.2832*c*dt;return r/(r+1.0)}
 fun f(v:Double,now:Double,mc:Double,be:Double):Double{
  if(!init){init=true;x=v;dx=0.0;t=now;return v}
  val dt=max(now-t,1e-3);t=now
  dx+=al(1.0,dt)*((v-x)/dt-dx);x+=al(mc+be*abs(dx),dt)*(v-x);return x}
 fun reset(){init=false}
}
