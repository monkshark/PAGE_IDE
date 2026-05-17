package page.app

data class RunConfig(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
) {
    fun isRunnable(): Boolean = command.isNotBlank()
}

data class RunConfigsState(
    val configs: List<RunConfig> = emptyList(),
    val activeId: String? = null,
) {
    val active: RunConfig? get() = configs.firstOrNull { it.id == activeId }

    fun add(config: RunConfig): RunConfigsState {
        if (configs.any { it.id == config.id }) return this
        return copy(configs = configs + config, activeId = activeId ?: config.id)
    }

    fun remove(id: String): RunConfigsState {
        val newConfigs = configs.filterNot { it.id == id }
        val newActive = when {
            activeId != id -> activeId
            newConfigs.isEmpty() -> null
            else -> newConfigs.first().id
        }
        return copy(configs = newConfigs, activeId = newActive)
    }

    fun update(config: RunConfig): RunConfigsState {
        val idx = configs.indexOfFirst { it.id == config.id }
        if (idx < 0) return this
        return copy(configs = configs.toMutableList().also { it[idx] = config })
    }

    fun select(id: String): RunConfigsState {
        if (configs.none { it.id == id }) return this
        return copy(activeId = id)
    }
}
