@file:Suppress("SpellCheckingInspection")

package com.mckimquyen.reader.ui.ext

import com.mckimquyen.reader.BuildConfig

const val GITHUB = "github"
const val FDROID = "fdroid"
const val isFdroid = BuildConfig.FLAVOR == FDROID
const val notFdroid = !isFdroid

/**
 * The update check read the latest release of the upstream project (`Ashinch/ReadYou`), so it could never see a newer version of this app,
 * and the Update button opened that project's page. The Play build is updated by the store. Turn this on only after `update_link` and
 * `github_link` point at this app's own releases.
 */
const val UPDATE_CHECK_ENABLED = false
