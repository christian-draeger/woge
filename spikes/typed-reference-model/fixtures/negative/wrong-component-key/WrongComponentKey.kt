package dev.woge.spike.references.negative

import dev.woge.spike.references.TaskRow
import dev.woge.spike.references.TaskRowProps

public fun invalidComponentKey() {
    TaskRow.instance("task-1", TaskRowProps("Wrong key type", completed = false))
}
