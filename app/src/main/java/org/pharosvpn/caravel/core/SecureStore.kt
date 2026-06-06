// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureStore persists the account passphrase for the logged-in cloud session —
 * so "Sync now" is one tap and survives restart. The Android half of the
 * cross-platform keystore contract (docs/cloud-sync.md §4): the passphrase is the
 * ONLY thing stored, in a Keystore-backed [EncryptedSharedPreferences] (never a
 * plain file, never argv). One item = one account. "Log out" deletes it.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "pharos_account",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun storePassphrase(secret: String) {
        prefs.edit().putString(KEY_PASS, secret).apply()
    }

    fun readPassphrase(): String? = prefs.getString(KEY_PASS, null)

    fun clearPassphrase() {
        prefs.edit().remove(KEY_PASS).apply()
    }

    val hasCredential: Boolean get() = readPassphrase() != null

    private companion object {
        const val KEY_PASS = "account-passphrase"
    }
}
