using System;
using System.Net;
using System.Reflection;
using System.Net.Sockets;
using System.Text;
using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using UnityEngine;

namespace CybergrindBridge
{
    /// <summary>
    /// Tells the Cybergrind Minecraft mod when ULTRAKILL's time stands still, and when V1 is invincible (dash i-frames). ULTRAKILL's impact frames, hit-stop and parry flashes
    /// (TimeController.HitStop / TrueStop, e.g. the Jackhammer's impact) set Time.timeScale to 0 for their length, so it's watched
    /// every frame: while it's stopped, "1" goes to Minecraft every 0.2 s (over UDP on 127.0.0.1, nothing leaves the PC); when time
    /// runs again, "0". Cybergrind freezes the Minecraft world's mobs in between, and unfreezes on its own if the messages stop.
    /// </summary>
    [BepInPlugin("dev.cybergrind.bridge", "Cybergrind Bridge", "1.0.2")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static ManualLogSource Log;

        private void Awake()
        {
            Log = this.Logger;
            ConfigEntry<int> port = this.Config.Bind("Link", "Port", 25633,
                "UDP port Cybergrind listens on (127.0.0.1). Must match -Dcybergrind.bridgePort in Minecraft if you change it.");

            // the watcher gets its own object: BepInEx's own can be switched off when ULTRAKILL loads a scene, and with it a
            // plugin's Update (which is why 1.0.0 never saw a time stop)
            GameObject holder = new GameObject("Cybergrind Bridge");
            holder.hideFlags = HideFlags.HideAndDontSave;
            UnityEngine.Object.DontDestroyOnLoad(holder);
            holder.AddComponent<TimeWatcher>().Init(port.Value);
            Log.LogInfo("Cybergrind Bridge: reporting ULTRAKILL's time stops to 127.0.0.1:" + port.Value);
        }
    }

    /// <summary>Watches Time.timeScale every frame and reports stops and restarts to Minecraft.</summary>
    internal sealed class TimeWatcher : MonoBehaviour
    {
        /// <summary>Below this, ULTRAKILL's time counts as stopped (its slow-motion effects stay above it).</summary>
        private const float Stopped = 0.05f;
        private const float Heartbeat = 0.2f;

        private UdpClient udp;
        private IPEndPoint minecraft;
        private bool frozen;
        private float nextBeat;
        private int stops;
        private float since;

        internal void Init(int port)
        {
            this.udp = new UdpClient();
            this.minecraft = new IPEndPoint(IPAddress.Loopback, port);
        }

        /// <summary>The layer ULTRAKILL puts V1 on while its dash makes it invincible (NewMovement.GetHurt skips damage on it).</summary>
        private const int InvincibleLayer = 15;

        private Component v1;
        private FieldInfo hurtInvincibility;
        private float nextLook;
        private bool invincible;
        private float nextInvincibleBeat;

        /// <summary>
        /// V1's i-frames, as ULTRAKILL has them: dashing (V1 on the invincible layer) or just hurt (hurtInvincibility above 0).
        /// "i1" every 0.2 s while it lasts, "i0" when it ends: Cybergrind lets no damage through meanwhile.
        /// </summary>
        private void ReportInvincibility()
        {
            if (this.v1 == null && Time.unscaledTime >= this.nextLook)
            {
                this.nextLook = Time.unscaledTime + 1f;
                Type movement = Type.GetType("NewMovement, Assembly-CSharp");
                if (movement != null)
                {
                    this.v1 = UnityEngine.Object.FindObjectOfType(movement) as Component;
                    this.hurtInvincibility = movement.GetField("hurtInvincibility", BindingFlags.Public | BindingFlags.Instance);
                }
            }

            bool now = false;
            if (this.v1 != null)
            {
                now = this.v1.gameObject.layer == InvincibleLayer
                    || (this.hurtInvincibility != null && this.hurtInvincibility.GetValue(this.v1) is float left && left > 0f);
            }

            if (now == this.invincible && !(now && Time.unscaledTime >= this.nextInvincibleBeat))
            {
                return;
            }

            this.invincible = now;
            this.nextInvincibleBeat = Time.unscaledTime + Heartbeat;
            this.Send(now ? "i1" : "i0");
        }

        private void Update()
        {
            this.ReportInvincibility();
            bool now = Time.timeScale < Stopped;
            if (now && !this.frozen)
            {
                this.stops++;
                this.since = Time.unscaledTime;
            }
            else if (!now && this.frozen)
            {
                Plugin.Log.LogInfo("time stop " + this.stops + ": " + ((Time.unscaledTime - this.since) * 1000f).ToString("0") + " ms");
            }

            if (now == this.frozen && !(now && Time.unscaledTime >= this.nextBeat))
            {
                return;
            }

            this.frozen = now;
            this.nextBeat = Time.unscaledTime + Heartbeat;
            this.Send(now ? "1" : "0");
        }

        private void OnDestroy()
        {
            this.Send("0");
            this.udp?.Close();
        }

        private void Send(string message)
        {
            try
            {
                byte[] bytes = Encoding.ASCII.GetBytes(message);
                this.udp.Send(bytes, bytes.Length, this.minecraft);
            }
            catch (Exception)
            {
                // Minecraft not listening: nothing to do
            }
        }
    }
}
