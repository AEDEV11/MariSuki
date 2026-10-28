package com.aesprt.marisuki.feature.printing

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

object DebugPrintSimulation {
    private enum class Failure { NONE, DISCONNECTED, PRINT_FAILED }
    private val nextFailure = AtomicReference(Failure.NONE)

    fun simulateDisconnection() { nextFailure.set(Failure.DISCONNECTED) }
    fun simulatePrintFailure() { nextFailure.set(Failure.PRINT_FAILED) }

    fun beforePrint() {
        when (nextFailure.getAndSet(Failure.NONE)) {
            Failure.NONE -> Unit
            Failure.DISCONNECTED -> throw IOException("Debug simulation: printer is disconnected.")
            Failure.PRINT_FAILED -> throw IOException("Debug simulation: print job failed.")
        }
    }
}
