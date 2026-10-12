package nebula.service

import nebula.protocol.NebulaPlayer

data class ServiceInstance(
    val serviceName: String,
    val hostPort: Int,
    val containerId: String,
    val token: String,
    val status: ServiceInstanceStatus = ServiceInstanceStatus.STARTING,
    val players: List<NebulaPlayer> = emptyList(),
    val expected: Map<String, Long> = emptyMap(),
    val statusSince: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
) {
    val connectedPlayers: Int
        get() = players.size

    fun load(now: Long): Int = players.size + expected.values.count { it > now }
}
