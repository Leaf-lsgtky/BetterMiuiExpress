package com.moefactory.bettermiuiexpress.base

import android.util.Log
import kotlinx.coroutines.flow.flow

@Suppress("FunctionName")
inline fun <RequestType> NetworkBoundResource(
    crossinline fetch: suspend () -> RequestType,
    crossinline onFetchFailed: (Throwable) -> Unit = { }
) = flow {
    val data = try {
        val result = fetch()
        Result.success(result)
    } catch (throwable: Throwable) {
        Log.e("BetterMiuiExpress", "Network fetch failed", throwable)
        onFetchFailed(throwable)
        Result.failure(throwable)
    }

    emit(data)
}
