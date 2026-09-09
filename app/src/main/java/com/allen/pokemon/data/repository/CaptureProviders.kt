package com.allen.pokemon.data.repository

import java.util.UUID
import javax.inject.Inject

fun interface CaptureClock {
    fun nowMillis(): Long
}

fun interface CaptureIdGenerator {
    fun nextId(): String
}

class SystemCaptureClock @Inject constructor() : CaptureClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class UuidCaptureIdGenerator @Inject constructor() : CaptureIdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
