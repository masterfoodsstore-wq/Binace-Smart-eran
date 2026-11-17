package com.example

import android.net.Uri
import com.example.data.model.AppConstants
import com.example.data.model.AppSettings
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.GitHubAssetDto
import com.example.data.repository.GitHubReleaseApi
import com.example.data.repository.GitHubReleaseDto
import com.example.util.ReferralLinkHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReferralAndUpdateTest {

    @Test
    fun `referral link generation and mobile desktop deep link capture`() {
        val link = ReferralLinkHelper.buildReferralLink("user123")
        assertEquals("https://binacesmartearn.online/signup?ref=USER123", link)

        assertEquals(
            "USER123",
            ReferralLinkHelper.extractReferralCode(Uri.parse("https://binacesmartearn.online/signup?ref=user123"))
        )
        assertEquals(
            "ALICE99",
            ReferralLinkHelper.extractReferralCode(Uri.parse("https://www.binacesmartearn.online/signup?referral=alice99"))
        )
        assertEquals(
            "MOBILE77",
            ReferralLinkHelper.extractReferralCode(Uri.parse("binacesmartearn://signup?ref=mobile77"))
        )
        assertEquals(
            "DESK88",
            ReferralLinkHelper.extractReferralCodeFromUrl("https://binacesmartearn.online/?code=desk88")
        )
        assertNull(
            ReferralLinkHelper.extractReferralCode(Uri.parse("https://untrusted-site.example/signup?ref=USER123"))
        )

        val message = ReferralLinkHelper.buildShareMessage("USER123")
        assertTrue(message.contains("https://binacesmartearn.online/signup?ref=USER123"))
        assertTrue(message.contains("simulated", ignoreCase = true))
        assertTrue(ReferralLinkHelper.whatsAppShareUrl(message).startsWith("https://wa.me/?text="))
        assertTrue(ReferralLinkHelper.telegramShareUrl(link, message).startsWith("https://t.me/share/url?"))
        assertTrue(ReferralLinkHelper.facebookShareUrl(link, message).startsWith("https://www.facebook.com/sharer/sharer.php?"))
    }

    @Test
    fun `app update version comparison and github release resolution`() = runTest {
        assertTrue(AppUpdateRepository.compareVersions("1.0.2", "1.0.0") > 0)
        assertTrue(AppUpdateRepository.compareVersions("v1.1.0", "1.0.9") > 0)
        assertEquals(0, AppUpdateRepository.compareVersions("v1.0.0", "1.0.0"))
        assertTrue(AppUpdateRepository.compareVersions("0.9.5", "1.0.0") < 0)

        assertEquals(
            "https://github.com/acme/binace-smart-earn/releases/latest/download/${AppConstants.APK_ASSET_NAME}",
            AppUpdateRepository.buildPublicLatestApkUrl("https://github.com/acme/binace-smart-earn")
        )

        val fakeApi = object : GitHubReleaseApi {
            override suspend fun getLatestRelease(owner: String, repo: String): GitHubReleaseDto {
                return GitHubReleaseDto(
                    tagName = "v1.0.3",
                    name = "BINACE SMART EARN v1.0.3",
                    body = "Improved wallet performance and signed release.",
                    htmlUrl = "https://github.com/$owner/$repo/releases/tag/v1.0.3",
                    publishedAt = "2026-11-16T10:30:00Z",
                    assets = listOf(
                        GitHubAssetDto(
                            name = "binace-smart-earn-v1.0.3.apk",
                            browserDownloadUrl = "https://github.com/$owner/$repo/releases/download/v1.0.3/binace-smart-earn-v1.0.3.apk",
                            size = 18_450_000L
                        ),
                        GitHubAssetDto(
                            name = AppConstants.APK_ASSET_NAME,
                            browserDownloadUrl = "https://github.com/$owner/$repo/releases/latest/download/${AppConstants.APK_ASSET_NAME}",
                            size = 18_450_000L
                        )
                    )
                )
            }
        }

        val repo = AppUpdateRepository(fakeApi)
        val result = repo.fetchLatestUpdate(
            settings = AppSettings(githubRepoSlug = "acme/binace-smart-earn"),
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            embeddedRepoSlug = ""
        )

        assertTrue(result.isUpdateAvailable)
        assertEquals("1.0.3", result.latestVersionName)
        assertEquals(18_450_000L, result.apkFileSizeBytes)
        assertEquals("2026-11-16T10:30:00Z", result.publishedAt)
        assertEquals(
            "https://github.com/acme/binace-smart-earn/releases/latest/download/${AppConstants.APK_ASSET_NAME}",
            result.downloadUrl
        )
        assertEquals("Improved wallet performance and signed release.", result.releaseNotes)
    }
}
