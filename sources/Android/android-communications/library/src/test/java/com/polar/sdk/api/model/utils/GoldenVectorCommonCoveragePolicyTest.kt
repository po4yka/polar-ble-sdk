package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoldenVectorCommonCoveragePolicyTest {

    @Test
    fun `type utility migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TypeUtilsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/type-utils/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            TYPE_UTILS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing type-utils common policy term $term" }
        }
        if (!inventory.contains("TypeUtilsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention TypeUtilsCommonPolicyTest.kt in the basic byte/type utilities row"
        }
        if (!readme.contains("TypeUtilsCommonPolicyTest")) {
            violations += "protocol/type-utils/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Type utility migration vectors must have executable shared common policy coverage before parser primitives move to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `device id migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/device-id/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            DEVICE_ID_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing device-id common policy term $term" }
        }
        if (!inventory.contains("DeviceIdCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention DeviceIdCommonPolicyTest.kt in the device ID row"
        }
        if (!readme.contains("DeviceIdCommonPolicyTest")) {
            violations += "protocol/device-id/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Device ID migration vectors must have executable shared common policy coverage before UUID/checksum logic moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `advertisement migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/AdvertisementCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/advertisement/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ADVERTISEMENT_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing advertisement common policy term $term" }
        }
        if (!inventory.contains("AdvertisementCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention AdvertisementCommonPolicyTest.kt in the advertisement row"
        }
        if (!readme.contains("AdvertisementCommonPolicyTest")) {
            violations += "protocol/advertisement/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Advertisement migration vectors must have executable shared common policy coverage before advertisement parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD settings migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdSettingsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_SETTINGS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD settings common policy term $term" }
        }
        if (!inventory.contains("PmdSettingsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdSettingsCommonPolicyTest.kt in the PMD settings row"
        }
        if (!readme.contains("PmdSettingsCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD settings policy coverage"
        }

        assertTrue(
            "PMD settings migration vectors must have executable shared common policy coverage before settings parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD control point migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdControlPointCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_CONTROL_POINT_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD control-point common policy term $term" }
        }
        if (!inventory.contains("PmdControlPointCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdControlPointCommonPolicyTest.kt in the PMD control point row"
        }
        if (!readme.contains("PmdControlPointCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD control-point policy coverage"
        }

        assertTrue(
            "PMD control-point migration vectors must have executable shared common policy coverage before control-point parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD secret migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdSecretCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_SECRET_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD secret common policy term $term" }
        }
        if (!inventory.contains("PmdSecretCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdSecretCommonPolicyTest.kt in the PMD control point row"
        }
        if (!readme.contains("PmdSecretCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD secret policy coverage"
        }

        assertTrue(
            "PMD secret migration vectors must have executable shared common policy coverage before secret strategy code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `ECG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/EcgParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ECG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing ECG common policy term $term" }
        }
        if (!inventory.contains("EcgParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention EcgParserCommonPolicyTest.kt in the ECG parser row"
        }
        if (!readme.contains("EcgParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared ECG parser policy coverage"
        }

        assertTrue(
            "ECG parser migration vectors must have executable shared common policy coverage before ECG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `ACC parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/AccParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ACC_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing ACC common policy term $term" }
        }
        if (!inventory.contains("AccParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention AccParserCommonPolicyTest.kt in the ACC parser row"
        }
        if (!readme.contains("AccParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared ACC parser policy coverage"
        }

        assertTrue(
            "ACC parser migration vectors must have executable shared common policy coverage before ACC parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `GYR parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GyrParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            GYR_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing GYR common policy term $term" }
        }
        if (!inventory.contains("GyrParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention GyrParserCommonPolicyTest.kt in the GYR parser row"
        }
        if (!readme.contains("GyrParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared GYR parser policy coverage"
        }

        assertTrue(
            "GYR parser migration vectors must have executable shared common policy coverage before GYR parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `MAG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/MagParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            MAG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing MAG common policy term $term" }
        }
        if (!inventory.contains("MagParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention MagParserCommonPolicyTest.kt in the MAG parser row"
        }
        if (!readme.contains("MagParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared MAG parser policy coverage"
        }

        assertTrue(
            "MAG parser migration vectors must have executable shared common policy coverage before MAG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PPG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PpgParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PPG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PPG common policy term $term" }
        }
        if (!inventory.contains("PpgParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PpgParserCommonPolicyTest.kt in the PPG parser row"
        }
        val ppgInventoryRow = inventory.tableRows().firstOrNull { row -> row.firstOrNull() == "PPG parser" }
        if (ppgInventoryRow == null || !ppgInventoryRow[COVERAGE_STATUS_COLUMN].startsWith("Covered")) {
            violations += "KmpCoverageInventory.md PPG parser row must stay Covered after shared compile verification"
        }
        if (ppgInventoryRow != null && !ppgInventoryRow[COVERAGE_STATUS_COLUMN].contains(":shared:jvmTest")) {
            violations += "KmpCoverageInventory.md PPG parser row must keep :shared:jvmTest compile-verification evidence"
        }
        if (ppgInventoryRow != null && !ppgInventoryRow[COVERAGE_REQUIRED_COLUMN].contains("compile-verification-gate")) {
            violations += "KmpCoverageInventory.md PPG parser row must keep compile-verification-gate migration language"
        }
        if (!readme.contains("PpgParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared PPG parser policy coverage"
        }

        assertTrue(
            "PPG parser migration vectors must have executable shared common policy coverage before PPG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PPI parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PpiParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PPI_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PPI common policy term $term" }
        }
        if (!inventory.contains("PpiParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PpiParserCommonPolicyTest.kt in the PPI parser row"
        }
        if (!readme.contains("PpiParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared PPI parser policy coverage"
        }

        assertTrue(
            "PPI parser migration vectors must have executable shared common policy coverage before PPI parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `pressure and temperature parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PressureTemperatureParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PRESSURE_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing pressure/temperature common policy term $term" }
        }
        if (!inventory.contains("PressureTemperatureParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PressureTemperatureParserCommonPolicyTest.kt in the pressure and temperature parser rows"
        }
        if (!readme.contains("PressureTemperatureParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared pressure/temperature parser policy coverage"
        }

        assertTrue(
            "Pressure and temperature parser migration vectors must have executable shared common policy coverage before scalar parser code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `offline HR parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineHrParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            OFFLINE_HR_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing offline HR common policy term $term" }
        }
        if (!inventory.contains("OfflineHrParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention OfflineHrParserCommonPolicyTest.kt in the offline HR parser row"
        }
        if (!readme.contains("OfflineHrParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared offline HR parser policy coverage"
        }

        assertTrue(
            "Offline HR parser migration vectors must have executable shared common policy coverage before offline HR parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `GNSS location parser migration vectors have executable shared ownership policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GnssLocationOwnershipCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            GNSS_LOCATION_OWNERSHIP_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing GNSS ownership common policy term $term" }
        }
        if (!inventory.contains("GnssLocationOwnershipCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention GnssLocationOwnershipCommonPolicyTest.kt in the GNSS/location parser row"
        }
        if (!inventory.contains("No direct iOS parser surface; `gnss-location-readiness.json` intentionally has no iOS consumer")) {
            violations += "KmpCoverageInventory.md must record GNSS iOS as an intentional no-parser-surface boundary, not a generic missing test"
        }
        if (inventory.contains("Missing direct Swift parser implementation/test")) {
            violations += "KmpCoverageInventory.md must not describe GNSS iOS parser ownership as a generic missing Swift test"
        }
        if (!readme.contains("GnssLocationOwnershipCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared GNSS ownership policy coverage"
        }

        assertTrue(
            "GNSS location migration vectors must have executable shared ownership policy coverage before location parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `offline recording metadata migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineRecordingMetadataCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/offline-recording/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            OFFLINE_RECORDING_METADATA_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing offline-recording metadata common policy term $term" }
        }
        if (!inventory.contains("OfflineRecordingMetadataCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention OfflineRecordingMetadataCommonPolicyTest.kt in the offline recording row"
        }
        if (!readme.contains("OfflineRecordingMetadataCommonPolicyTest")) {
            violations += "sdk/offline-recording/README.md must mention executable shared offline-recording metadata policy coverage"
        }

        assertTrue(
            "Offline-recording metadata migration vectors must have executable shared common policy coverage before metadata mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `training session migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TrainingSessionCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/training-session/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            TRAINING_SESSION_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing training-session common policy term $term" }
        }
        if (!inventory.contains("TrainingSessionCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention TrainingSessionCommonPolicyTest.kt in the training-session row"
        }
        if (!readme.contains("TrainingSessionCommonPolicyTest")) {
            violations += "sdk/training-session/README.md must mention executable shared training-session policy coverage"
        }

        assertTrue(
            "Training-session migration vectors must have executable shared common policy coverage before training-session discovery/read orchestration moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `skin temperature parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SkinTemperatureParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SKIN_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing skin-temperature common policy term $term" }
        }
        if (!inventory.contains("SkinTemperatureParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SkinTemperatureParserCommonPolicyTest.kt in the temperature parser row"
        }
        if (!readme.contains("SkinTemperatureParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared skin-temperature parser policy coverage"
        }

        assertTrue(
            "Skin-temperature parser migration vectors must have executable shared common policy coverage before skin-temperature parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `skin temperature domain migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SkinTemperatureDomainCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/skin-temperature/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SKIN_TEMPERATURE_DOMAIN_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing skin-temperature domain common policy term $term" }
        }
        if (!inventory.contains("SkinTemperatureDomainCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SkinTemperatureDomainCommonPolicyTest.kt in the skin temperature domain row"
        }
        if (!readme.contains("SkinTemperatureDomainCommonPolicyTest")) {
            violations += "sdk/skin-temperature/README.md must mention executable shared skin-temperature domain policy coverage"
        }

        assertTrue(
            "Skin-temperature domain migration vectors must have executable shared common policy coverage before public model mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `disk space migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskSpaceCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/disk-space/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            DISK_SPACE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing disk-space common policy term $term" }
        }
        if (!inventory.contains("DiskSpaceCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention DiskSpaceCommonPolicyTest.kt in the disk space row"
        }
        if (!readme.contains("DiskSpaceCommonPolicyTest")) {
            violations += "sdk/disk-space/README.md must mention executable shared disk-space policy coverage"
        }

        assertTrue(
            "Disk-space migration vectors must have executable shared common policy coverage before disk-space model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `SPo2 migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/Spo2CommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/spo2-test/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SPO2_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing SPo2 common policy term $term" }
        }
        if (!inventory.contains("Spo2CommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention Spo2CommonPolicyTest.kt in the SPo2 row"
        }
        if (!readme.contains("Spo2CommonPolicyTest")) {
            violations += "sdk/spo2-test/README.md must mention executable shared SPo2 policy coverage"
        }

        assertTrue(
            "SPo2 migration vectors must have executable shared common policy coverage before SPo2 model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `watch face migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/WatchFaceCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/watch-face/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            WATCH_FACE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing watch-face common policy term $term" }
        }
        if (!inventory.contains("WatchFaceCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention WatchFaceCommonPolicyTest.kt in the watch face row"
        }
        if (!readme.contains("WatchFaceCommonPolicyTest")) {
            violations += "sdk/watch-face/README.md must mention executable shared watch-face policy coverage"
        }

        assertTrue(
            "Watch-face migration vectors must have executable shared common policy coverage before watch-face model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `KVTX migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/KvtxCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/kvtx/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            KVTX_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing KVTX common policy term $term" }
        }
        if (!inventory.contains("KvtxCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention KvtxCommonPolicyTest.kt in the KVTX row"
        }
        if (!readme.contains("KvtxCommonPolicyTest")) {
            violations += "sdk/kvtx/README.md must mention executable shared KVTX policy coverage"
        }

        assertTrue(
            "KVTX migration vectors must have executable shared common policy coverage before KVTX script code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `D2H notification migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hNotificationCommonPolicyTest.kt")
        val streamRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hStreamRuntimePolicyCommonTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/d2h-notifications/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            D2H_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing D2H common policy term $term" }
        }
        if (!streamRuntimeTest.isFile) {
            violations += streamRuntimeTest.relativeTo(root).path
        } else {
            val streamRuntimeTestText = streamRuntimeTest.readText()
            D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> streamRuntimeTestText.contains(term) }
                .mapTo(violations) { term -> "${streamRuntimeTest.relativeTo(root).path}: missing D2H stream runtime common policy term $term" }
        }
        if (!inventory.contains("D2hNotificationCommonPolicyTest.kt") || !inventory.contains("D2hStreamRuntimePolicyCommonTest.kt")) {
            violations += "KmpCoverageInventory.md must mention D2hNotificationCommonPolicyTest.kt and D2hStreamRuntimePolicyCommonTest.kt in the D2H row"
        }
        if (!readme.contains("D2hNotificationCommonPolicyTest") || !readme.contains("D2hStreamRuntimePolicyCommonTest")) {
            violations += "sdk/d2h-notifications/README.md must mention executable shared D2H mapping and stream policy coverage"
        }

        assertTrue(
            "D2H migration vectors must have executable shared common policy coverage before D2H notification mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `activity automatic sample and daily summary migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/ActivitySummaryCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val activityReadme = root.resolve("testdata/golden-vectors/sdk/activity-samples/README.md").readText()
        val automaticReadme = root.resolve("testdata/golden-vectors/sdk/automatic-samples/README.md").readText()
        val dailyReadme = root.resolve("testdata/golden-vectors/sdk/daily-summary/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ACTIVITY_SUMMARY_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing activity/automatic/daily common policy term $term" }
        }
        if (!inventory.contains("ActivitySummaryCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention ActivitySummaryCommonPolicyTest.kt in the activity samples and summaries row"
        }
        if (!activityReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/activity-samples/README.md must mention executable shared activity policy coverage"
        }
        if (!automaticReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/automatic-samples/README.md must mention executable shared automatic-sample policy coverage"
        }
        if (!dailyReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/daily-summary/README.md must mention executable shared daily-summary policy coverage"
        }

        assertTrue(
            "Activity, automatic-sample, and daily-summary migration vectors must have executable shared common policy coverage before model mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `sleep and nightly recharge migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SleepNightlyRechargeCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val sleepReadme = root.resolve("testdata/golden-vectors/sdk/sleep/README.md").readText()
        val nightlyReadme = root.resolve("testdata/golden-vectors/sdk/nightly-recharge/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SLEEP_NIGHTLY_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing sleep/nightly common policy term $term" }
        }
        if (!inventory.contains("SleepNightlyRechargeCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SleepNightlyRechargeCommonPolicyTest.kt in the sleep/nightly row"
        }
        if (!sleepReadme.contains("SleepNightlyRechargeCommonPolicyTest")) {
            violations += "sdk/sleep/README.md must mention executable shared sleep policy coverage"
        }
        if (!nightlyReadme.contains("SleepNightlyRechargeCommonPolicyTest")) {
            violations += "sdk/nightly-recharge/README.md must mention executable shared nightly recharge policy coverage"
        }

        assertTrue(
            "Sleep and nightly recharge migration vectors must have executable shared common policy coverage before model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `user device settings migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/UserDeviceSettingsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/user-device-settings/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            USER_DEVICE_SETTINGS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing user-device-settings common policy term $term" }
        }
        if (!inventory.contains("UserDeviceSettingsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention UserDeviceSettingsCommonPolicyTest.kt in the user device settings row"
        }
        if (!readme.contains("UserDeviceSettingsCommonPolicyTest")) {
            violations += "sdk/user-device-settings/README.md must mention executable shared user-device-settings policy coverage"
        }

        assertTrue(
            "User-device-settings migration vectors must have executable shared common policy coverage before settings model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `first time use migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirstTimeUseModelsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/first-time-use/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            FIRST_TIME_USE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing first-time-use common policy term $term" }
        }
        if (!inventory.contains("FirstTimeUseModelsCommonPolicyTest.kt") || !inventory.contains("first-time-use-readiness.json")) {
            violations += "KmpCoverageInventory.md must mention FirstTimeUseModelsCommonPolicyTest.kt and first-time-use-readiness.json in the first-time-use row"
        }
        if (!readme.contains("FirstTimeUseModelsCommonPolicyTest")) {
            violations += "sdk/first-time-use/README.md must mention executable shared first-time-use policy coverage"
        }

        assertTrue(
            "First-time-use migration vectors must have executable shared common policy coverage before FTU model or facade execution moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `sd log migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SdLogModelsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/sd-log/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SD_LOG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing sd-log common policy term $term" }
        }
        if (!inventory.contains("SdLogModelsCommonPolicyTest.kt") || !inventory.contains("sd-log-readiness.json")) {
            violations += "KmpCoverageInventory.md must mention SdLogModelsCommonPolicyTest.kt and sd-log-readiness.json in the SD-log row"
        }
        if (!readme.contains("SdLogModelsCommonPolicyTest.kt")) {
            violations += "sdk/sd-log/README.md must mention executable shared SD-log policy coverage"
        }

        assertTrue(
            "SD-log migration vectors must have executable shared common policy coverage before SD-log model or facade execution moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `exercise session migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/ExerciseSessionModelsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/exercise-session/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            EXERCISE_SESSION_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing exercise-session common policy term $term" }
        }
        if (!inventory.contains("ExerciseSessionModelsCommonPolicyTest.kt") || !inventory.contains("exercise-session-readiness.json")) {
            violations += "KmpCoverageInventory.md must mention ExerciseSessionModelsCommonPolicyTest.kt and exercise-session-readiness.json in the exercise-session row"
        }
        if (!readme.contains("ExerciseSessionModelsCommonPolicyTest.kt")) {
            violations += "sdk/exercise-session/README.md must mention executable shared exercise-session policy coverage"
        }

        assertTrue(
            "Exercise-session migration vectors must have executable shared common policy coverage before exercise model or facade execution moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `available data types migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/AvailableDataTypesCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/available-data-types/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            AVAILABLE_DATA_TYPES_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing available-data-types common policy term $term" }
        }
        if (!inventory.contains("AvailableDataTypesCommonPolicyTest.kt") || !inventory.contains("available-data-types-readiness.json")) {
            violations += "KmpCoverageInventory.md must mention AvailableDataTypesCommonPolicyTest.kt and available-data-types-readiness.json in the available data types notes"
        }
        if (!readme.contains("AvailableDataTypesCommonPolicyTest.kt")) {
            violations += "sdk/available-data-types/README.md must mention executable shared available-data-types policy coverage"
        }

        assertTrue(
            "Available-data-types migration vectors must have executable shared common policy coverage before availability facade behavior moves to KMP: $violations",
            violations.isEmpty()
        )
    }
}
