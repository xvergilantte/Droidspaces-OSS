package com.droidspaces.app.util

import com.topjohnwu.superuser.Shell

object SymlinkInstaller {

    /** Returns true if the symlink exists at the module system/bin path. */
    fun isSymlinkEnabled(): Boolean =
        Shell.cmd("test -L '${Constants.SYSTEM_BIN_SYMLINK_PATH}'").exec().isSuccess

    /** Creates system/bin dir, symlink, and sets permissions. Returns success. */
    fun enable(): Boolean {
        val binDir = Constants.MODULE_SYSTEM_BIN_PATH
        val link = Constants.SYSTEM_BIN_SYMLINK_PATH
        val target = Constants.DROIDSPACES_BINARY_PATH

        Shell.cmd("mkdir -p '$binDir'").exec().takeIf { it.isSuccess } ?: return false
        Shell.cmd("rm -f '$link'").exec()
        Shell.cmd("ln -sf '$target' '$link'").exec().takeIf { it.isSuccess } ?: return false
        Shell.cmd("chmod 755 '$link'").exec()
        return true
    }

    /** Nukes the entire system/bin folder from the module. Returns success. */
    fun disable(): Boolean =
        Shell.cmd("rm -rf '${Constants.MODULE_SYSTEM_BIN_PATH}'").exec().isSuccess
}
