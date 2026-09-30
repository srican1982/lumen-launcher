package com.lumen.launcher.data

object DockPlacement {
    data class Result(val dock: List<String>, val home: List<String>)
    fun place(dock: List<String>, home: List<String>, key: String, slot: Int, capacity: Int): Result {
        val next = dock.take(capacity).toMutableList()
        val target = slot.coerceIn(0, capacity - 1).coerceAtMost(next.size)
        val source = next.indexOf(key)
        if (source == target) return Result(next, home)
        val occupant = next.getOrNull(target)
        if (source >= 0) {
            if (occupant != null) { next[source] = occupant; next[target] = key }
            else { next.removeAt(source); next.add(key) }
        } else if (occupant == null) next.add(key) else next[target] = key
        val homeNext = home.toMutableList()
        val homeSource = homeNext.indexOf(key)
        if (homeSource >= 0) {
            if (occupant == null) homeNext.removeAt(homeSource)
            else {
                val oldPosition = homeNext.indexOf(occupant)
                homeNext[homeSource] = occupant
                if (oldPosition >= 0 && oldPosition != homeSource) homeNext[oldPosition] = key
            }
        }
        return Result(next, homeNext)
    }
}
