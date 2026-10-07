package cc.stkmn.kalplan.infrastructure.sync

import android.content.Context
import androidx.work.*
import cc.stkmn.kalplan.application.RequestFactory
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.MailFolderRef
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.infrastructure.mail.*
import cc.stkmn.kalplan.infrastructure.widget.RequestSurfaces
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = AppRepository.get(applicationContext)
        return try {
            repository.load()
            if (PlanningPolicy.syncPaused(repository.data.value.settings) && !inputData.getBoolean("manual", false)) {
                SyncScheduler.scheduleFrequent(applicationContext, repository.data.value.settings.syncMinutes)
                return Result.success()
            }
            val success = sync(applicationContext, repository)
            if (success) {
                SyncScheduler.scheduleFrequent(applicationContext, repository.data.value.settings.syncMinutes)
                Result.success()
            } else Result.retry()
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        suspend fun sync(context: Context, repository: AppRepository): Boolean = repository.syncMutex.withLock {
            repository.load()
            val providers = AccountProviders(repository)
            val reader = AngusMailReader(providers, providers)
            var success = true
            var hasMore = false
            for (account in repository.data.value.accounts.filter { it.enabled }) {
                for (folder in account.folders.distinct()) {
                    try {
                        val key = account.id + "|" + folder
                        val cursor = repository.data.value.cursors[key]
                        val batch = reader.listIncremental(MailFolderRef(account.id, folder), cursor, 100)
                        val newRequests = mutableListOf<StoredRequest>()
                        val unseen = batch.envelopes.filterNot { e -> repository.data.value.requests.any { it.id == e.stableId } }
                        val messages = if (unseen.isEmpty()) emptyList() else reader.loadBatch(MailFolderRef(account.id, folder), unseen)
                        for (item in messages) {
                            val envelope = item.envelope
                            if (item.exceededLimits) {
                                newRequests += StoredRequest(id = envelope.stableId, accountId = account.id, folder = folder,
                                    messageId = envelope.messageId, sender = envelope.sender, recipient = envelope.replyTo ?: envelope.sender,
                                    subject = envelope.subject, body = "", receivedMillis = envelope.receivedAt.toEpochMilli(),
                                    issues = listOf("mime_limits_exceeded"), unclear = true)
                                repository.log("mime", "limits_exceeded")
                                continue
                            }
                            val message = requireNotNull(item.snapshot)
                            val profile = repository.data.value.profiles.firstOrNull { it.id == account.folderProfiles[folder] }
                            val body = message.plainText ?: cc.stkmn.kalplan.extraction.MailTextNormalizer().htmlToText(message.htmlText.orEmpty())
                            newRequests += RequestFactory.create(envelope.stableId, envelope.sender, envelope.subject, body.take(512_000), envelope.receivedAt.toEpochMilli(), repository.data.value, profile)
                                .copy(accountId = account.id, folder = folder, messageId = envelope.messageId,
                                    recipient = envelope.replyTo ?: envelope.sender,
                                    attachmentMeta = message.attachments.map { StoredAttachment(it.fileName ?: "attachment", it.mimeType, it.sizeBytes, it.inline, it.partPath) })
                        }
                        // One durable write per folder batch, including cursor. No per-message full-store rewrites.
                        repository.update { current -> current.copy(
                            requests = current.requests + newRequests.filterNot { r -> current.requests.any { it.id == r.id } },
                            cursors = current.cursors + (key to batch.cursor)) }
                        val newIds = newRequests.map { it.id }
                        for (id in newIds) RequestSurfaces.notify(context, id)
                        hasMore = hasMore || batch.hasMore
                    } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) { success = false; repository.log("imap", error.javaClass.simpleName) }
                }
            }
            if (hasMore && success) SyncScheduler.continueSync(context)
            if (success) repository.update { it.copy(lastSyncMillis = System.currentTimeMillis()) }
            RequestSurfaces.updateWidgets(context)
            success
        }
    }
}
object SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun configure(context: Context, minutes: Int) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("kalplan-periodic")
        manager.cancelUniqueWork("kalplan-frequent")
        if (minutes >= 15) manager.enqueueUniquePeriodicWork("kalplan-periodic", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        else if (minutes >= 5) scheduleFrequent(context, minutes)
    }
    fun scheduleFrequent(context: Context, minutes: Int) {
        if (minutes !in 5..14) return
        WorkManager.getInstance(context).enqueueUniqueWork("kalplan-frequent", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun manual(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork("kalplan-manual", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("manual" to true)).setConstraints(constraints).build())
    fun continueSync(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork("kalplan-continuation", ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(1, TimeUnit.MINUTES).setConstraints(constraints).build())
}
