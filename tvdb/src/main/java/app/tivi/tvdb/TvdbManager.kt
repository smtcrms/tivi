/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.tvdb

import app.tivi.data.isInFuture
import app.tivi.data.simple.SimpleStorage
import app.tivi.extensions.bodyOrThrow
import app.tivi.util.AppCoroutineDispatchers
import com.uwetrottmann.thetvdb.TheTvdb
import com.uwetrottmann.thetvdb.entities.LoginData
import com.uwetrottmann.thetvdb.entities.Token
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TvdbManager @Inject constructor(
    private val dispatchers: AppCoroutineDispatchers,
    private val theTvdb: TheTvdb,
    @Named("auth") private val simpleStorage: SimpleStorage
) {
    fun init() {
        GlobalScope.launch(dispatchers.io) {
            updateToken(false)
        }
    }

    fun updateToken(forceUpdate: Boolean) {
        val expiration = Instant.ofEpochMilli(simpleStorage.getLong(STORAGE_KEY_TVDB_JWT_EXPIRE))
        var handled = false

        if (!forceUpdate && expiration.minus(3, ChronoUnit.HOURS).isInFuture()) {
            // If we're not being forced to update, and we still have at least 3 hours before expiration, skip
            return
        }
        // We still have a valid token, refresh instead
        if (expiration.isInFuture() && refreshToken()) {
            handled = true
        }
        if (!handled && login()) {
            handled = true
        }
        if (!handled) {
            clearToken()
        }
    }

    private fun refreshToken(): Boolean {
        val response = theTvdb.authentication().refreshToken().execute()
        if (response.isSuccessful) {
            saveToken(response.bodyOrThrow(), Instant.now().plus(24, ChronoUnit.HOURS))
            return true
        }
        return false
    }

    private fun login(): Boolean {
        val response = theTvdb.authentication().login(LoginData(theTvdb.apiKey())).execute()
        if (response.isSuccessful) {
            saveToken(response.bodyOrThrow(), Instant.now().plus(24, ChronoUnit.HOURS))
            return true
        }
        return false
    }

    private fun saveToken(token: Token, expiration: Instant) {
        simpleStorage.saveString(STORAGE_KEY_TVDB_JWT, token.token!!)
        simpleStorage.saveLong(STORAGE_KEY_TVDB_JWT_EXPIRE, expiration.toEpochMilli())
    }

    private fun clearToken() {
        simpleStorage.clearValue(STORAGE_KEY_TVDB_JWT)
        simpleStorage.clearValue(STORAGE_KEY_TVDB_JWT_EXPIRE)
    }

    companion object {
        private const val STORAGE_KEY_TVDB_JWT = "tvdb_jwt"
        private const val STORAGE_KEY_TVDB_JWT_EXPIRE = "tvdb_jwt_expire"
    }
}