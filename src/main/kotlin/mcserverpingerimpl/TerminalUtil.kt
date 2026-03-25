package com.github.onlaait.mcserverpingerimpl

object TerminalUtil {

    private val clearTerminalTask =
        if (System.getProperty("os.name").contains("Windows")) {
            {
                ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
            }
        } else {
            {
                print("\u001b[H\u001b[2J")
                System.out.flush()
            }
        }

    fun clearTerminal() {
        clearTerminalTask.invoke()
    }
}