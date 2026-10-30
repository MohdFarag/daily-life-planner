package app.dailylife.android.sync

import app.dailylife.android.data.Repository
import java.io.File
import java.io.IOException
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
        try {
            engine.run()
            media.upload()
            engine.run() // sends the media ids recorded by the upload
            media.download()
            accounts.syncFinished(null)
            SyncResult.DONE
        } catch (e: SignedOutException) {
            accounts.syncFinished(ERROR_SIGNED_OUT)
            SyncResult.NOT_SIGNED_IN
        } catch (e: IOException) {
            accounts.syncFinished(ERROR_OFFLINE)
            SyncResult.RETRY
        } catch (e: ApiException) {
            accounts.syncFinished(e.message)
            if (e.status >= 500 || e.status == 429) SyncResult.RETRY else SyncResult.FAILED
        }
    }

    companion object {
        /** Status codes the account screen translates. */
        const val ERROR_OFFLINE = "offline"
        const val ERROR_SIGNED_OUT = "signedOut"
    }
}
