package page.editor

internal enum class BufferKind { ORIGINAL, ADDED }

internal class PieceBuffer(val text: String) {
    val lineStarts: IntArray = computeLineStarts(text)

    companion object {
        fun computeLineStarts(s: String): IntArray {
            val starts = ArrayList<Int>()
            starts.add(0)
            for (i in s.indices) {
                if (s[i] == '\n') starts.add(i + 1)
            }
            return starts.toIntArray()
        }
    }
}

internal data class Piece(
    val kind: BufferKind,
    val start: Int,
    val length: Int,
    val lineFeedCount: Int,
)

internal enum class NodeColor { RED, BLACK }

internal class PieceNode(var piece: Piece) {
    var color: NodeColor = NodeColor.RED
    var parent: PieceNode = NIL
    var left: PieceNode = NIL
    var right: PieceNode = NIL
    var leftSubtreeLength: Int = 0
    var leftSubtreeLineCount: Int = 0

    val isNil: Boolean get() = this === NIL
}

private val NIL: PieceNode = run {
    val n = PieceNode(Piece(BufferKind.ORIGINAL, 0, 0, 0))
    n.color = NodeColor.BLACK
    n.parent = n
    n.left = n
    n.right = n
    n
}

class PieceTree(initial: String = "") {
    private val original = PieceBuffer(initial)
    private val addedSb = StringBuilder()

    private var root: PieceNode = NIL
    private var totalLength: Int = 0
    private var totalLineFeeds: Int = 0

    init {
        if (initial.isNotEmpty()) {
            val piece = Piece(
                kind = BufferKind.ORIGINAL,
                start = 0,
                length = initial.length,
                lineFeedCount = original.lineStarts.size - 1,
            )
            root = PieceNode(piece).also { it.color = NodeColor.BLACK }
            totalLength = piece.length
            totalLineFeeds = piece.lineFeedCount
        }
    }

    val length: Int get() = totalLength
    val lineCount: Int get() = totalLineFeeds + 1

    fun text(): String {
        val sb = StringBuilder(totalLength)
        appendSubtree(root, sb)
        return sb.toString()
    }

    private fun appendSubtree(node: PieceNode, sb: StringBuilder) {
        if (node.isNil) return
        appendSubtree(node.left, sb)
        sb.append(pieceText(node.piece))
        appendSubtree(node.right, sb)
    }

    private fun pieceText(p: Piece): CharSequence = when (p.kind) {
        BufferKind.ORIGINAL -> original.text.substring(p.start, p.start + p.length)
        BufferKind.ADDED -> addedSb.substring(p.start, p.start + p.length)
    }

    private fun pieceCharAt(p: Piece, indexInPiece: Int): Char = when (p.kind) {
        BufferKind.ORIGINAL -> original.text[p.start + indexInPiece]
        BufferKind.ADDED -> addedSb[p.start + indexInPiece]
    }

    fun insert(offset: Int, text: String) {
        require(offset in 0..totalLength) { "offset $offset out of bounds (length=$totalLength)" }
        if (text.isEmpty()) return

        val addedStart = addedSb.length
        addedSb.append(text)
        val lf = countLineFeeds(text)
        val newPiece = Piece(BufferKind.ADDED, addedStart, text.length, lf)

        if (root.isNil) {
            root = PieceNode(newPiece).also { it.color = NodeColor.BLACK }
            totalLength += text.length
            totalLineFeeds += lf
            return
        }

        val located = locate(offset)
        val node = located.node
        val withinPiece = located.offsetInPiece

        if (withinPiece == 0) {
            insertBefore(node, newPiece)
        } else if (withinPiece == node.piece.length) {
            insertAfter(node, newPiece)
        } else {
            val original = node.piece
            val rightLen = original.length - withinPiece
            val leftPart = sliceFront(original, withinPiece)
            val rightPart = sliceBack(original, rightLen)
            updateNodePiece(node, leftPart)
            val rightNode = insertAfter(node, rightPart)
            insertBefore(rightNode, newPiece)
        }

        totalLength += text.length
        totalLineFeeds += lf
    }

    fun delete(start: Int, end: Int) {
        require(start in 0..totalLength) { "start $start out of bounds (length=$totalLength)" }
        require(end in start..totalLength) { "end $end out of bounds (start=$start, length=$totalLength)" }
        if (start == end) return

        val from = locate(start)
        val to = locate(end - 1)

        if (from.node === to.node) {
            val node = from.node
            val piece = node.piece
            val leftKeep = from.offsetInPiece
            val rightKeep = piece.length - (to.offsetInPiece + 1)
            val deletedLen = piece.length - leftKeep - rightKeep
            val deletedLF = countLineFeedsInPieceSlice(piece, leftKeep, leftKeep + deletedLen)
            when {
                leftKeep == 0 && rightKeep == 0 -> deleteNode(node)
                leftKeep == 0 -> updateNodePiece(node, sliceBack(piece, rightKeep))
                rightKeep == 0 -> updateNodePiece(node, sliceFront(piece, leftKeep))
                else -> {
                    val leftPart = sliceFront(piece, leftKeep)
                    val rightPart = sliceBack(piece, rightKeep)
                    updateNodePiece(node, leftPart)
                    insertAfter(node, rightPart)
                }
            }
            totalLength -= deletedLen
            totalLineFeeds -= deletedLF
            return
        }

        val middle = ArrayList<PieceNode>()
        var cur = successor(from.node)
        while (cur !== to.node && !cur.isNil) {
            middle.add(cur)
            cur = successor(cur)
        }

        var deletedLen = 0
        var deletedLF = 0

        val firstPiece = from.node.piece
        val leftKeep = from.offsetInPiece
        val firstRemoved = firstPiece.length - leftKeep
        deletedLen += firstRemoved
        deletedLF += countLineFeedsInPieceSlice(firstPiece, leftKeep, firstPiece.length)

        val lastPiece = to.node.piece
        val rightKeep = lastPiece.length - (to.offsetInPiece + 1)
        val lastRemoved = lastPiece.length - rightKeep
        deletedLen += lastRemoved
        deletedLF += countLineFeedsInPieceSlice(lastPiece, 0, lastRemoved)

        for (n in middle) {
            deletedLen += n.piece.length
            deletedLF += n.piece.lineFeedCount
        }

        if (leftKeep == 0) deleteNode(from.node)
        else updateNodePiece(from.node, sliceFront(firstPiece, leftKeep))

        for (n in middle) deleteNode(n)

        if (rightKeep == 0) deleteNode(to.node)
        else updateNodePiece(to.node, sliceBack(lastPiece, rightKeep))

        totalLength -= deletedLen
        totalLineFeeds -= deletedLF
    }

