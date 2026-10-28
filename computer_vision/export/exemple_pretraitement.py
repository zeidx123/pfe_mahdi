"""Prétraitement de référence du modèle de propreté (à reproduire à l'identique dans le Dart de l'application).

1. Décoder l'image en RGB (8 bits par canal, ordre des canaux R, G, B).
2. Redimensionner PAR ÉTIREMENT en 224 x 224 (aucun recadrage, proportions non conservées), interpolation bilinéaire.
3. Convertir en float32 dans [0, 1] (valeur / 255).
4. Normaliser chaque canal : (x - moyenne) / écart-type,
   moyenne = [0.485, 0.456, 0.406], écart-type = [0.229, 0.224, 0.225] (R, G, B).
5. Disposition :
   - ONNX   : [1, 3, 224, 224] (NCHW)
   - TFLite : [1, 224, 224, 3] (NHWC)  -> voir model_card.json, champ "disposition_entree".
La sortie est un vecteur de 3 probabilités (softmax, température incluse) dans l'ordre :
   0 = CANNOT_DETERMINE, 1 = DEBORDEMENT, 2 = PROPRE.

Dépendances : numpy, pillow.  Usage : python exemple_pretraitement.py image.jpg [modele.onnx|modele.tflite]
"""
import sys

import numpy as np
from PIL import Image

TAILLE = 224
MOYENNE = np.array([0.485, 0.456, 0.406], dtype=np.float32)
ECART_TYPE = np.array([0.229, 0.224, 0.225], dtype=np.float32)
CLASSES = ["CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE"]


def pretraiter(chemin, disposition="NCHW"):
    with Image.open(chemin) as im:
        im = im.convert("RGB").resize((TAILLE, TAILLE), Image.BILINEAR)   # étirement, sans recadrage
    x = np.asarray(im, dtype=np.float32) / 255.0                           # [224, 224, 3] HWC, RGB
    x = (x - MOYENNE) / ECART_TYPE
    if disposition == "NCHW":
        x = x.transpose(2, 0, 1)                                           # [3, 224, 224]
    return x[None].astype(np.float32)                                      # ajoute la dimension du lot


if __name__ == "__main__":
    image = sys.argv[1]
    modele = sys.argv[2] if len(sys.argv) > 2 else None
    if modele is None:
        x = pretraiter(image)
        print(x.shape, x.dtype, float(x.mean()))
    elif modele.endswith(".onnx"):
        import onnxruntime as ort
        s = ort.InferenceSession(modele, providers=["CPUExecutionProvider"])
        p = s.run(None, {s.get_inputs()[0].name: pretraiter(image, "NCHW")})[0][0]
        print({c: round(float(v), 4) for c, v in zip(CLASSES, p)})
    else:
        try:
            from ai_edge_litert.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite import Interpreter
        it = Interpreter(model_path=modele)
        it.allocate_tensors()
        ent, sor = it.get_input_details()[0], it.get_output_details()[0]
        it.set_tensor(ent["index"], pretraiter(image, "NHWC"))
        it.invoke()
        p = it.get_tensor(sor["index"])[0]
        print({c: round(float(v), 4) for c, v in zip(CLASSES, p)})
