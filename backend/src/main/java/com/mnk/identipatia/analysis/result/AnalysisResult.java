package com.mnk.identipatia.analysis.result;

import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import io.swagger.v3.oas.annotations.media.Schema;

/** Marker contract for explicitly versioned canonical analysis results. */
@Schema(oneOf = {AnalysisResultV1.class, AnalysisResultV2.class}, discriminatorProperty = "schemaVersion")
public interface AnalysisResult {
    String schemaVersion();
}
