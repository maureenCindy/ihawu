package com.ihawu.samples.serialization

import kotlin.test.Test

class MaskingSerializerSampleTest {
    @Test
    fun `maskEmployeeProfile sample compiles and runs`() {
        maskEmployeeProfile()
    }

    @Test
    fun `maskCollectionItems sample compiles and runs`() {
        maskCollectionItems()
    }

    @Test
    fun `maskFailsClosedWithoutPrincipal sample compiles and runs`() {
        maskFailsClosedWithoutPrincipal()
    }

    @Test
    fun `failClosedOnResolverError sample compiles and runs`() {
        failClosedOnResolverError()
    }
}
