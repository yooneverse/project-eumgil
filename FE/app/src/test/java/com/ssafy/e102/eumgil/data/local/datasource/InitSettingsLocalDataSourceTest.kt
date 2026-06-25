package com.ssafy.e102.eumgil.data.local.datasource

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ssafy.e102.eumgil.feature.onboarding.MobilitySubtype
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class InitSettingsLocalDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `changing primary user type preserves accepted terms and clears type specific progress`() =
        runTest {
            val dataSource = createDataSource()

            dataSource.savePrimaryUserType(DEFAULT_SCOPE, PrimaryUserType.MOBILITY_IMPAIRED.routeValue)
            dataSource.saveMobilitySubtype(DEFAULT_SCOPE, MobilitySubtype.MANUAL_WHEELCHAIR.routeValue)
            dataSource.saveLowVisionFollowUpCompleted(DEFAULT_SCOPE, isCompleted = true)
            dataSource.saveLocationTermsAgreement(
                scopeKey = DEFAULT_SCOPE,
                isLocationTermsAgreed = true,
                isPrivacyPolicyAgreed = true,
            )

            dataSource.savePrimaryUserType(DEFAULT_SCOPE, PrimaryUserType.LOW_VISION.routeValue)

            val settings = dataSource.getInitSettings(DEFAULT_SCOPE)
            assertEquals(PrimaryUserType.LOW_VISION.routeValue, settings.selectedPrimaryUserType)
            assertNull(settings.selectedMobilitySubtype)
            assertFalse(settings.isLowVisionFollowUpCompleted)
            assertTrue(settings.isLocationTermsAgreed)
            assertTrue(settings.isPrivacyPolicyAgreed)
        }

    @Test
    fun `saving low vision type again clears stale mobility subtype and keeps follow up flag`() =
        runTest {
            val dataSource = createDataSource()

            dataSource.savePrimaryUserType(DEFAULT_SCOPE, PrimaryUserType.LOW_VISION.routeValue)
            dataSource.saveMobilitySubtype(DEFAULT_SCOPE, MobilitySubtype.MANUAL_WHEELCHAIR.routeValue)
            dataSource.saveLowVisionFollowUpCompleted(DEFAULT_SCOPE, isCompleted = true)

            dataSource.savePrimaryUserType(DEFAULT_SCOPE, PrimaryUserType.LOW_VISION.routeValue)

            val settings = dataSource.getInitSettings(DEFAULT_SCOPE)
            assertEquals(PrimaryUserType.LOW_VISION.routeValue, settings.selectedPrimaryUserType)
            assertNull(settings.selectedMobilitySubtype)
            assertTrue(settings.isLowVisionFollowUpCompleted)
        }

    @Test
    fun `scoped settings stay isolated between accounts`() =
        runTest {
            val dataSource = createDataSource()

            dataSource.savePrimaryUserType(FIRST_SCOPE, PrimaryUserType.MOBILITY_IMPAIRED.routeValue)
            dataSource.saveMobilitySubtype(FIRST_SCOPE, MobilitySubtype.MANUAL_WHEELCHAIR.routeValue)
            dataSource.saveLocationTermsAgreement(
                scopeKey = FIRST_SCOPE,
                isLocationTermsAgreed = true,
                isPrivacyPolicyAgreed = false,
            )

            dataSource.savePrimaryUserType(SECOND_SCOPE, PrimaryUserType.LOW_VISION.routeValue)
            dataSource.saveLowVisionFollowUpCompleted(SECOND_SCOPE, isCompleted = true)

            val firstSettings = dataSource.getInitSettings(FIRST_SCOPE)
            val secondSettings = dataSource.getInitSettings(SECOND_SCOPE)

            assertEquals(PrimaryUserType.MOBILITY_IMPAIRED.routeValue, firstSettings.selectedPrimaryUserType)
            assertEquals(MobilitySubtype.MANUAL_WHEELCHAIR.routeValue, firstSettings.selectedMobilitySubtype)
            assertTrue(firstSettings.isLocationTermsAgreed)
            assertFalse(firstSettings.isPrivacyPolicyAgreed)

            assertEquals(PrimaryUserType.LOW_VISION.routeValue, secondSettings.selectedPrimaryUserType)
            assertNull(secondSettings.selectedMobilitySubtype)
            assertTrue(secondSettings.isLowVisionFollowUpCompleted)
            assertFalse(secondSettings.isLocationTermsAgreed)
        }

    @Test
    fun `clearing one scope does not remove another scope onboarding progress`() =
        runTest {
            val dataSource = createDataSource()

            dataSource.savePrimaryUserType(FIRST_SCOPE, PrimaryUserType.MOBILITY_IMPAIRED.routeValue)
            dataSource.saveMobilitySubtype(FIRST_SCOPE, MobilitySubtype.MANUAL_WHEELCHAIR.routeValue)
            dataSource.savePrimaryUserType(SECOND_SCOPE, PrimaryUserType.LOW_VISION.routeValue)
            dataSource.saveLowVisionFollowUpCompleted(SECOND_SCOPE, isCompleted = true)

            dataSource.clearInitSettings(FIRST_SCOPE)

            val clearedSettings = dataSource.getInitSettings(FIRST_SCOPE)
            val preservedSettings = dataSource.getInitSettings(SECOND_SCOPE)

            assertNull(clearedSettings.selectedPrimaryUserType)
            assertNull(clearedSettings.selectedMobilitySubtype)
            assertFalse(clearedSettings.isLowVisionFollowUpCompleted)
            assertFalse(clearedSettings.isLocationTermsAgreed)
            assertFalse(clearedSettings.isPrivacyPolicyAgreed)

            assertEquals(PrimaryUserType.LOW_VISION.routeValue, preservedSettings.selectedPrimaryUserType)
            assertTrue(preservedSettings.isLowVisionFollowUpCompleted)
        }

    private fun TestScope.createDataSource(): InitSettingsLocalDataSource {
        val file = File(temporaryFolder.newFolder(), "init_settings.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            )

        return InitSettingsLocalDataSource(dataStore)
    }

    private companion object {
        private const val DEFAULT_SCOPE = "user::default"
        private const val FIRST_SCOPE = "user::first"
        private const val SECOND_SCOPE = "user::second"
    }
}
