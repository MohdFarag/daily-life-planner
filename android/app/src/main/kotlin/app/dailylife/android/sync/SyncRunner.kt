package app.dailylife.android.sync

import android.util.Log
import app.dailylife.android.data.Repository
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How a sync run ended, for the background worker. */
enum class SyncResult {
    DONE,
    NOT_SIGNED_IN,
    RETRY,
    FAILED,
}

/** One full sync: records out and in, then media up and down. Runs one at a time. */
class SyncRunner(
    private val accounts: AccountStore,
    api: ApiClient,
    repository: Repository,
    mediaDir: File,
) {
    private val engine = SyncEngine(api, repository, accounts)
    private val media = MediaSync(api, repository, mediaDir)
    private val lock = Mutex()

    suspend fun run(): SyncResult = lock.withLock {
        if (accounts.account.value == null) return SyncResult.NOT_SIGNED_IN
        accounts.syncStarted()
        var error: String? = ERROR_UNEXPECTED
        var cancelled = false
        try {
            engine.run()
            media.upload()
            engine.run() // sends the media ids recorded by the upload
            media.download()
            error = null
            SyncResult.DONE
        } catch (e: SignedOutException) {
            error = ERROR_SIGNED_OUT
            SyncResult.NOT_SIGNED_IN
        } catch (e: IOException) {
            error = ERROR_OFFLINE
            SyncResult.RETRY
        } catch (e: ApiException) {
            error = e.message
            if (e.status >= 500 || e.status == 429) SyncResult.RETRY else SyncResult.FAILED
        } catch (e: CancellationException) {
            cancelled = true // stopped (e.g. replaced by a newer request): neither done nor failed
            throw e
        } catch (e: Exception) {
            // A response the app cannot read, or a broken invariant: report it, keep the app alive.
            Log.e(TAG, "Sync failed", e)
            SyncResult.FAILED
        } finally {
            // Always clears "running", whatever ended the run.
            if (cancelled) accounts.syncStopped() else accounts.syncFinished(error)
        }
    }

    companion object {
        /** Status codes the account screen translates. */
        const val ERROR_OFFLINE = "offline"
        const val ERROR_SIGNED_OUT = "signedOut"
        const val ERROR_UNEXPECTED = "unexpected"
        private const val TAG = "SyncRunner"
    }
}
