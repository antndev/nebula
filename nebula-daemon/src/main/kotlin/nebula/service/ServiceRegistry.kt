package nebula.service

import nebula.config.Config
import nebula.config.JoiningBehavior
import nebula.config.Service
import nebula.protocol.NebulaPlayer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ServiceRegistry {
    private val instancesByService = ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceInstance>>()

    @Synchronized
    fun register(instance: ServiceInstance) {
        instancesByService.values.forEach { it.removeIf { existing -> existing.hostPort == instance.hostPort } }
        instancesByService.computeIfAbsent(instance.serviceName) { CopyOnWriteArrayList() }.add(instance)
    }

    @Synchronized
    fun deregister(serviceName: String, containerId: String) {
        instancesByService[serviceName]?.removeIf { it.containerId == containerId }
    }

    fun serviceConnected(hostPort: Int, players: List<NebulaPlayer>): Boolean =
        update(hostPort) {
            val now = System.currentTimeMillis()
            it.copy(
                status = ServiceInstanceStatus.RUNNING,
                players = players,
                statusSince = now,
                lastActiveAt = maxOf(it.lastActiveAt, now),
            )
        }

    fun serviceDisconnected(hostPort: Int) {
        update(hostPort) {
            if (it.status == ServiceInstanceStatus.STOPPED) {
                it
            } else {
                it.copy(
                    status = ServiceInstanceStatus.STARTING,
                    players = emptyList(),
                    statusSince = System.currentTimeMillis(),
                )
            }
        }
    }

    fun markStopped(hostPort: Int) {
        update(hostPort) { it.copy(status = ServiceInstanceStatus.STOPPED, statusSince = System.currentTimeMillis()) }
    }

    fun playerExpected(hostPort: Int, uuid: String, until: Long) {
        val now = System.currentTimeMillis()
        update(hostPort) {
            it.copy(
                expected = it.expected.filterValues { expiry -> expiry > now } + (uuid to until),
                lastActiveAt = maxOf(it.lastActiveAt, until),
            )
        }
    }

    fun playerJoined(hostPort: Int, player: NebulaPlayer) {
        update(hostPort) { instance ->
            instance.copy(
                players = instance.players.filterNot { it.uuid == player.uuid } + player,
                expected = instance.expected - player.uuid,
                lastActiveAt = maxOf(instance.lastActiveAt, System.currentTimeMillis()),
            )
        }
    }

    fun playerLeft(hostPort: Int, uuid: String) {
        update(hostPort) { instance ->
            instance.copy(
                players = instance.players.filterNot { it.uuid == uuid },
                lastActiveAt = maxOf(instance.lastActiveAt, System.currentTimeMillis()),
            )
        }
    }

    @Synchronized
    private fun update(hostPort: Int, transform: (ServiceInstance) -> ServiceInstance): Boolean {
        for (instances in instancesByService.values) {
            val index = instances.indexOfFirst { it.hostPort == hostPort }
            if (index != -1) {
                instances[index] = transform(instances[index])
                return true
            }
        }
        return false
    }

    fun snapshot(): Map<String, List<ServiceInstance>> =
        instancesByService.mapValues { it.value.toList() }

    fun getInstances(serviceName: String): List<ServiceInstance> =
        instancesByService[serviceName].orEmpty().toList()

    fun getActiveInstances(serviceName: String): List<ServiceInstance> =
        getInstances(serviceName).filter { it.status != ServiceInstanceStatus.STOPPED }

    fun findPlayerInstance(uuid: String): ServiceInstance? =
        instancesByService.values.flatten().find { instance ->
            instance.players.any { it.uuid == uuid }
        }

    fun instanceByPort(hostPort: Int): ServiceInstance? =
        instancesByService.values.flatten().find { it.hostPort == hostPort }

    fun instanceByContainer(containerId: String): ServiceInstance? =
        instancesByService.values.flatten().find { it.containerId == containerId }

    fun totalActiveInstances(): Int =
        instancesByService.values.sumOf { instances ->
            instances.count { it.status != ServiceInstanceStatus.STOPPED }
        }

    fun nextAvailablePort(blocked: Set<Int>): Int? {
        val usedPorts = instancesByService.values.flatten()
            .map { it.hostPort }
            .toSet()
        return Config.NODE_PORT_RANGE.firstOrNull { it !in usedPorts && it !in blocked }
    }

    fun selectJoinTarget(service: Service): ServiceInstance {
        val now = System.currentTimeMillis()
        val candidates = getInstances(service.name).filter {
            it.status == ServiceInstanceStatus.RUNNING && it.load(now) < service.scaling.maxPlayersPerInstance
        }
        return when (service.joiningBehavior) {
            JoiningBehavior.FILL_EXISTING -> candidates.maxByOrNull { it.load(now) }
            JoiningBehavior.LEAST_PLAYERS -> candidates.minByOrNull { it.load(now) }
        } ?: error("no joinable instances are available for service '${service.name}'.")
    }
}
