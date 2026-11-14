using UnityEngine;

namespace FoxMR
{
    /// <summary>In-game settings and calibration menu (tracking presets, color light calibration, PS4/Xbox/Nintendo controller calibration).
    /// Open it with a three-finger tap or the open key, set things up BEFORE putting the phone in the headset.</summary>
    public class Menu : MonoBehaviour
    {
        public KeyCode openKey = KeyCode.JoystickButton7;
        public bool showOnStart = false;
        enum Page { Main, Tracking, Color, Pads }
        Page page = Page.Main; bool open; int joy = 1;
        bool ranging; float rangeEnd; PadAction? mapping; Texture2D swatch;

        void Start() { Tracker.EnsureStarted(); swatch = new Texture2D(1, 1); open = showOnStart; }

        void Update()
        {
            if (Input.GetKeyDown(openKey) || (Input.touchCount >= 3 && Input.GetTouch(2).phase == TouchPhase.Began)) Toggle();
            if (ranging) { ControllerInput.UpdateRange(joy); if (Time.time > rangeEnd) { ranging = false; ControllerInput.EndRange(); } }
            if (mapping.HasValue) { int b = ControllerInput.FirstPressed(joy); if (b >= 0) { ControllerInput.SetMapping(joy, mapping.Value, b); mapping = null; } }
        }

        void Toggle() { open = !open; if (!open) Tracker.SetCalibrating(false); }
        void Go(Page p) { page = p; Tracker.SetCalibrating(p == Page.Color); }

        void OnGUI()
        {
            if (!open) return;
            float s = Screen.dpi > 0 ? Mathf.Max(1f, Screen.dpi / 160f) : 2f;
            GUI.matrix = Matrix4x4.Scale(new Vector3(s, s, 1f));
            GUILayout.BeginArea(new Rect(10, 10, Screen.width / s - 20, Screen.height / s - 20), GUI.skin.box);
            GUILayout.Label("Fox MR   (camera " + Tracker.CameraFps.ToString("0") + " fps)");
            if (page != Page.Main && Btn("< Back")) Go(Page.Main);
            switch (page)
            {
                case Page.Main:
                    if (Btn("Tracking settings")) Go(Page.Tracking);
                    if (Btn("Color light calibration")) Go(Page.Color);
                    if (Btn("Controller calibration")) Go(Page.Pads);
                    if (Btn("Close")) Toggle();
                    break;
                case Page.Tracking:
                    GUILayout.Label("Mode"); GUILayout.BeginHorizontal();
                    if (Btn("Hands")) Tracker.SetMode(0); if (Btn("Color lights")) Tracker.SetMode(1); if (Btn("Both")) Tracker.SetMode(2);
                    GUILayout.EndHorizontal(); GUILayout.Label("Preset"); GUILayout.BeginHorizontal();
                    if (Btn("Battery saver")) Tracker.SetPreset("saver"); if (Btn("Balanced")) Tracker.SetPreset("balanced"); if (Btn("Quality")) Tracker.SetPreset("quality");
                    GUILayout.EndHorizontal(); GUILayout.Label("Tracking framerate"); GUILayout.BeginHorizontal();
                    if (Btn("15")) Tracker.SetInt("fps", 15); if (Btn("30")) Tracker.SetInt("fps", 30); if (Btn("60")) Tracker.SetInt("fps", 60);
                    GUILayout.EndHorizontal(); GUILayout.Label("Camera resolution / camera"); GUILayout.BeginHorizontal();
                    if (Btn("480p")) Tracker.SetInt("resH", 480); if (Btn("720p")) Tracker.SetInt("resH", 720);
                    if (Btn("Back cam")) Tracker.SetInt("facing", 0); if (Btn("Front cam")) Tracker.SetInt("facing", 1);
                    GUILayout.EndHorizontal();
                    if (Btn("Restart tracking to apply")) Tracker.Restart();
                    break;
                case Page.Color:
                    GUILayout.Label("Hold the light so it fills the middle of the camera picture, then save.");
                    float[] c = Tracker.LiveColor();
                    if (c != null && c.Length >= 6)
                    {
                        swatch.SetPixel(0, 0, new Color(c[0], c[1], c[2])); swatch.Apply();
                        GUI.DrawTexture(GUILayoutUtility.GetRect(200, 50), swatch);
                        GUILayout.Label("hue " + c[3].ToString("0") + "  sat " + c[4].ToString("0.00") + "  val " + c[5].ToString("0.00"));
                    }
                    GUILayout.BeginHorizontal(); if (Btn("Save LEFT light")) Tracker.SaveColor(0); if (Btn("Save RIGHT light")) Tracker.SaveColor(1); GUILayout.EndHorizontal();
                    GUILayout.Label("Set Mode to Color lights or Both. Dim the room for best results.");
                    break;
                case Page.Pads:
                    GUILayout.BeginHorizontal(); GUILayout.Label("Controller number:"); if (Btn("1")) joy = 1; if (Btn("2")) joy = 2; GUILayout.EndHorizontal();
                    GUILayout.Label("Detected type: " + ControllerInput.TypeOf(joy) + (ranging ? "   (capturing range, move sticks and press triggers)" : ""));
                    if (Btn("1. Save centers (sticks untouched)")) ControllerInput.SaveCenters(joy);
                    if (Btn("2. Capture range (5 s: roll sticks, press triggers)")) { ControllerInput.BeginRange(joy); ranging = true; rangeEnd = Time.time + 5f; }
                    foreach (PadAction a in System.Enum.GetValues(typeof(PadAction)))
                    {
                        string t = mapping.HasValue && mapping.Value == a ? "Press the button now..." : "Set " + a + " button (now: " + ControllerInput.MappedButton(joy, a) + ")";
                        if (Btn(t)) mapping = a;
                    }
                    for (int ax = 1; ax <= 6; ax++) GUILayout.Label("axis " + ax + ": raw " + ControllerInput.RawAxis(joy, ax).ToString("+0.00;-0.00") + "  calibrated " + ControllerInput.Axis(joy, ax).ToString("+0.00;-0.00"));
                    if (Btn("Reset this controller")) ControllerInput.Reset(joy);
                    break;
            }
            GUILayout.EndArea();
        }
        static bool Btn(string t) { return GUILayout.Button(t, GUILayout.Height(52)); }
    }
}
