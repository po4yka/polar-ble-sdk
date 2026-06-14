package com.polar.sdk.api.model.utils

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    GoldenVectorContractPolicyTest::class,
    KmpValidationAndChecklistPolicyTest::class,
    KmpSharedBoundaryPolicyTest::class
)
class GoldenVectorMigrationPolicyTest
