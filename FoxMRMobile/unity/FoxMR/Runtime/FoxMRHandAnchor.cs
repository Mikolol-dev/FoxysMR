using UnityEngine;
using UnityEngine.Events;

namespace FoxMR
{
    public enum GrabMode { Pinch, Fist, Either, ControllerButton }

    /// <summary>Put this on the object that should follow a hand (e.g. the game's hand/controller object). It moves in front of the head camera.</summary>
    public class HandAnchor : MonoBehaviour
    {
        public int slot = 0;                     // 0 = left, 1 = right
        public Transform head;                   // the VR camera; defaults to Camera.main
        public float reachX = 0.45f, reachY = 0.35f; // meters from center at the edges of the camera picture
        public float nearDepth = 0.25f, farDepth = 0.7f;
        public GrabMode grabMode = GrabMode.Either;
        public int controllerNumber = 1;         // used when grabMode = ControllerButton (1 or 2)
        public UnityEvent onGrab, onRelease;
        public bool Tracked { get; private set; }
        public bool Grabbing { get; private set; }

        void Start() { Tracker.EnsureStarted(); if (head == null && Camera.main != null) head = Camera.main.transform; }

        void Update()
        {
            if (head == null) return;
            HandState h = Tracker.Hands[slot];
            Tracked = h.valid;
            if (h.valid)
            {
                float depth = Mathf.Lerp(farDepth, nearDepth, Mathf.InverseLerp(0.08f, 0.3f, h.size));
                Vector3 local = new Vector3((h.pos.x - 0.5f) * 2f * reachX, (0.5f - h.pos.y) * 2f * reachY, depth);
                transform.position = head.TransformPoint(local);
                transform.rotation = head.rotation;
            }
            bool g = false;
            switch (grabMode)
            {
                case GrabMode.Pinch: g = h.valid && h.pinch; break;
                case GrabMode.Fist: g = h.valid && h.closed && h.source == 1; break;
                case GrabMode.Either: g = h.valid && (h.pinch || (h.closed && h.source == 1)); break;
                case GrabMode.ControllerButton: g = ControllerInput.Pressed(controllerNumber, PadAction.Trigger); break;
            }
            if (g && !Grabbing) onGrab.Invoke();
            if (!g && Grabbing) onRelease.Invoke();
            Grabbing = g;
        }
    }
}
