package com.ykis.mob

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform