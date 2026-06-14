package com.polar.androidcommunications.api.ble.model.advertisement

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE_16BIT_UUID
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PFTP_SERVICE_16BIT_UUID
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import com.polar.sdk.api.model.PolarSdkModelAdapter
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileReader

internal class BleAdvertisementContentTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    private lateinit var bleAdvertisementContent: BleAdvertisementContent

    @Before
    fun setUp() {
        bleAdvertisementContent = BleAdvertisementContent()
    }

    @Test
    fun `test parse name from complete local name`() {
        // Arrange
        val testInputString = "ABC EDE aa123459"
        val map = hashMapOf<BleUtils.AD_TYPE, ByteArray>()
        map[BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE] = testInputString.toByteArray()

        // Act
        val name = bleAdvertisementContent.getNameFromAdvData(map)
        bleAdvertisementContent.processName(name)

        // Assert
        Assert.assertEquals(testInputString, bleAdvertisementContent.name)
        Assert.assertTrue(bleAdvertisementContent.polarDeviceType.isEmpty())
        Assert.assertTrue(bleAdvertisementContent.polarDeviceId.isEmpty())
    }

    @Test
    fun `test parse name from complete local name when Polar device`() {
        // Arrange
        val testInputString = "Polar GritX Pro aa123459"
        val map = hashMapOf<BleUtils.AD_TYPE, ByteArray>()
        map[BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE] = testInputString.toByteArray()

        // Act
        val name = bleAdvertisementContent.getNameFromAdvData(map)
        bleAdvertisementContent.processName(name)

        // Assert
        Assert.assertEquals(testInputString, bleAdvertisementContent.name)
        Assert.assertEquals("GritX Pro", bleAdvertisementContent.polarDeviceType)
        Assert.assertEquals("aa123459", bleAdvertisementContent.polarDeviceId)
    }

    @Test
    fun `test parse name from short local name when Polar device`() {
        // Arrange
        val testInputString = "Polar GritX Pro aa123459"
        val map = hashMapOf<BleUtils.AD_TYPE, ByteArray>()
        map[BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT] = testInputString.toByteArray()

        // Act
        val name = bleAdvertisementContent.getNameFromAdvData(map)
        bleAdvertisementContent.processName(name)

        // Assert
        Assert.assertEquals(testInputString, bleAdvertisementContent.name)
        Assert.assertEquals("GritX Pro", bleAdvertisementContent.polarDeviceType)
        Assert.assertEquals("aa123459", bleAdvertisementContent.polarDeviceId)
    }

    @Test
    fun `test parse name from short local name when Polar device with custom brand name`() {
        // Arrange
        val testInputString = "Custom Bio Sense cbs123456"
        val map = hashMapOf<BleUtils.AD_TYPE, ByteArray>()
        map[BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT] = testInputString.toByteArray()
        bleAdvertisementContent.advertisementDeviceNamePrefix = "Custom"
        // Act
        val name = bleAdvertisementContent.getNameFromAdvData(map)
        bleAdvertisementContent.processName(name)

        // Assert
        Assert.assertEquals(testInputString, bleAdvertisementContent.name)
        Assert.assertEquals("Bio Sense", bleAdvertisementContent.polarDeviceType)
        Assert.assertEquals("cbs123456", bleAdvertisementContent.polarDeviceId)
    }

    @Test
    fun `test parse hr from manufacturer data without hr`() {
        // Arrange
        val onlyGpbManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(),
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )

        val onlyBpb: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to onlyGpbManufacturerData)

        // Act
        bleAdvertisementContent.processAdvManufacturerData(onlyBpb, bleAdvertisementContent.polarHrAdvertisement)

        // Assert
        Assert.assertNotNull(bleAdvertisementContent.polarHrAdvertisement)
        Assert.assertFalse(bleAdvertisementContent.polarHrAdvertisement.isPresent)
    }

    @Test
    fun `test parse hr from manufacturer data SAGRFC23 format`() {
        // Arrange
        val gpbAndHrManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(),
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7a.toByte(), 0x01.toByte(), 0x03.toByte(), 0x33.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val gpbAndHr: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to gpbAndHrManufacturerData)

        // Act && Assert
        bleAdvertisementContent.processAdvManufacturerData(gpbAndHr, bleAdvertisementContent.polarHrAdvertisement)
        Assert.assertNotNull(bleAdvertisementContent.polarHrAdvertisement)
        Assert.assertTrue(bleAdvertisementContent.polarHrAdvertisement.isPresent)
    }

    @Test
    fun `manufacturer HR payload extraction delegates to shared policy`() {
        val gpbAndHrManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(),
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7a.toByte(), 0x01.toByte(), 0x03.toByte(), 0x33.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val malformedGpbMissingLength = byteArrayOf(0x6b.toByte(), 0x00.toByte(), 0x40.toByte())

        Assert.assertArrayEquals(byteArrayOf(0x33.toByte(), 0x00.toByte(), 0x00.toByte()), PolarSdkModelAdapter.polarManufacturerHrPayloads(gpbAndHrManufacturerData).single())
        Assert.assertTrue(PolarSdkModelAdapter.polarManufacturerHrPayloads(malformedGpbMissingLength).isEmpty())
    }

    @Test
    fun `test parse hr from manufacturer data SAGRFC31 format`() {
        // Arrange
        val onlyHrManufacturerData = byteArrayOf(0x6b.toByte(), 0x00.toByte(), 0x2b.toByte(), 0x0b.toByte(), 0xb6.toByte(), 0xac.toByte())
        val onlyHr: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to onlyHrManufacturerData)

        // Act
        bleAdvertisementContent.processAdvManufacturerData(onlyHr, bleAdvertisementContent.polarHrAdvertisement)

        // Assert
        Assert.assertNotNull(bleAdvertisementContent.polarHrAdvertisement)
        Assert.assertTrue(bleAdvertisementContent.polarHrAdvertisement.isPresent)
    }

    @Test
    fun `test parse hr from manufacturer data not Polar adv`() {
        // Arrange
        val nonPolarManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x01.toByte(), // 0x006B is Polar manufacturer Id, 0x016B is not Polar
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7a.toByte(), 0x01.toByte(), 0x03.toByte(), 0x33.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val nonPolar: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to nonPolarManufacturerData)

        // Act
        bleAdvertisementContent.processAdvManufacturerData(nonPolar, bleAdvertisementContent.polarHrAdvertisement)

        // Assert
        Assert.assertNotNull(bleAdvertisementContent.polarHrAdvertisement)
        Assert.assertFalse(bleAdvertisementContent.polarHrAdvertisement.isPresent)
    }

    @Test
    fun `test processing of consecutive adv packets`() {
        // Arrange
        val gpbAndHrManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(),
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7a.toByte(), 0x01.toByte(), 0x03.toByte(), 0x33.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val onlyGpbManufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(), 0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val emptyManufacturerData = byteArrayOf()

        val gbpAndHr: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to gpbAndHrManufacturerData)
        val onlyBpb: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to onlyGpbManufacturerData)
        val emptyManufacturer: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to emptyManufacturerData)

        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(gbpAndHr, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertEquals(true, bleAdvertisementContent.polarHrAdvertisement.isPresent)
        bleAdvertisementContent.processAdvertisementData(onlyBpb, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertEquals(false, bleAdvertisementContent.polarHrAdvertisement.isPresent)
        bleAdvertisementContent.processAdvertisementData(gbpAndHr, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertEquals(true, bleAdvertisementContent.polarHrAdvertisement.isPresent)
        bleAdvertisementContent.processAdvertisementData(emptyManufacturer, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertEquals(false, bleAdvertisementContent.polarHrAdvertisement.isPresent)
    }

    @Test
    fun `test process RSSI when less than 7 RSSI values arrived`() {
        // Arrange
        val rssiValues = listOf(0, 1, 2, 3, 4, 99)

        // Act
        for (rssi in rssiValues) {
            bleAdvertisementContent.processRssi(rssi)
        }

        // Assert
        Assert.assertEquals(rssiValues.last(), bleAdvertisementContent.rssi)
        Assert.assertEquals(rssiValues.last(), bleAdvertisementContent.medianRssi)
    }

    @Test
    fun `test process RSSI when more than 7 RSSI values arrived`() {
        // Arrange
        val rssiValues = listOf(10, 21, 2, 5, 9, 0, 8, 1, 8, 2)
        val median = 5 // 0, 1, 2, !5!, 8, 8, 9,

        // Act
        for (rssi in rssiValues) {
            bleAdvertisementContent.processRssi(rssi)
        }
        // Assert
        Assert.assertEquals(rssiValues.last(), bleAdvertisementContent.rssi)
        Assert.assertEquals(median, bleAdvertisementContent.medianRssi)
    }

    @Test
    fun `test adv contains service`() {
        // Arrange
        val services = byteArrayOf(0x0d.toByte(), 0x18.toByte(), 0xee.toByte(), 0xfe.toByte())
        val servicesAdvData: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE to services)
        val singleServiceAdvData: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE to byteArrayOf(0x0d.toByte(), 0x18.toByte()))
        val emptyServicesAdvData: HashMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE to byteArrayOf())

        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(servicesAdvData, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertTrue(bleAdvertisementContent.containsService(PFTP_SERVICE_16BIT_UUID))
        Assert.assertTrue(bleAdvertisementContent.containsService(HR_SERVICE_16BIT_UUID))
        bleAdvertisementContent.processAdvertisementData(emptyServicesAdvData, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertFalse(bleAdvertisementContent.containsService(PFTP_SERVICE_16BIT_UUID))
        Assert.assertFalse(bleAdvertisementContent.containsService(HR_SERVICE_16BIT_UUID))
        bleAdvertisementContent.processAdvertisementData(singleServiceAdvData, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertFalse(bleAdvertisementContent.containsService(PFTP_SERVICE_16BIT_UUID))
        Assert.assertTrue(bleAdvertisementContent.containsService(HR_SERVICE_16BIT_UUID))
    }

    @Test
    fun `test advertisement data is updated and cleared`() {
        // Arrange
        val adType16bitMoreData = byteArrayOf(0x0d.toByte(), 0x18.toByte(), 0xee.toByte(), 0xfe.toByte())
        val adType16bitMore: MutableMap<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE to adType16bitMoreData)

        val manufacturerData1 = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(), 0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val adTypeManufacturerSpecific1: Map<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to manufacturerData1)

        val manufacturerData2 = byteArrayOf(
            0x00.toByte(), 0xFF.toByte()
        )
        val adTypeManufacturerSpecific2: Map<BleUtils.AD_TYPE, ByteArray> = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to manufacturerData2)

        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(adType16bitMore, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertTrue(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))
        Assert.assertEquals(adType16bitMoreData, bleAdvertisementContent.advertisementData[BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE])

        Assert.assertTrue(bleAdvertisementContent.advertisementDataAll.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))
        Assert.assertEquals(adType16bitMoreData, bleAdvertisementContent.advertisementDataAll[BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE])

        bleAdvertisementContent.processAdvertisementData(adTypeManufacturerSpecific1, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertTrue(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC))
        Assert.assertEquals(manufacturerData1, bleAdvertisementContent.advertisementData[BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC])
        Assert.assertFalse(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))

        Assert.assertTrue(bleAdvertisementContent.advertisementDataAll.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))
        Assert.assertEquals(adType16bitMoreData, bleAdvertisementContent.advertisementDataAll[BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE])
        Assert.assertTrue(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC))
        Assert.assertEquals(manufacturerData1, bleAdvertisementContent.advertisementData[BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC])

        bleAdvertisementContent.processAdvertisementData(adTypeManufacturerSpecific2, BleUtils.EVENT_TYPE.ADV_IND, 0)
        Assert.assertTrue(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC))
        Assert.assertEquals(manufacturerData2, bleAdvertisementContent.advertisementData[BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC])
        Assert.assertFalse(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))

        Assert.assertTrue(bleAdvertisementContent.advertisementDataAll.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))
        Assert.assertEquals(adType16bitMoreData, bleAdvertisementContent.advertisementDataAll[BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE])
        Assert.assertTrue(bleAdvertisementContent.advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC))
        Assert.assertEquals(manufacturerData2, bleAdvertisementContent.advertisementData[BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC])
    }

    @Test
    fun advertisementGoldenVectors_matchAndroidBehavior() {
        val vectors = loadAdvertisementVectors()
        Assert.assertTrue("Expected advertisement golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            setUp()
            if (vector.getAsJsonObject("platforms")?.get("android")?.asBoolean == false) {
                return@forEach
            }
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val caseId = vector.get("id").asString

            if (input.has("deviceNamePrefix")) {
                bleAdvertisementContent.advertisementDeviceNamePrefix = input.get("deviceNamePrefix").asString
            }

            if (input.has("localName")) {
                val map = hashMapOf(
                    BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to input.get("localName").asString.toByteArray()
                )
                bleAdvertisementContent.processAdvertisementData(map, BleUtils.EVENT_TYPE.ADV_IND, 0)

                if (expected.has("name")) {
                    Assert.assertEquals(caseId, expected.get("name").asString, bleAdvertisementContent.name)
                }
                if (expected.has("deviceType")) {
                    Assert.assertEquals(caseId, expected.get("deviceType").asString, bleAdvertisementContent.polarDeviceType)
                }
                if (expected.has("deviceId")) {
                    Assert.assertEquals(caseId, expected.get("deviceId").asString, bleAdvertisementContent.polarDeviceId)
                }

                val androidExpectations = vector
                    .getAsJsonObject("platformExpectations")
                    ?.getAsJsonObject("android")
                if (androidExpectations?.has("deviceId") == true) {
                    Assert.assertEquals(caseId, androidExpectations.get("deviceId").asString, bleAdvertisementContent.polarDeviceId)
                }
            }

            if (input.has("manufacturerDataHex")) {
                val map = hashMapOf(
                    BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to input.get("manufacturerDataHex").asString.hexToByteArray()
                )
                bleAdvertisementContent.processAdvertisementData(map, BleUtils.EVENT_TYPE.ADV_IND, 0)

                if (expected.has("hrPresent")) {
                    Assert.assertEquals(caseId, expected.get("hrPresent").asBoolean, bleAdvertisementContent.polarHrAdvertisement.isPresent)
                }
            }

            if (input.has("services16Hex")) {
                val services = input.getAsJsonArray("services16Hex")
                    .flatMap { service ->
                        val value = service.asString.toInt(16)
                        listOf((value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())
                    }
                    .toByteArray()
                val map = hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE to services)
                bleAdvertisementContent.processAdvertisementData(map, BleUtils.EVENT_TYPE.ADV_IND, 0)

                expected.getAsJsonObject("containsServices")?.entrySet()?.forEach { expectation ->
                    Assert.assertEquals(caseId, expectation.value.asBoolean, bleAdvertisementContent.containsService(expectation.key))
                }
            }

            if (input.has("rssiSequence")) {
                input.getAsJsonArray("rssiSequence").forEach { rssi ->
                    bleAdvertisementContent.processRssi(rssi.asInt)
                }

                if (expected.has("rssi")) {
                    Assert.assertEquals(caseId, expected.get("rssi").asInt, bleAdvertisementContent.rssi)
                }
                if (expected.has("medianRssi")) {
                    Assert.assertEquals(caseId, expected.get("medianRssi").asInt, bleAdvertisementContent.medianRssi)
                }
            }
        }
    }

    @Test
    fun `advertisement golden vectors follow neutral shared vector shape`() {
        loadAdvertisementVectors().forEach { vector ->
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.has("android"))
            Assert.assertTrue(id, platforms.has("ios"))
            Assert.assertTrue(id, platforms.has("common"))
        }
    }

    @Test
    fun `advertisement readiness manifest is pinned for shared parser ownership`() {
        val manifest = loadAdvertisementReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("advertisement-readiness", manifest.get("id").asString)
        Assert.assertEquals("advertisementReadiness", input.get("kind").asString)
        Assert.assertEquals(
            listOf(
                "protocol/advertisement/custom-prefix-local-name.json",
                "protocol/advertisement/manufacturer-hr-sagrfc23.json",
                "protocol/advertisement/manufacturer-no-hr.json",
                "protocol/advertisement/manufacturer-non-polar.json",
                "protocol/advertisement/manufacturer-polar-gpb-missing-length-platform-policy.json",
                "protocol/advertisement/manufacturer-polar-id-only.json",
                "protocol/advertisement/manufacturer-polar-truncated-hr-candidate.json",
                "protocol/advertisement/manufacturer-polar-unknown-gpb-segment.json",
                "protocol/advertisement/manufacturer-unknown-company.json",
                "protocol/advertisement/non-polar-local-name-platform-difference.json",
                "protocol/advertisement/polar-local-name.json",
                "protocol/advertisement/rssi-median-seven-sample-window.json",
                "protocol/advertisement/service-uuid-membership.json",
                "protocol/advertisement/seven-digit-local-name.json"
            ),
            policyPaths
        )
        val expectedFamilies = listOf(
            "polar-local-name-parsing",
            "custom-prefix-local-name-parsing",
            "seven-digit-device-id-assembly",
            "non-polar-local-name-platform-decision",
            "manufacturer-polar-hr-presence",
            "manufacturer-no-hr-policy",
            "manufacturer-non-polar-policy",
            "manufacturer-unknown-company-policy",
            "manufacturer-unknown-segment-policy",
            "malformed-gpb-missing-length-policy",
            "malformed-truncated-hr-candidate-policy",
            "service-uuid-membership",
            "rssi-median-seven-sample-window",
            "platform-advertisement-vector-reference-gate",
            "compile-verification-gate"
        )
        Assert.assertEquals(expectedFamilies, requiredFamilies)
        Assert.assertEquals(expectedFamilies, coveredFamilies)
        Assert.assertEquals(
            "Advertisement parsing shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS advertisement tests continue to reference the same vectors, Polar and custom-prefix local-name parsing, seven-digit device ID assembly, non-Polar local-name platform decisions, manufacturer HR presence and absence, non-Polar and unknown company behavior, unknown Polar segment handling, malformed GPB missing-length and truncated HR-candidate policies, service UUID membership, RSSI median calculation, and compile verification remain explicit before production advertisement parsing moves.",
            expected.get("commonDecision").asString
        )
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContentTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("BleAdvertisementContentTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.AdvertisementCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadAdvertisementVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/advertisement")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString != "advertisementReadiness" }
    }

    private fun loadAdvertisementReadinessManifest(): JsonObject {
        val manifestFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/advertisement/advertisement-readiness.json")
        FileReader(manifestFile).use { reader ->
            return JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
