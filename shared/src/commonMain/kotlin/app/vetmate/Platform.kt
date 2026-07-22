package app.vetmate

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform