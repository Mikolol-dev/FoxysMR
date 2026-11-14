#if UNITY_EDITOR
using UnityEditor;
using UnityEngine;

namespace FoxMR.EditorTools
{
    /// <summary>Adds the joystick axes FoxMR_J1_A1..A10 and FoxMR_J2_A1..A10 to the legacy Input Manager.</summary>
    public static class InputSetup
    {
        [MenuItem("FoxMR/Setup Input Axes")]
        public static void Run()
        {
            var obj = new SerializedObject(AssetDatabase.LoadAllAssetsAtPath("ProjectSettings/InputManager.asset")[0]);
            var axes = obj.FindProperty("m_Axes");
            int added = 0;
            for (int j = 1; j <= 2; j++)
                for (int a = 1; a <= 10; a++)
                {
                    string name = "FoxMR_J" + j + "_A" + a;
                    if (Exists(axes, name)) continue;
                    axes.arraySize++; obj.ApplyModifiedProperties();
                    var p = axes.GetArrayElementAtIndex(axes.arraySize - 1);
                    p.FindPropertyRelative("m_Name").stringValue = name;
                    p.FindPropertyRelative("descriptiveName").stringValue = "";
                    p.FindPropertyRelative("descriptiveNegativeName").stringValue = "";
                    p.FindPropertyRelative("negativeButton").stringValue = "";
                    p.FindPropertyRelative("positiveButton").stringValue = "";
                    p.FindPropertyRelative("altNegativeButton").stringValue = "";
                    p.FindPropertyRelative("altPositiveButton").stringValue = "";
                    p.FindPropertyRelative("gravity").floatValue = 0f;
                    p.FindPropertyRelative("dead").floatValue = 0f;
                    p.FindPropertyRelative("sensitivity").floatValue = 1f;
                    p.FindPropertyRelative("snap").boolValue = false;
                    p.FindPropertyRelative("invert").boolValue = false;
                    p.FindPropertyRelative("type").intValue = 2;      // joystick axis
                    p.FindPropertyRelative("axis").intValue = a - 1;
                    p.FindPropertyRelative("joyNum").intValue = j;
                    added++;
                }
            obj.ApplyModifiedProperties();
            Debug.Log("FoxMR: added " + added + " input axes.");
        }
        static bool Exists(SerializedProperty axes, string name)
        {
            for (int i = 0; i < axes.arraySize; i++)
                if (axes.GetArrayElementAtIndex(i).FindPropertyRelative("m_Name").stringValue == name) return true;
            return false;
        }
    }
}
#endif
