package it.lgdb.biblenotes.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefSpan

sealed interface OpenBibleResult {
    data object OpenedInJwLibrary : OpenBibleResult
    data object OpenedInBrowser : OpenBibleResult
    data object FallbackToJwOrg : OpenBibleResult
    data object NoAppAvailable : OpenBibleResult
}

object JwLinkOpener {

    fun openReference(
        context: Context,
        ref: BibleRefSpan,
        openWithJwLibrary: Boolean,
        language: AppLanguage
    ): OpenBibleResult {
        if (openWithJwLibrary) {
            val primaryUri = Uri.parse(JwLinkBuilder.buildJwLibraryUrl(ref, language))
            if (tryStartViewIntent(context, primaryUri)) {
                return OpenBibleResult.OpenedInJwLibrary
            }
            val legacyUri = Uri.parse(JwLinkBuilder.buildJwLibraryLegacyUrl(ref, language))
            if (tryStartViewIntent(context, legacyUri)) {
                return OpenBibleResult.OpenedInJwLibrary
            }

            val fallbackUri = Uri.parse(JwLinkBuilder.buildJwOrgUrl(ref, language))
            return if (tryStartViewIntent(context, fallbackUri)) {
                OpenBibleResult.FallbackToJwOrg
            } else {
                OpenBibleResult.NoAppAvailable
            }
        }

        val webUri = Uri.parse(JwLinkBuilder.buildJwOrgUrl(ref, language))
        return if (tryStartViewIntent(context, webUri)) {
            OpenBibleResult.OpenedInBrowser
        } else {
            OpenBibleResult.NoAppAvailable
        }
    }

    fun openExternalUrl(context: Context, rawUrl: String): Boolean {
        val normalized = SpanAdjuster.normalizeLinkUrl(rawUrl)
        if (!SpanAdjuster.isValidLinkUrl(normalized)) return false
        val uri = Uri.parse(normalized)
        return tryStartViewIntent(context, uri)
    }

    private fun tryStartViewIntent(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
