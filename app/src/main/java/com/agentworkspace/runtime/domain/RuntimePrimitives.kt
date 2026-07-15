package com.agentworkspace.runtime.domain

import java.util.UUID
import javax.inject.Inject

fun interface RuntimeClock {
    fun nowMillis(): Long
}

fun interface IdGenerator {
    fun newId(): String
}

class SystemRuntimeClock @Inject constructor() : RuntimeClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class UuidGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
