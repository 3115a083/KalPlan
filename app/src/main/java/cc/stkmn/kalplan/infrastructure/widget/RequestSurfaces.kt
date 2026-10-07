package cc.stkmn.kalplan.infrastructure.widget

import android.Manifest
import android.app.*
import android.appwidget.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cc.stkmn.kalplan.MainActivity
import cc.stkmn.kalplan.R
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.infrastructure.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object RequestSurfaces {
    private const val CHANNEL = "requests"
    fun intent(context: Context, id: String, action: String = "open"): Intent = Intent(context, MainActivity::class.java)
        .setData(Uri.Builder().scheme("kalplan").authority("request").appendPath(id).appendPath(action).build())
        .putExtra("request_id", id).putExtra("request_action", action)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun pending(context: Context, id: String, action: String = "open") = PendingIntent.getActivity(context, 0,
        intent(context, id, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun notify(context: Context, id: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val request = AppRepository.get(context).data.value.requests.firstOrNull { it.id == id && it.pending } ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "KalPlan", NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val german = Locale.getDefault().language == "de"
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(request.subject).setContentText(time(request))
            .setContentIntent(pending(context, id)).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle("KalPlan").setContentText(if (german) "Neue Anfrage" else "New request").build())
            .addAction(0, if (german) "Annehmen" else "Accept", pending(context, id, "accept"))
            .addAction(0, if (german) "Ablehnen" else "Decline", pending(context, id, "decline")).build()
        manager.notify(id.hashCode(), notification)
    }
    fun cancelNotification(context: Context, id: String) { context.getSystemService(NotificationManager::class.java).cancel(id.hashCode()) }
    fun updateWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, KalPlanWidget::class.java))
        ids.forEach { update(context, manager, it) }
        @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
    fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val german = Locale.getDefault().language == "de"
        val views = RemoteViews(context.packageName, R.layout.widget_requests)
        val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        views.setTextViewText(R.id.widget_title, "KalPlan")
        views.setTextViewText(R.id.widget_refresh, if (german) "Aktualisieren" else "Refresh")
        val refresh = Intent(context, KalPlanWidget::class.java).setAction("cc.stkmn.kalplan.REFRESH").setData(Uri.parse("kalplan://widget/$id"))
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val repository = AppRepository.get(context)
        val state = repository.data.value
        val requests = state.requests.filter { it.pending }.sortedByDescending { PlanningPolicy.priority(it, state.settings).score }
        views.setTextViewText(R.id.widget_empty, if (german) "Keine offenen Anfragen" else "No pending requests")
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        @Suppress("DEPRECATION") views.setRemoteAdapter(R.id.widget_list, Intent(context, RequestWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("small", height < 200).setData(Uri.parse("kalplan://widget-list/$id/${height < 200}")))
        // Immutable collection template is insufficient for fill-in data. It is explicit and Activity-only.
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, id,
            Intent(context, MainActivity::class.java).setAction("cc.stkmn.kalplan.WIDGET_OPEN"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        manager.updateAppWidget(id, views)
    }
    fun time(request: StoredRequest): String = request.candidate?.startMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
    } ?: if (Locale.getDefault().language == "de") "Termin unklar" else "Unclear appointment"
}
class KalPlanWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { RequestSurfaces.update(context, manager, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { RequestSurfaces.update(context, manager, id) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "cc.stkmn.kalplan.REFRESH") { SyncScheduler.manual(context); RequestSurfaces.updateWidgets(context) }
    }
}
@Suppress("DEPRECATION")
class RequestWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent.getBooleanExtra("small", false))
    private class Factory(private val context: Context, private val small: Boolean) : RemoteViewsFactory {
        private var requests = emptyList<StoredRequest>()
        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            val repository = AppRepository.get(context)
            runCatching { runBlocking { repository.load() } }
            val state = repository.data.value
            requests = state.requests.filter { it.pending }.sortedByDescending { PlanningPolicy.priority(it, state.settings).score }.let { if (small) it.take(2) else it }
        }
        override fun onDestroy() = Unit
        override fun getCount() = requests.size
        override fun getViewAt(position: Int): RemoteViews? {
            val r = requests.getOrNull(position) ?: return null
            val german = Locale.getDefault().language == "de"
            return RemoteViews(context.packageName, R.layout.widget_request_row).apply {
                setTextViewText(R.id.widget_row_title, RequestSurfaces.time(r) + " · P${PlanningPolicy.priority(r, AppRepository.get(context).data.value.settings).rank}")
                setTextViewText(R.id.widget_row_detail, r.subject.take(80) + "\n" + r.labels.joinToString(" · ") + if (r.unclear) " · ?" else "")
                setOnClickFillInIntent(R.id.widget_row, RequestSurfaces.intent(context, r.id))
                setTextViewText(R.id.widget_accept, if (german) "Annehmen" else "Accept")
                setTextViewText(R.id.widget_decline, if (german) "Ablehnen" else "Decline")
                setOnClickFillInIntent(R.id.widget_accept, RequestSurfaces.intent(context, r.id, "accept"))
                setOnClickFillInIntent(R.id.widget_decline, RequestSurfaces.intent(context, r.id, "decline"))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = requests.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
    }
}
