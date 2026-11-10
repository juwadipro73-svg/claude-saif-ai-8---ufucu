/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.live.LiveCodingBridge
import org.junit.Assert.*
import org.junit.Test

class LiveCodingBridgeTest {
    @Test fun onlyEditingRequestsUseTheCodingBackend() {
        assertTrue(LiveCodingBridge.shouldHandle("ek snake game banao"))
        assertTrue(LiveCodingBridge.shouldHandle("Please fix this repo bug"))
        assertTrue(LiveCodingBridge.shouldHandle("मेरे लिए एक ऐप बनाओ"))
        assertFalse(LiveCodingBridge.shouldHandle("What is the weather like today?"))
    }
}
