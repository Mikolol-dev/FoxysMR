using UnityEngine;

namespace FoxMR
{
    public enum PadType { Generic, PS4, Xbox, Nintendo }
    public enum PadAction { Trigger, Grip, A, B }

    /// <summary>Calibrated gamepad reading for up to two controllers (PS4 / Xbox / Nintendo). Needs the legacy Input Manager axes (menu: FoxMR/Setup Input Axes).</summary>
    public static class ControllerInput
    {
        public static PadType TypeOf(int joy)
        {
            var n = Input.GetJoystickNames();
            if (joy < 1 || joy > n.Length) return PadType.Generic;
            string s = n[joy - 1].ToLowerInvariant();
            if (s.Contains("wireless controller") || s.Contains("dualshock") || s.Contains("playstation") || s.Contains("sony")) return PadType.PS4;
            if (s.Contains("xbox") || s.Contains("x-box") || s.Contains("microsoft")) return PadType.Xbox;
            if (s.Contains("nintendo") || s.Contains("pro controller") || s.Contains("joy-con")) return PadType.Nintendo;
            return PadType.Generic;
        }
        static string K(PadType t, string w) { return "foxmr_" + t + "_" + w; }
        public static float RawAxis(int joy, int axis) { return Input.GetAxisRaw("FoxMR_J" + joy + "_A" + axis); }

        /// <summary>Calibrated axis, -1..1. axis is Unity's 1-based joystick axis number.</summary>
        public static float Axis(int joy, int axis)
        {
            var t = TypeOf(joy); float raw = RawAxis(joy, axis);
            float c = PlayerPrefs.GetFloat(K(t, "c" + axis), 0f), lo = PlayerPrefs.GetFloat(K(t, "lo" + axis), -1f), hi = PlayerPrefs.GetFloat(K(t, "hi" + axis), 1f);
            float v = raw >= c ? (raw - c) / Mathf.Max(hi - c, 0.05f) : (raw - c) / Mathf.Max(c - lo, 0.05f);
            v = Mathf.Clamp(v, -1f, 1f);
            return Mathf.Abs(v) < PlayerPrefs.GetFloat("foxmr_dead", 0.12f) ? 0f : v;
        }
        public static bool Button(int joy, int idx) { return Input.GetKey((KeyCode)((int)KeyCode.Joystick1Button0 + (joy - 1) * 20 + idx)); }
        public static int DefaultButton(PadAction a) { switch (a) { case PadAction.Trigger: return 7; case PadAction.Grip: return 5; case PadAction.A: return 0; default: return 1; } }
        public static int MappedButton(int joy, PadAction a) { return PlayerPrefs.GetInt(K(TypeOf(joy), "map" + a), DefaultButton(a)); }
        public static bool Pressed(int joy, PadAction a) { return Button(joy, MappedButton(joy, a)); }

        // ---- calibration steps (used by FoxMRMenu)
        public static void SaveCenters(int joy) { var t = TypeOf(joy); for (int a = 1; a <= 10; a++) PlayerPrefs.SetFloat(K(t, "c" + a), RawAxis(joy, a)); PlayerPrefs.Save(); }
        public static void BeginRange(int joy) { var t = TypeOf(joy); for (int a = 1; a <= 10; a++) { PlayerPrefs.SetFloat(K(t, "lo" + a), 0f); PlayerPrefs.SetFloat(K(t, "hi" + a), 0f); } }
        public static void UpdateRange(int joy)
        {
            var t = TypeOf(joy);
            for (int a = 1; a <= 10; a++)
            {
                float v = RawAxis(joy, a);
                if (v < PlayerPrefs.GetFloat(K(t, "lo" + a), 0f)) PlayerPrefs.SetFloat(K(t, "lo" + a), v);
                if (v > PlayerPrefs.GetFloat(K(t, "hi" + a), 0f)) PlayerPrefs.SetFloat(K(t, "hi" + a), v);
            }
        }
        public static void EndRange() { PlayerPrefs.Save(); }
        public static int FirstPressed(int joy) { for (int i = 0; i < 20; i++) if (Button(joy, i)) return i; return -1; }
        public static void SetMapping(int joy, PadAction a, int idx) { PlayerPrefs.SetInt(K(TypeOf(joy), "map" + a), idx); PlayerPrefs.Save(); }
        public static void Reset(int joy) { var t = TypeOf(joy); for (int a = 1; a <= 10; a++) { PlayerPrefs.DeleteKey(K(t, "c" + a)); PlayerPrefs.DeleteKey(K(t, "lo" + a)); PlayerPrefs.DeleteKey(K(t, "hi" + a)); } foreach (PadAction p in System.Enum.GetValues(typeof(PadAction))) PlayerPrefs.DeleteKey(K(t, "map" + p)); }
    }
}
