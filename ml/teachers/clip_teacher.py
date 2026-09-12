"""CLIP-ViT-B/32 zero-shot 8-class. Falls back to geometry heuristic if CLIP is missing."""

from __future__ import annotations

from ml.students.spec import CLASS_NAMES

PROMPTS = {
    "thirds": "a photograph composed with the rule of thirds",
    "center": "a photograph with the subject centered in the frame",
    "diagonal": "a photograph with a strong diagonal composition",
    "triangle": "a photograph with a triangular composition",
    "leading_line": "a photograph with leading lines or vanishing point",
    "symmetric": "a symmetrically composed photograph",
    "fill_frame": "a photograph that fills the frame with the subject",
    "none": "a snapshot with no clear photographic composition",
}


class ClipTeacher:
    def __init__(self, device: str = "cpu") -> None:
        self.device = device
        self.kind = "heuristic"
        self.model = None
        self.preprocess = None
        self.text = None
        self._try_open_clip()
        if self.model is None:
            self._try_transformers()

    def _try_open_clip(self) -> None:
        try:
            import open_clip
            import torch

            model, _, preprocess = open_clip.create_model_and_transforms(
                "ViT-B-32", pretrained="openai"
            )
            tokenizer = open_clip.get_tokenizer("ViT-B-32")
            model = model.to(self.device).eval()
            text = tokenizer([PROMPTS[n] for n in CLASS_NAMES]).to(self.device)
            with torch.no_grad():
                text_features = model.encode_text(text)
                text_features = text_features / text_features.norm(dim=-1, keepdim=True)
            self.model = model
            self.preprocess = preprocess
            self.text = text_features
            self.kind = "open_clip"
        except Exception:
            return

    def _try_transformers(self) -> None:
        try:
            import torch
            from transformers import CLIPModel, CLIPProcessor

            self.processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")
            self.model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32").to(self.device).eval()
            self.kind = "transformers"
        except Exception:
            self.model = None

    def logits_from_center(self, cx: float, cy: float) -> tuple[int, list[float]]:
        from ml.datasets.synthetic import class_from_center
        import numpy as np

        label = class_from_center(cx, cy)
        logits = np.full((8,), -4.0, dtype=np.float32)
        logits[label] = 4.0
        return label, logits.tolist()

    def infer_pil(self, image) -> tuple[int, list[float]]:
        import numpy as np
        import torch

        if self.kind == "open_clip":
            import torch.nn.functional as F

            x = self.preprocess(image).unsqueeze(0).to(self.device)
            with torch.no_grad():
                feat = self.model.encode_image(x)
                feat = feat / feat.norm(dim=-1, keepdim=True)
                logits = (100.0 * feat @ self.text.T).softmax(dim=-1)[0]
            arr = logits.detach().cpu().numpy().astype(np.float32)
            return int(arr.argmax()), arr.tolist()
        if self.kind == "transformers":
            inputs = self.processor(
                text=[PROMPTS[n] for n in CLASS_NAMES],
                images=image,
                return_tensors="pt",
                padding=True,
            )
            inputs = {k: v.to(self.device) for k, v in inputs.items()}
            with torch.no_grad():
                out = self.model(**inputs)
                probs = out.logits_per_image.softmax(dim=-1)[0]
            arr = probs.detach().cpu().numpy().astype(np.float32)
            return int(arr.argmax()), arr.tolist()
        # heuristic needs box; caller should pass center
        return 7, [0.0] * 7 + [1.0]
