#pragma once

namespace autocam {

struct NetOut {
    float box[4];
    float obj;
    float logits[8];
};

// Ship 1 interpreter is Kotlin TFLite. Native LiteRT is not linked yet.
bool inference_available();

}  // namespace autocam