    fun lineAt(line: Int): String {
        require(line in 0 until lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        val start = lineStartOffset(line)
        val end = lineEndOffset(line)
        if (start >= end) return ""
        val sb = StringBuilder(end - start)
        appendRange(start, end, sb)
        return sb.toString()
    }

    fun offsetOf(line: Int, col: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        val start = lineStartOffset(line)
        val end = lineEndOffset(line)
        require(col in 0..(end - start)) { "col $col out of bounds (line length=${end - start})" }
        return start + col
    }

    fun lineColOf(offset: Int): LineCol {
        require(offset in 0..totalLength) { "offset $offset out of bounds (length=$totalLength)" }
        if (root.isNil) return LineCol(0, 0)
        if (offset == totalLength) {
            val lastLineStart = lineStartOffset(totalLineFeeds)
            return LineCol(totalLineFeeds, totalLength - lastLineStart)
        }

        var node = root
        var rel = offset
        var lineAccum = 0
        while (!node.isNil) {
            if (rel < node.leftSubtreeLength) {
                node = node.left
            } else if (rel < node.leftSubtreeLength + node.piece.length) {
                lineAccum += node.leftSubtreeLineCount
                val inPiece = rel - node.leftSubtreeLength
                val (lineIn, colIn) = lineColInPiece(node.piece, inPiece)
                return if (lineIn == 0) {
                    val prefixCol = lineStartColAtBoundary(node, colIn)
                    LineCol(lineAccum, prefixCol)
                } else {
                    LineCol(lineAccum + lineIn, colIn)
                }
            } else {
                lineAccum += node.leftSubtreeLineCount + node.piece.lineFeedCount
                rel -= node.leftSubtreeLength + node.piece.length
                node = node.right
            }
        }
        return LineCol(lineAccum, 0)
    }

    private fun lineStartColAtBoundary(node: PieceNode, colInPiece: Int): Int {
        var col = colInPiece
        var prev = predecessor(node)
        while (!prev.isNil && prev.piece.lineFeedCount == 0) {
            col += prev.piece.length
            prev = predecessor(prev)
        }
        if (!prev.isNil) {
            col += tailLengthAfterLastLF(prev.piece)
        }
        return col
    }

    private fun tailLengthAfterLastLF(p: Piece): Int {
        var i = p.length - 1
        while (i >= 0) {
            if (pieceCharAt(p, i) == '\n') return p.length - 1 - i
            i--
        }
        return p.length
    }

    private fun lineColInPiece(p: Piece, inPiece: Int): Pair<Int, Int> {
        var line = 0
        var lastLF = -1
        for (i in 0 until inPiece) {
            if (pieceCharAt(p, i) == '\n') {
                line++
                lastLF = i
            }
        }
        return line to (inPiece - lastLF - 1)
    }

    private fun lineStartOffset(line: Int): Int {
        if (line == 0) return 0
        if (line >= lineCount) return totalLength

        var node = root
        var lineRemaining = line
        var offsetAccum = 0
        while (!node.isNil) {
            if (lineRemaining <= node.leftSubtreeLineCount) {
                node = node.left
            } else {
                lineRemaining -= node.leftSubtreeLineCount
                if (lineRemaining <= node.piece.lineFeedCount) {
                    val nthLFInPiece = nthLFOffset(node.piece, lineRemaining)
                    return offsetAccum + node.leftSubtreeLength + nthLFInPiece + 1
                }
                lineRemaining -= node.piece.lineFeedCount
                offsetAccum += node.leftSubtreeLength + node.piece.length
                node = node.right
            }
        }
        return totalLength
    }

    private fun lineEndOffset(line: Int): Int {
        return if (line + 1 >= lineCount) totalLength
        else lineStartOffset(line + 1) - 1
    }

    private fun nthLFOffset(p: Piece, n: Int): Int {
        var seen = 0
        for (i in 0 until p.length) {
            if (pieceCharAt(p, i) == '\n') {
                seen++
                if (seen == n) return i
            }
        }
        return p.length - 1
    }

    private fun appendRange(start: Int, end: Int, out: StringBuilder) {
        if (start >= end) return
        val from = locate(start)
        val to = locate(end - 1)
        if (from.node === to.node) {
            val s = from.offsetInPiece
            val e = to.offsetInPiece + 1
            appendPieceSlice(from.node.piece, s, e, out)
            return
        }
        appendPieceSlice(from.node.piece, from.offsetInPiece, from.node.piece.length, out)
        var cur = successor(from.node)
        while (cur !== to.node && !cur.isNil) {
            out.append(pieceText(cur.piece))
            cur = successor(cur)
        }
        appendPieceSlice(to.node.piece, 0, to.offsetInPiece + 1, out)
    }

    private fun appendPieceSlice(p: Piece, s: Int, e: Int, out: StringBuilder) {
        when (p.kind) {
            BufferKind.ORIGINAL -> out.append(original.text, p.start + s, p.start + e)
            BufferKind.ADDED -> out.append(addedSb, p.start + s, p.start + e)
        }
    }

    private data class Located(val node: PieceNode, val offsetInPiece: Int)

    private fun locate(offset: Int): Located {
        var node = root
        var rel = offset
        while (!node.isNil) {
            if (rel < node.leftSubtreeLength) {
                node = node.left
            } else if (rel < node.leftSubtreeLength + node.piece.length) {
                return Located(node, rel - node.leftSubtreeLength)
            } else {
                rel -= node.leftSubtreeLength + node.piece.length
                if (node.right.isNil) return Located(node, node.piece.length)
                node = node.right
            }
        }
        return Located(NIL, 0)
    }

    private fun rightmostNonNil(start: PieceNode): PieceNode {
        var n = start
        while (!n.right.isNil) n = n.right
        return n
    }

    private fun leftmostNonNil(start: PieceNode): PieceNode {
        var n = start
        while (!n.left.isNil) n = n.left
        return n
    }

    private fun successor(node: PieceNode): PieceNode {
        if (!node.right.isNil) return leftmostNonNil(node.right)
        var x = node
        var p = node.parent
        while (!p.isNil && p.right === x) {
            x = p
            p = p.parent
        }
        return p
    }

    private fun predecessor(node: PieceNode): PieceNode {
        if (!node.left.isNil) return rightmostNonNil(node.left)
        var x = node
        var p = node.parent
        while (!p.isNil && p.left === x) {
            x = p
            p = p.parent
        }
        return p
    }

    private fun insertBefore(node: PieceNode, piece: Piece): PieceNode {
        val newNode = PieceNode(piece)
        if (node.left.isNil) {
            node.left = newNode
            newNode.parent = node
        } else {
            val pred = rightmostNonNil(node.left)
            pred.right = newNode
            newNode.parent = pred
        }
        recomputeMetadataPathToRoot(newNode)
        fixInsert(newNode)
        return newNode
    }

    private fun insertAfter(node: PieceNode, piece: Piece): PieceNode {
        val newNode = PieceNode(piece)
        if (node.right.isNil) {
            node.right = newNode
            newNode.parent = node
        } else {
            val succ = leftmostNonNil(node.right)
            succ.left = newNode
            newNode.parent = succ
        }
        recomputeMetadataPathToRoot(newNode)
        fixInsert(newNode)
        return newNode
    }

    private fun updateNodePiece(node: PieceNode, piece: Piece) {
        node.piece = piece
        recomputeMetadataPathToRoot(node)
    }

    private fun deleteNode(z: PieceNode) {
        var y = z
        var yOriginalColor = y.color
        val x: PieceNode
        when {
            z.left.isNil -> {
                x = z.right
                transplant(z, z.right)
            }
            z.right.isNil -> {
                x = z.left
                transplant(z, z.left)
            }
            else -> {
                y = leftmostNonNil(z.right)
                yOriginalColor = y.color
                x = y.right
                if (y.parent === z) {
                    x.parent = y
                } else {
                    transplant(y, y.right)
                    y.right = z.right
                    y.right.parent = y
                }
                transplant(z, y)
                y.left = z.left
                y.left.parent = y
                y.color = z.color
                recomputeMetadataPathToRoot(y)
            }
        }
        recomputeMetadataPathToRoot(x.parent)
        if (yOriginalColor == NodeColor.BLACK) fixDelete(x)
    }

    private fun transplant(u: PieceNode, v: PieceNode) {
        when {
            u.parent.isNil -> root = v
            u === u.parent.left -> u.parent.left = v
            else -> u.parent.right = v
        }
        v.parent = u.parent
    }

    private fun rotateLeft(x: PieceNode) {
        val y = x.right
        x.right = y.left
        if (!y.left.isNil) y.left.parent = x
        y.parent = x.parent
        when {
            x.parent.isNil -> root = y
            x === x.parent.left -> x.parent.left = y
            else -> x.parent.right = y
        }
        y.left = x
        x.parent = y
        recomputeMetadata(x)
        recomputeMetadata(y)
    }

    private fun rotateRight(y: PieceNode) {
        val x = y.left
        y.left = x.right
        if (!x.right.isNil) x.right.parent = y
        x.parent = y.parent
        when {
            y.parent.isNil -> root = x
            y === y.parent.right -> y.parent.right = x
            else -> y.parent.left = x
        }
        x.right = y
        y.parent = x
        recomputeMetadata(y)
        recomputeMetadata(x)
    }

    private fun fixInsert(z0: PieceNode) {
        var z = z0
        while (z.parent.color == NodeColor.RED) {
            if (z.parent === z.parent.parent.left) {
                val uncle = z.parent.parent.right
                if (uncle.color == NodeColor.RED) {
                    z.parent.color = NodeColor.BLACK
                    uncle.color = NodeColor.BLACK
                    z.parent.parent.color = NodeColor.RED
                    z = z.parent.parent
                } else {
                    if (z === z.parent.right) {
                        z = z.parent
                        rotateLeft(z)
                    }
                    z.parent.color = NodeColor.BLACK
                    z.parent.parent.color = NodeColor.RED
                    rotateRight(z.parent.parent)
                }
            } else {
                val uncle = z.parent.parent.left
                if (uncle.color == NodeColor.RED) {
                    z.parent.color = NodeColor.BLACK
                    uncle.color = NodeColor.BLACK
                    z.parent.parent.color = NodeColor.RED
                    z = z.parent.parent
                } else {
                    if (z === z.parent.left) {
                        z = z.parent
                        rotateRight(z)
                    }
                    z.parent.color = NodeColor.BLACK
                    z.parent.parent.color = NodeColor.RED
                    rotateLeft(z.parent.parent)
                }
            }
            if (z === root) break
        }
        root.color = NodeColor.BLACK
    }

    private fun fixDelete(x0: PieceNode) {
        var x = x0
        while (x !== root && x.color == NodeColor.BLACK) {
            if (x === x.parent.left) {
                var w = x.parent.right
                if (w.color == NodeColor.RED) {
                    w.color = NodeColor.BLACK
                    x.parent.color = NodeColor.RED
                    rotateLeft(x.parent)
                    w = x.parent.right
                }
                if (w.left.color == NodeColor.BLACK && w.right.color == NodeColor.BLACK) {
                    w.color = NodeColor.RED
                    x = x.parent
                } else {
                    if (w.right.color == NodeColor.BLACK) {
                        w.left.color = NodeColor.BLACK
                        w.color = NodeColor.RED
                        rotateRight(w)
                        w = x.parent.right
                    }
                    w.color = x.parent.color
                    x.parent.color = NodeColor.BLACK
                    w.right.color = NodeColor.BLACK
                    rotateLeft(x.parent)
                    x = root
                }
            } else {
                var w = x.parent.left
                if (w.color == NodeColor.RED) {
                    w.color = NodeColor.BLACK
                    x.parent.color = NodeColor.RED
                    rotateRight(x.parent)
                    w = x.parent.left
                }
                if (w.right.color == NodeColor.BLACK && w.left.color == NodeColor.BLACK) {
                    w.color = NodeColor.RED
                    x = x.parent
                } else {
                    if (w.left.color == NodeColor.BLACK) {
                        w.right.color = NodeColor.BLACK
                        w.color = NodeColor.RED
                        rotateLeft(w)
                        w = x.parent.left
                    }
                    w.color = x.parent.color
                    x.parent.color = NodeColor.BLACK
                    w.left.color = NodeColor.BLACK
                    rotateRight(x.parent)
                    x = root
                }
            }
        }
        x.color = NodeColor.BLACK
    }

    private fun recomputeMetadata(node: PieceNode) {
        if (node.isNil) return
        node.leftSubtreeLength = subtreeLength(node.left)
        node.leftSubtreeLineCount = subtreeLineCount(node.left)
    }

    private fun recomputeMetadataPathToRoot(start: PieceNode) {
        var n = start
        while (!n.isNil) {
            recomputeMetadata(n)
            n = n.parent
        }
    }

    private fun subtreeLength(node: PieceNode): Int {
        if (node.isNil) return 0
        return node.leftSubtreeLength + node.piece.length + subtreeLength(node.right)
    }

    private fun subtreeLineCount(node: PieceNode): Int {
        if (node.isNil) return 0
        return node.leftSubtreeLineCount + node.piece.lineFeedCount + subtreeLineCount(node.right)
    }

    private fun sliceFront(p: Piece, keepLen: Int): Piece {
        val lf = countLineFeedsInPieceSlice(p, 0, keepLen)
        return Piece(p.kind, p.start, keepLen, lf)
    }

    private fun sliceBack(p: Piece, keepLen: Int): Piece {
        val dropped = p.length - keepLen
        val lf = countLineFeedsInPieceSlice(p, dropped, p.length)
        return Piece(p.kind, p.start + dropped, keepLen, lf)
    }

    private fun countLineFeedsInPieceSlice(p: Piece, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) if (pieceCharAt(p, i) == '\n') c++
        return c
    }

    private fun countLineFeeds(s: String): Int {
        var c = 0
        for (i in s.indices) if (s[i] == '\n') c++
        return c
    }
}
