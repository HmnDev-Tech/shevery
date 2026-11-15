package moe.shizuku.manager

import com.hamondev.shevery.api.SheveryApiConstants

/** Permission constants mirroring the manifest-declared API and manager permissions. */
object Manifest {

    object permission {
        const val PRIVILEGED_API = SheveryApiConstants.PERMISSION_PRIVILEGED_API
        const val MANAGER = SheveryApiConstants.PERMISSION_MANAGER
        const val API_V23 = SheveryApiConstants.LEGACY_SHIZUKU_PERMISSION
    }
}
