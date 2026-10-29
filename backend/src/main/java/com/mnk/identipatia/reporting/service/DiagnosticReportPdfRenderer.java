package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DiagnosticReportPdfRenderer {
    private static final float MARGIN = 46;
    private static final float TOP_MARGIN = 42;
    private static final float BODY_SIZE = 9.7f;
    private static final float BODY_LEADING = 13;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (2 * MARGIN);
    private static final Color INK = new Color(37, 50, 63);
    private static final Color MUTED = new Color(83, 99, 114);
    private static final Color INSTITUTIONAL = new Color(39, 73, 103);
    private static final Color SOFT_BLUE = new Color(236, 243, 249);
    private static final Color SOFT_SCREENING = new Color(239, 240, 249);
    private static final Color DIVIDER = new Color(208, 218, 227);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("America/Lima"));

    public byte[] render(DiagnosticReport report) {
        try (PDDocument document = new PDDocument();
                InputStream textFontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
                InputStream mathFontStream = getClass().getResourceAsStream("/fonts/NotoSansMath-Regular.ttf")) {
            if (textFontStream == null || mathFontStream == null) {
                throw new IllegalStateException("A bundled PDF font is missing");
            }
            PDType0Font textFont = PDType0Font.load(document, textFontStream);
            PDType0Font mathFont = PDType0Font.load(document, mathFontStream);
            Cursor cursor = new Cursor(document, textFont, mathFont);
            cursor.brand("IDENTIPAT-IA");
            cursor.heading(report.title());
            cursor.highlight(report.disclaimer().title(), report.disclaimer().content()
                    + " (Versión " + report.disclaimer().version() + ")");

            cursor.section("Resumen");
            cursor.paragraph(report.summary());
            cursor.note(report.applicabilityNote());

            cursor.section("Modalidades de protección");
            if (report.protectionOptions().isEmpty()) {
                cursor.paragraph("No se identificaron modalidades en el resultado canónico.");
            }
            for (DiagnosticReport.ProtectionPresentation option : report.protectionOptions()) {
                cursor.keepTogether(cursor.optionHeaderHeight()
                        + cursor.labelBadgeHeight(option.relationshipQuestion()));
                cursor.optionHeader(option.name());
                cursor.labelBadge(option.relationshipQuestion(), option.applicabilityLabel(),
                        BadgeTone.forApplicability(option.applicability()));
                if (option.protectedSubjectMatter() != null) {
                    cursor.labelValue("¿Qué parte de tu creación podría proteger?", option.protectedSubjectMatter());
                }
                cursor.labelValue("¿Por qué se identificó esta modalidad?", option.rationale());
                if (!option.legalBasis().isEmpty()) {
                    String firstBasis = legalBasisText(option.legalBasis().getFirst());
                    cursor.keepLabelAndFirstLine("Base jurídica", "• " + firstBasis);
                    cursor.label("Base jurídica");
                    option.legalBasis().forEach(basis -> cursor.bullet(legalBasisText(basis)));
                }
                cursor.labelValueTogether("Orientación", option.orientation());
                if (option.patentScreening() != null) {
                    writeScreening(cursor, option.patentScreening());
                }
                if (option.geneticResourceAccess() != null) {
                    writeGeneticResources(cursor, option.geneticResourceAccess());
                }
                cursor.endOption();
            }
            if (report.patentScreening() != null) {
                writeScreening(cursor, report.patentScreening());
            }

            if (report.historicalPatentability() != null) {
                cursor.section("Evaluación patentaria histórica");
                cursor.labelValue("Resultado", report.historicalPatentability().outcome());
                cursor.paragraph(report.historicalPatentability().rationale());
            }

            if (report.geneticResourceAccess() != null) {
                writeGeneticResources(cursor, report.geneticResourceAccess());
            }

            writeList(cursor, "Aspectos a considerar", report.observations());
            writeList(cursor, "Advertencias del caso", report.warnings());
            cursor.metadata(report.analysisId().toString(), DATE_FORMAT.format(report.analysisCompletedAt()),
                    report.reportVersion());
            cursor.finish();

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render diagnostic report PDF", exception);
        }
    }

    private static String legalBasisText(DiagnosticReport.LegalBasisPresentation basis) {
        return basis.instrument() + " — artículo(s) " + String.join(", ", basis.articles());
    }

    private static void writeGeneticResources(Cursor cursor,
            DiagnosticReport.GeneticResourcePresentation genetic) {
        cursor.keepGeneticResources(genetic);
        cursor.section("Recursos genéticos");
        cursor.labelBadge("Evaluación", genetic.assessmentLabel(),
                BadgeTone.forAssessment(genetic.assessment()));
        cursor.paragraph(genetic.rationale());
        if (!genetic.missingInformation().isEmpty()) {
            cursor.keepLabelAndFirstLine("Información faltante",
                    "• " + genetic.missingInformation().getFirst());
            cursor.label("Información faltante");
            genetic.missingInformation().forEach(cursor::bullet);
        }
    }

    private static void writeScreening(Cursor cursor, DiagnosticReport.PatentScreeningPresentation screening) {
        cursor.screeningHeader(screening.title());
        cursor.note(screening.context());
        writeScreeningArticle(cursor, screening.article15(), false, screening.scopeNote());
        writeScreeningArticle(cursor, screening.article20(), true, screening.scopeNote());
        cursor.screeningScope(screening.scopeNote());
    }

    private static void writeScreeningArticle(Cursor cursor,
            DiagnosticReport.ScreeningArticlePresentation article, boolean last, String scopeNote) {
        if (last) {
            cursor.keepScreeningTail(article, scopeNote);
        } else {
            cursor.keepScreeningArticle(article);
        }
        cursor.screeningArticleHeader(article.article(), article.assessmentLabel(),
                BadgeTone.forAssessment(article.assessment()));
        cursor.paragraph(article.rationale());
        article.matches().forEach(match -> cursor.bullet(match.literal() + ": " + match.rationale()));
    }

    private static void writeList(Cursor cursor, String title, List<String> values) {
        cursor.section(title);
        if (values.isEmpty()) {
            cursor.paragraph("Sin elementos específicos para este caso.");
        } else {
            values.forEach(cursor::bullet);
        }
    }

    private record BadgeTone(Color background, Color foreground) {
        private static final BadgeTone POSITIVE = new BadgeTone(
                new Color(226, 241, 233), new Color(40, 91, 66));
        private static final BadgeTone ATTENTION = new BadgeTone(
                new Color(247, 239, 216), new Color(111, 84, 28));
        private static final BadgeTone NEUTRAL = new BadgeTone(
                new Color(232, 237, 242), new Color(70, 84, 97));

        private static BadgeTone forApplicability(String value) {
            return switch (value) {
                case "LIKELY" -> POSITIVE;
                case "POSSIBLE" -> ATTENTION;
                default -> NEUTRAL;
            };
        }

        private static BadgeTone forAssessment(String value) {
            return switch (value) {
                case "NO_POTENTIAL_MATCH", "NOT_INDICATED" -> POSITIVE;
                case "POTENTIAL_MATCH", "POTENTIALLY_REQUIRED" -> ATTENTION;
                default -> NEUTRAL;
            };
        }
    }

    private static final class Cursor {
        private static final float PAGE_BOTTOM = 55;
        private final PDDocument document;
        private final PDType0Font textFont;
        private final PDType0Font mathFont;
        private PDPage page;
        private float y;

        private Cursor(PDDocument document, PDType0Font textFont, PDType0Font mathFont) throws IOException {
            this.document = document;
            this.textFont = textFont;
            this.mathFont = mathFont;
            newPage();
        }

        private void newPage() throws IOException {
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            y = page.getMediaBox().getHeight() - TOP_MARGIN;
        }

        private void brand(String text) {
            drawLines(List.of(text), 10.5f, 14, MARGIN, INSTITUTIONAL);
            rule(INSTITUTIONAL, 1.2f);
            gap(5);
        }

        private void heading(String text) {
            keepTogether(height(text, 15, CONTENT_WIDTH) + 8);
            drawLines(wrap(text, 15, CONTENT_WIDTH), 15, 19, MARGIN, INK);
            gap(5);
        }

        private void section(String text) {
            float titleHeight = height(text, 12.5f, CONTENT_WIDTH - 10);
            keepTogether(8 + titleHeight + BODY_LEADING);
            gap(7);
            fill(MARGIN, y - titleHeight, 3, titleHeight, INSTITUTIONAL);
            drawLines(wrap(text, 12.5f, CONTENT_WIDTH - 10), 12.5f, 16,
                    MARGIN + 9, INSTITUTIONAL);
            gap(2);
        }

        private float optionHeaderHeight() {
            return 24;
        }

        private void optionHeader(String text) {
            ensure(24);
            fill(MARGIN, y - 20, CONTENT_WIDTH, 20, SOFT_BLUE);
            fill(MARGIN, y - 20, 3, 20, INSTITUTIONAL);
            drawLines(wrap(text, 11.2f, CONTENT_WIDTH - 20), 11.2f, 15,
                    MARGIN + 10, INSTITUTIONAL);
            y -= 5;
        }

        private void screeningHeader(String text) {
            float titleHeight = height(text, 11.2f, CONTENT_WIDTH - 20);
            keepTogether(8 + titleHeight + BODY_LEADING);
            gap(5);
            fill(MARGIN, y - titleHeight - 4, CONTENT_WIDTH, titleHeight + 4, SOFT_SCREENING);
            fill(MARGIN, y - titleHeight - 4, 3, titleHeight + 4, new Color(91, 83, 139));
            drawLines(wrap(text, 11.2f, CONTENT_WIDTH - 20), 11.2f, 15,
                    MARGIN + 10, new Color(75, 69, 117));
            gap(3);
        }

        private void endOption() {
            gap(3);
            rule(DIVIDER, 0.55f);
            gap(1);
        }

        private void paragraph(String text) {
            drawLines(wrap(text, BODY_SIZE, CONTENT_WIDTH), BODY_SIZE, BODY_LEADING, MARGIN, INK);
            gap(2);
        }

        private void note(String text) {
            drawLines(wrap(text, 9.2f, CONTENT_WIDTH - 14), 9.2f, 12.5f,
                    MARGIN + 7, MUTED);
            gap(3);
        }

        private void label(String text) {
            drawLines(wrap(text, 10.2f, CONTENT_WIDTH), 10.2f, 13.5f, MARGIN, INSTITUTIONAL);
        }

        private void labelValue(String label, String value) {
            keepLabelAndFirstLine(label, value);
            label(label);
            drawLines(wrap(value, BODY_SIZE, CONTENT_WIDTH - 12), BODY_SIZE, BODY_LEADING,
                    MARGIN + 12, INK);
            gap(1.5f);
        }

        private void labelValueTogether(String label, String value) {
            float required = height(label, 10.2f, CONTENT_WIDTH)
                    + height(value, BODY_SIZE, CONTENT_WIDTH - 12) + 2;
            keepReasonable(required);
            label(label);
            drawLines(wrap(value, BODY_SIZE, CONTENT_WIDTH - 12), BODY_SIZE, BODY_LEADING,
                    MARGIN + 12, INK);
            gap(1.5f);
        }

        private float labelBadgeHeight(String label) {
            return height(label, 10.2f, CONTENT_WIDTH) + 21;
        }

        private void labelBadge(String label, String value, BadgeTone tone) {
            keepTogether(labelBadgeHeight(label));
            label(label);
            badge(value, tone);
            gap(2);
        }

        private void screeningArticleHeader(String article, String assessment, BadgeTone tone) {
            label(article);
            badge(assessment, tone);
            gap(1);
        }

        private void badge(String value, BadgeTone tone) {
            float size = 9.2f;
            float width = Math.min(CONTENT_WIDTH - 12, textWidth(value, size) + 18);
            ensure(18);
            fill(MARGIN + 12, y - 15, width, 15, tone.background());
            drawText(value, size, MARGIN + 20, y - 11, tone.foreground());
            y -= 18;
        }

        private void bullet(String text) {
            drawLines(wrap("• " + text, BODY_SIZE, CONTENT_WIDTH - 12), BODY_SIZE,
                    BODY_LEADING, MARGIN + 12, INK);
            gap(0.5f);
        }

        private void highlight(String title, String text) {
            List<String> titleLines = wrap(title, 10.8f, CONTENT_WIDTH - 22);
            List<String> bodyLines = wrap(text, 9.5f, CONTENT_WIDTH - 22);
            keepTogether(Math.min(46, titleLines.size() * 14.5f + BODY_LEADING));
            drawHighlighted(titleLines, 10.8f, 14.5f, INSTITUTIONAL);
            drawHighlighted(bodyLines, 9.5f, BODY_LEADING, INK);
            gap(3);
        }

        private void drawHighlighted(List<String> wrapped, float size, float leading, Color color) {
            for (String line : wrapped) {
                ensure(leading);
                fill(MARGIN, y - leading, CONTENT_WIDTH, leading, SOFT_BLUE);
                fill(MARGIN, y - leading, 3, leading, INSTITUTIONAL);
                drawText(line, size, MARGIN + 11, y - size, color);
                y -= leading;
            }
        }

        private void keepLabelAndFirstLine(String label, String value) {
            float firstLine = wrap(value, BODY_SIZE, CONTENT_WIDTH - 12).isEmpty() ? 0 : BODY_LEADING;
            keepTogether(height(label, 10.2f, CONTENT_WIDTH) + firstLine);
        }

        private void keepScreeningArticle(DiagnosticReport.ScreeningArticlePresentation article) {
            keepTogether(labelBadgeHeight(article.article()) + BODY_LEADING);
        }

        private void keepGeneticResources(DiagnosticReport.GeneticResourcePresentation genetic) {
            float required = 7 + height("Recursos genéticos", 12.5f, CONTENT_WIDTH - 10) + 2
                    + labelBadgeHeight("Evaluación")
                    + height(genetic.rationale(), BODY_SIZE, CONTENT_WIDTH) + 2;
            if (!genetic.missingInformation().isEmpty()) {
                required += height("Información faltante", 10.2f, CONTENT_WIDTH);
                required += (float) genetic.missingInformation().stream()
                        .mapToDouble(value -> height("• " + value, BODY_SIZE, CONTENT_WIDTH - 12) + 0.5f)
                        .sum();
            }
            float usablePageHeight = PDRectangle.A4.getHeight() - TOP_MARGIN - PAGE_BOTTOM;
            if (required <= usablePageHeight) {
                keepTogether(required);
            }
        }

        private void keepScreeningTail(DiagnosticReport.ScreeningArticlePresentation article, String scopeNote) {
            float completeArticle = (float) (labelBadgeHeight(article.article())
                    + height(article.rationale(), BODY_SIZE, CONTENT_WIDTH)
                    + article.matches().stream()
                            .mapToDouble(match -> height("• " + match.literal() + ": " + match.rationale(),
                                    BODY_SIZE, CONTENT_WIDTH - 12) + 0.5f)
                            .sum());
            float completeScope = height(scopeNote, 9.2f, CONTENT_WIDTH - 14) + 3;
            float completeTail = completeArticle + completeScope;
            if (completeTail <= 170) {
                keepTogether(completeTail);
            } else {
                keepScreeningArticle(article);
            }
        }

        private void screeningScope(String text) {
            keepReasonable(height(text, 9.2f, CONTENT_WIDTH - 14) + 3);
            note(text);
        }

        private void metadata(String analysisId, String completedAt, String reportVersion) {
            keepTogether(61);
            gap(5);
            float boxHeight = 53;
            float top = y;
            fill(MARGIN, top - boxHeight, CONTENT_WIDTH, boxHeight, new Color(246, 248, 250));
            stroke(MARGIN, top - boxHeight, CONTENT_WIDTH, boxHeight, DIVIDER, 0.6f);
            metadataCell("Identificador del análisis", analysisId, MARGIN + 10, top - 8);
            float columnY = top - 31;
            float secondColumn = MARGIN + (CONTENT_WIDTH * 0.59f);
            metadataCell("Fecha de finalización", completedAt, MARGIN + 10, columnY);
            metadataCell("Versión del reporte", reportVersion, secondColumn, columnY);
            y -= boxHeight + 2;
        }

        private void metadataCell(String label, String value, float x, float top) {
            drawText(label, 7.8f, x, top, MUTED);
            drawText(value, 9.1f, x, top - 12, INK);
        }

        private float height(String text, float size, float width) {
            float leading = size == BODY_SIZE ? BODY_LEADING : size + 3.5f;
            return wrap(text, size, width).size() * leading;
        }

        private void keepReasonable(float required) {
            if (required <= 90) {
                keepTogether(required);
            } else {
                keepTogether(BODY_LEADING * 2);
            }
        }

        private void keepTogether(float required) {
            ensure(required);
        }

        private void drawLines(List<String> wrapped, float size, float leading, float x, Color color) {
            for (String line : wrapped) {
                ensure(leading);
                drawText(line, size, x, y - size, color);
                y -= leading;
            }
        }

        private List<String> wrap(String text, float size, float width) {
            if (text == null || text.isBlank()) {
                return List.of("");
            }
            List<String> result = new ArrayList<>();
            for (String sourceLine : text.replace("\r", "").split("\n", -1)) {
                String current = "";
                for (String word : sourceLine.trim().split("\\s+")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (!current.isEmpty() && textWidth(candidate, size) > width) {
                        result.add(current);
                        current = word;
                    } else {
                        current = candidate;
                    }
                }
                result.add(current);
            }
            return result;
        }

        private float textWidth(String text, float size) {
            try {
                float width = 0;
                for (int offset = 0; offset < text.length();) {
                    int codePoint = text.codePointAt(offset);
                    PDType0Font font = fontFor(codePoint);
                    width += font.getStringWidth(new String(Character.toChars(codePoint))) / 1000 * size;
                    offset += Character.charCount(codePoint);
                }
                return width;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void drawText(String text, float size, float x, float baseline, Color color) {
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(color);
                stream.beginText();
                stream.newLineAtOffset(x, baseline);
                showText(stream, text, size);
                stream.endText();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void fill(float x, float bottom, float width, float height, Color color) {
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(color);
                stream.addRect(x, bottom, width, height);
                stream.fill();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void stroke(float x, float bottom, float width, float height, Color color, float lineWidth) {
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setStrokingColor(color);
                stream.setLineWidth(lineWidth);
                stream.addRect(x, bottom, width, height);
                stream.stroke();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void rule(Color color, float lineWidth) {
            ensure(2);
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setStrokingColor(color);
                stream.setLineWidth(lineWidth);
                stream.moveTo(MARGIN, y);
                stream.lineTo(MARGIN + CONTENT_WIDTH, y);
                stream.stroke();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            y -= 2;
        }

        private void showText(PDPageContentStream stream, String text, float size) throws IOException {
            PDType0Font currentFont = null;
            StringBuilder run = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                PDType0Font nextFont = fontFor(codePoint);
                if (currentFont != null && currentFont != nextFont) {
                    stream.setFont(currentFont, size);
                    stream.showText(run.toString());
                    run.setLength(0);
                }
                currentFont = nextFont;
                run.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
            }
            if (currentFont != null) {
                stream.setFont(currentFont, size);
                stream.showText(run.toString());
            }
        }

        private PDType0Font fontFor(int codePoint) throws IOException {
            if (supports(textFont, codePoint)) {
                return textFont;
            }
            if (supports(mathFont, codePoint)) {
                return mathFont;
            }
            throw new IllegalArgumentException("No bundled PDF font supports U+"
                    + String.format("%04X", codePoint));
        }

        private static boolean supports(PDType0Font font, int codePoint) throws IOException {
            try {
                font.getStringWidth(new String(Character.toChars(codePoint)));
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private void ensure(float required) {
            if (y - required < PAGE_BOTTOM) {
                try {
                    newPage();
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        private void gap(float amount) {
            ensure(amount);
            y -= amount;
        }

        private void finish() throws IOException {
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                page = document.getPage(index);
                try (PDPageContentStream stream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.setStrokingColor(DIVIDER);
                    stream.setLineWidth(0.5f);
                    stream.moveTo(MARGIN, 39);
                    stream.lineTo(MARGIN + CONTENT_WIDTH, 39);
                    stream.stroke();
                }
                drawText("IDENTIPAT-IA", 8, MARGIN, 25, MUTED);
                String pagination = "Página " + (index + 1) + " de " + total;
                drawText(pagination, 8, MARGIN + CONTENT_WIDTH - textWidth(pagination, 8) - 8, 25, MUTED);
            }
        }
    }
}
