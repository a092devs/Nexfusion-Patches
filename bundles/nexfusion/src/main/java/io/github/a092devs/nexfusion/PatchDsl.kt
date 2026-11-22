package io.github.a092devs.nexfusion

fun patch(
    name: String = "",
    description: String = "",
    use: Boolean = name.isEmpty() || name.startsWith("<"),
    func: PatchExecutor.() -> Unit
): Patch = Patch(name, description, if (name.isEmpty() || name.startsWith("<")) true else use, func)
