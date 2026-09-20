package workbench.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.nio.file.Path

/**
 * Attaches an AWT DropTarget to a Window to intercept desktop file drops.
 */
@Composable
fun FileDropHandler(
    window: Window?,
    enabled: Boolean = true,
    onDragStateChanged: (Boolean) -> Unit = {},
    onFilesDropped: (List<Path>) -> Unit
) {
    DisposableEffect(window, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose {}

        val dropTarget = DropTarget(window, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    onDragStateChanged(true)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
            }

            override fun dragExit(dte: DropTargetEvent) {
                onDragStateChanged(false)
            }

            override fun drop(dtde: DropTargetDropEvent) {
                onDragStateChanged(false)
                try {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        @Suppress("UNCHECKED_CAST")
                        val fileList = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                        if (!fileList.isNullOrEmpty()) {
                            val paths = fileList.map { it.toPath() }
                            onFilesDropped(paths)
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.rejectDrop()
                } catch (e: Exception) {
                    dtde.rejectDrop()
                }
            }
        })

        onDispose {
            window.dropTarget = null
        }
    }
}
