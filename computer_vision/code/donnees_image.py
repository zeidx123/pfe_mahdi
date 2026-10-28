"""Prétraitement et jeux de données image (partagés par les étapes 5, 6, 8, 9).

Prétraitement de référence (identique à l'inférence dans l'application) :
  image RGB -> redimensionnement PAR ÉTIREMENT en 224x224 (PIL, bilinéaire), sans recadrage
  -> float32 [0,1] -> normalisation ImageNet -> tenseur [3, 224, 224] (NCHW une fois en lot).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import torch
from PIL import Image
from torch.utils.data import Dataset
from torchvision import transforms as T

import config as C

NORMALISER = T.Normalize(C.MOYENNE, C.ECART_TYPE)
INTERP = T.InterpolationMode.BILINEAR

# Évaluation : étirement direct 640x640 -> 224x224.
TF_EVAL = T.Compose([T.Resize((C.TAILLE, C.TAILLE), interpolation=INTERP), T.ToTensor(), NORMALISER])

# Entraînement, images réelles : recadrage-agrandissement léger (échelle >= 0,8, rapport d'aspect conservé :
# l'image est carrée, ratio fixé à 1), retournement horizontal, couleur douce (±0,15).
TF_TRAIN_REEL = T.Compose([
    T.RandomResizedCrop(C.TAILLE, scale=(0.8, 1.0), ratio=(1.0, 1.0), interpolation=INTERP),
    T.RandomHorizontalFlip(),
    T.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
    T.ToTensor(), NORMALISER])

# Entraînement, synthétiques CANNOT_DETERMINE : retournement horizontal seulement.
TF_TRAIN_SYNTH = T.Compose([T.Resize((C.TAILLE, C.TAILLE), interpolation=INTERP), T.RandomHorizontalFlip(),
                            T.ToTensor(), NORMALISER])


def charger_rgb(chemin):
    with Image.open(chemin) as im:
        return im.convert("RGB")


def pretraiter_numpy(chemin):
    """Référence NumPy du prétraitement (sans torchvision) : [1, 3, 224, 224] float32."""
    im = charger_rgb(chemin).resize((C.TAILLE, C.TAILLE), Image.BILINEAR)
    x = np.asarray(im, dtype=np.float32) / 255.0
    x = (x - np.array(C.MOYENNE, np.float32)) / np.array(C.ECART_TYPE, np.float32)
    return x.transpose(2, 0, 1)[None].astype(np.float32)


class JeuImages(Dataset):
    def __init__(self, df, entrainement=False):
        self.chemins = [str(C.DATASET / p) for p in df["chemin_image"]]
        self.y = df["y"].to_numpy().astype(np.int64)
        self.synth = (df["origine"] == "synthetique").to_numpy()
        self.entrainement = entrainement

    def __len__(self):
        return len(self.chemins)

    def __getitem__(self, k):
        im = charger_rgb(self.chemins[k])
        if not self.entrainement:
            tf = TF_EVAL
        else:
            tf = TF_TRAIN_SYNTH if self.synth[k] else TF_TRAIN_REEL
        return tf(im), self.y[k]


def init_worker(wid):
    s = torch.initial_seed() % 2**32
    np.random.seed(s)
    import random
    random.seed(s)
