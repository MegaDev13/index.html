package dev.neonos

import android.app.Activity
import android.os.*
import android.view.FrameMetrics
import android.view.Window
import org.json.JSONObject

class Diagnostics(private val activity: Activity) {
    private var frames=0;private var total=0.0;private var slow=0;private var worst=0.0
    private var start=0L;private var cpuStart=0L;private var frozenElapsed=0L;private var frozenCpu=0L
    private val listener=Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val ms=metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1e6
        val hz=activity.display?.refreshRate?:60f
        frames++;total+=ms;worst=maxOf(worst,ms);if(ms>1000.0/hz)slow++
    }
    var running=false;private set
    fun start() { if(running)return;frames=0;total=0.0;slow=0;worst=0.0;frozenElapsed=0;frozenCpu=0;start=SystemClock.elapsedRealtime();cpuStart=android.os.Process.getElapsedCpuTime();activity.window.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()));running=true }
    fun stop() { if(running) { activity.window.removeOnFrameMetricsAvailableListener(listener);frozenElapsed=SystemClock.elapsedRealtime()-start;frozenCpu=android.os.Process.getElapsedCpuTime()-cpuStart;running=false } }
    fun report(): JSONObject {
        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        val elapsed=if(running)SystemClock.elapsedRealtime()-start else frozenElapsed
        val cpu=if(running)android.os.Process.getElapsedCpuTime()-cpuStart else frozenCpu
        return JSONObject().put("app","NEON OS ${BuildConfig.VERSION_NAME}").put("device",Build.MODEL).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("build",Build.DISPLAY).put("fingerprint",Build.FINGERPRINT)
            .put("scope","Processo NEON nesta sessão; não é benchmark do aparelho nem latência biométrica")
            .put("session_ms",elapsed).put("cpu_process_ms",cpu).put("cpu_one_core_percent",if(elapsed>0)cpu*100.0/elapsed else 0.0)
            .put("pss_kib",memory.totalPss).put("java_heap_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            .put("frames",frames).put("mean_frame_ms",if(frames>0)total/frames else 0.0).put("worst_frame_ms",worst).put("frames_over_display_budget",slow)
            .put("current_hz",activity.display?.refreshRate?:0).put("authentication_method","not_observed")
    }
}
