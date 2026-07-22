package moe.shizuku.manager.shell

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.HtmlText
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.StepRow
import moe.shizuku.manager.utils.CustomTabsHelper

class ShellTutorialActivity : AppActivity() {

    companion object {

        private const val SH_NAME = "rish"
        private const val DEX_NAME = "rish_shizuku.dex"
    }

    private val openDocumentsTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
            if (tree == null) return@registerForActivityResult

            val cr = contentResolver
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val child =
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

            cr.query(
                child,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1)
                    if (name == SH_NAME || name == DEX_NAME) {
                        DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }

            fun writeToDocument(name: String) {
                DocumentsContract.createDocument(contentResolver, doc, "application/octet-stream", name)?.runCatching {
                    cr.openOutputStream(this)?.let { assets.open(name).copyTo(it) }
                }
            }

            writeToDocument(SH_NAME)
            writeToDocument(DEX_NAME)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val shName = SH_NAME
            val dexName = DEX_NAME

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.home_terminal_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_help_outline_24dp,
                            title = stringResource(R.string.home_terminal_title),
                            body = HtmlText(R.string.rish_description, shName),
                            onClick = { CustomTabsHelper.launchUrlOrCopy(this@ShellTutorialActivity, Helps.RISH.get()) }
                        ) {
                            FilledTonalButton(
                                onClick = { CustomTabsHelper.launchUrlOrCopy(this@ShellTutorialActivity, Helps.RISH.get()) }
                            ) {
                                Text(stringResource(R.string.home_adb_button_view_help))
                            }
                        }
                    }
                    item {
                        StepRow(
                            number = 1,
                            title = HtmlText(R.string.terminal_tutorial_1, shName, dexName),
                            body = stringResource(R.string.terminal_tutorial_1_description),
                            action = {
                                Button(onClick = { openDocumentsTree.launch(null) }) {
                                    Text(stringResource(R.string.terminal_export_files))
                                }
                            }
                        )
                    }
                    item {
                        StepRow(
                            number = 2,
                            title = HtmlText(R.string.terminal_tutorial_2, shName),
                            body = HtmlText(
                                R.string.terminal_tutorial_2_description,
                                "Termux",
                                "PKG",
                                "com.termux",
                                "com.termux"
                            )
                        )
                    }
                    item {
                        StepRow(
                            number = 3,
                            title = HtmlText(R.string.terminal_tutorial_3, "sh $shName"),
                            body = HtmlText(R.string.terminal_tutorial_3_description, shName, "PATH")
                        )
                    }
                }
            }
        }
    }
}
