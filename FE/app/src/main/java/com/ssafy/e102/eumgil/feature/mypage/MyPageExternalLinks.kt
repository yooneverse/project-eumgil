package com.ssafy.e102.eumgil.feature.mypage

import android.content.Intent
import android.net.Uri

private const val SERVICE_TERMS_URL =
    "https://www.notion.so/ryuwon-project/350a58d49be680ab9931f226486dac58"
private const val PRIVACY_POLICY_URL =
    "https://www.notion.so/ryuwon-project/350a58d49be68063bbd1f633be85badb"

internal fun createServiceTermsIntent(): Intent =
    createExternalLinkIntent(SERVICE_TERMS_URL)

internal fun createPrivacyPolicyIntent(): Intent =
    createExternalLinkIntent(PRIVACY_POLICY_URL)

private fun createExternalLinkIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
