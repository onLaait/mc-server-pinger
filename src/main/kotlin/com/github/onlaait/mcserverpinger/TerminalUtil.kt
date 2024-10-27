package com.github.onlaait.mcserverpinger

object TerminalUtil {
    private val clearTerminalTask =
        if (System.getProperty("os.name").contains("Windows")) {
            {
                ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
            }
        } else {
            {
                Runtime.getRuntime().exec("clear")
                print("\\033[H\\033[2J")
                System.out.flush()
            }
        }

    fun clearTerminal() {
        clearTerminalTask.invoke()
    }
}