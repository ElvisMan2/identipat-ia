package com.mnk.identipatia.analysis.worker;

class AnalysisLeaseLostException extends RuntimeException {
    AnalysisLeaseLostException() {
        super("Analysis lease is no longer active");
    }
}
