import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class TestParsing {
    public static void main(String[] args) throws Exception {
        System.out.println("--- EXCEL HEADERS ---");
        try (Workbook wb = WorkbookFactory.create(new File("data/scenario_01_retail_ecommerce/company_ledger_brightline.xlsx"))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            for (Cell c : header) {
                System.out.print(c.toString() + " | ");
            }
            System.out.println("\nRow 1:");
            Row r1 = sheet.getRow(1);
            for (Cell c : r1) {
                System.out.print(c.toString() + " | ");
            }
        }
        System.out.println("\n\n--- PDF TEXT ---");
        try (PDDocument doc = PDDocument.load(new File("data/scenario_01_retail_ecommerce/bank_statement_pacific_trust.pdf"))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(1);
            String text = stripper.getText(doc);
            String[] lines = text.split("\n");
            for (int i = 0; i < Math.min(20, lines.length); i++) {
                System.out.println(lines[i].trim());
            }
        }
    }
}
