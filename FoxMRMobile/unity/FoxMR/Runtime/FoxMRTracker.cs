using UnityEngine;
#if UNITY_ANDROID && !UNITY_EDITOR
using UnityEngine.Android;
#endif

namespace FoxMR
{
    public struct HandState
    {
        public bool valid;          // tracked this frame
        public Vector2 pos;         // 0..1 in the camera picture (x right, y down)
        public float size;          // apparent size, bigger = closer
        public bool pinch, closed;  // thumb-index pinch / fist (hand tracking only)
        public int source;          // 1 = hand, 2 = color light
        public Vector3[] landmarks; // 21 hand points (hand tracking only)
    }

    /// <summary>Starts the Android tracker (camera hand tracking + color tracking) and exposes the results every frame.</summary>
    public class Tracker : MonoBehaviour
    {
        const int HDR = 4, SLOT = 71;
        static Tracker inst;
        public static HandState[] Hands = { New(), New() };   // 0 = left, 1 = right
        public static float CameraFps;
        public static bool Running;
        static HandState New() { return new HandState { landmarks = new Vector3[21] }; }

        public static Tracker Instance
        {
            get
            {
                if (inst == null)
                {
                    var go = new GameObject("FoxMRTracker");
                    DontDestroyOnLoad(go);
                    inst = go.AddComponent<Tracker>();
                }
                return inst;
            }
        }
        public static void EnsureStarted() { var _ = Instance; }

#if UNITY_ANDROID && !UNITY_EDITOR
        AndroidJavaClass cls;
        bool asked;
        static object[] A(params object[] a) { return a; }
        public static T Call<T>(string m, params object[] a) { return inst != null && inst.cls != null ? inst.cls.CallStatic<T>(m, a) : default(T); }
        public static void Call(string m, params object[] a) { if (inst != null && inst.cls != null) inst.cls.CallStatic(m, a); }
#else
        public static T Call<T>(string m, params object[] a) { return default(T); }
        public static void Call(string m, params object[] a) { }
#endif

        // ---- settings (saved on the phone)
        public static void SetPreset(string name) { Call("setPreset", name); }       // "saver", "balanced", "quality"
        public static void SetMode(int mode) { Call("setMode", mode); }              // 0 hands, 1 color lights, 2 both
        public static void SetInt(string key, int v) { Call("setInt", key, v); }     // fps, resH (480/720), facing (0 back, 1 front)
        public static void SetFloat(string key, float v) { Call("setFloat", key, v); } // minC, beta, tolH, minS, minV, conf
        public static void Restart() { Call("restart"); }
        public static void SetCalibrating(bool on) { Call("setCalibrating", on); }
        public static float[] LiveColor() { return Call<float[]>("liveColor"); }    // r,g,b,hue,sat,val of the calibration box
        public static void SaveColor(int slot) { Call("saveColor", slot); }

        void Update()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (cls == null)
            {
                if (!Permission.HasUserAuthorizedPermission(Permission.Camera))
                {
                    if (!asked) { Permission.RequestUserPermission(Permission.Camera); asked = true; }
                    return;
                }
                using (var up = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var act = up.GetStatic<AndroidJavaObject>("currentActivity"))
                {
                    cls = new AndroidJavaClass("com.foxmr.tracker.FoxMRTracker");
                    cls.CallStatic("start", act);
                }
                Running = true;
            }
            float[] a = cls.CallStatic<float[]>("snapshot");
            if (a == null || a.Length < HDR + 2 * SLOT) return;
            CameraFps = a[3];
            for (int k = 0; k < 2; k++)
            {
                int o = HDR + k * SLOT;
                var h = Hands[k];
                h.valid = a[o] > 0.5f; h.pos = new Vector2(a[o + 1], a[o + 2]); h.size = a[o + 3];
                h.pinch = a[o + 4] > 0.5f; h.closed = a[o + 5] > 0.5f; h.source = (int)a[o + 6];
                for (int i = 0; i < 21; i++) h.landmarks[i] = new Vector3(a[o + 8 + i * 3], a[o + 9 + i * 3], a[o + 10 + i * 3]);
                Hands[k] = h;
            }
#endif
        }
    }
}
