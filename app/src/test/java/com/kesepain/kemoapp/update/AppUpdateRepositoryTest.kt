package com.kesepain.kemoapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun semanticVersionWithOptionalVPrefixIsComparedNumerically() {
        assertTrue(AppUpdateRepository.compareVersions("v1.2.0", "1.1.9") > 0)
        assertTrue(AppUpdateRepository.compareVersions("1.9.9", "2.0.0") < 0)
    }

    @Test
    fun missingPatchComponentsAreTreatedAsZero() {
        assertEquals(0, AppUpdateRepository.compareVersions("v1.0.0", "1.0"))
    }

    @Test
    fun prereleaseDoesNotOverrideSameStableVersion() {
        assertTrue(AppUpdateRepository.compareVersions("v1.0.0-beta.1", "1.0.0") < 0)
    }
}
