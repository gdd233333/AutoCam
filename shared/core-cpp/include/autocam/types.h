#pragma once

#include <cstdint>

namespace autocam {

struct Box {
    float x0, y0, x1, y1;
};

struct Guide {
    float target_nx, target_ny, suggested_zoom, confidence;
    const char* composition_class;
    Box subject_box;
};

struct FrameMeta {
    int64_t timestamp_ns;
    int width, height, rotation_deg;
    float zoom_ratio;
    const char* active_lens_id;
};

struct NetOut {
    Box box;
    float obj;
    float logits[8];
};

}  // namespace autocam
