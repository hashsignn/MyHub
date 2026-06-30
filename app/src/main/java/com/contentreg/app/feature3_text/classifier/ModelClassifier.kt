package com.contentreg.app.feature3_text.classifier

import com.contentreg.app.feature3_text.ScreenSnapshot

/**
 * M3.1 — seam for a future on-device ML model (LiteRT / ONNX).
 *
 * Currently a no-op stub: [isAvailable] is always false so [ContextClassifier] never calls
 * [classify]. When a model is ready, load it from res/raw/ in the constructor and flip
 * [isAvailable]. The rest of the classification pipeline stays unchanged.
 *
 * Target spec when implemented:
 *  - Small quantized text classifier, ~10–30 MB (BERT-tiny or bag-of-words).
 *  - Runs entirely on-device; no network access.
 *  - Invoked only for mid-confidence [0.40, 0.80) snapshots where rules are uncertain.
 */
class ModelClassifier {

    /** False until a real model is loaded. Always false in this stub. */
    val isAvailable: Boolean = false

    fun classify(snapshot: ScreenSnapshot): TextClassification = TextClassification.CLEAN
}