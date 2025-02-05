import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class PDFScraper {
    private static final int THREAD_COUNT = 50;
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 60000;
    private static final String CSV_FILE = "output.csv"; 

    public static void main(String[] args) {
        String baseUrl = "https://papers.nips.cc";
        String outputDir = "E:/Scraped_Data/";
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try {
            // Initialize CSV file for metadata
            BufferedWriter csvWriter = new BufferedWriter(new FileWriter(CSV_FILE));
            csvWriter.write("Year,Title,Authors,Paper Link\n");

            Document document = Jsoup.connect(baseUrl).timeout(TIMEOUT).get();
            Elements yearLinks = document.select("a[href^=/paper_files/paper/]");

            for (Element yearLink : yearLinks) {
                String yearUrl = baseUrl + yearLink.attr("href");
                String year = extractYear(yearUrl);
                String yearOutputDir = outputDir + year + "/";
                Files.createDirectories(Paths.get(yearOutputDir));

                try {
                    Document yearPage = Jsoup.connect(yearUrl).timeout(TIMEOUT).get();
                    Elements paperLinks = yearPage.select("ul.paper-list li a[href$=Abstract-Conference.html]");

                    for (Element paperLink : paperLinks) {
                        String paperUrl = baseUrl + paperLink.attr("href");
                        executor.submit(() -> processPaper(baseUrl, paperUrl, yearOutputDir, year, csvWriter));
                    }
                } catch (IOException e) {
                    System.err.println("Failed to process year: " + yearUrl);
                    e.printStackTrace();
                }
            }

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            csvWriter.close(); // Close CSV writer after scraping is complete
            System.out.println("Scraping completed! Metadata saved to " + CSV_FILE);
        } catch (IOException | InterruptedException e) {
            System.err.println("An error occurred during the scraping process.");
            e.printStackTrace();
        }
    }

    private static void processPaper(String baseUrl, String paperUrl, String outputDir, String year, BufferedWriter csvWriter) {
        int attempts = 0;
        boolean success = false;

        while (attempts < MAX_RETRIES && !success) {
            try {
                Document paperPage = Jsoup.connect(paperUrl).timeout(TIMEOUT).get();
                String paperTitle = sanitizeFilename(paperPage.select("title").text());

                // Extract authors
                Elements authorElements = paperPage.select(".author");
                String authors = authorElements.text().replaceAll("\\s+", ", ");

                // Extract PDF link
                Element pdfLink = paperPage.selectFirst("a[href$=Paper-Conference.pdf]");
                if (pdfLink != null) {
                    String pdfUrl = baseUrl + pdfLink.attr("href");
                    downloadPDF(pdfUrl, outputDir, paperTitle);
                }

                // Save paper metadata to CSV
                csvWriter.write(String.format("%s,%s,%s,%s\n", year, paperTitle, authors, paperUrl));

                success = true;
            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    System.err.println("Giving up on paper: " + paperUrl);
                }
            }
        }
    }

    private static void downloadPDF(String pdfUrl, String outputDir, String fileName) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(new HttpGet(pdfUrl));
             InputStream inputStream = response.getEntity().getContent();
             FileOutputStream outputStream = new FileOutputStream(outputDir + fileName + ".pdf")) {

            Files.createDirectories(Paths.get(outputDir));
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String extractYear(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1].replaceAll("\\D", "");
    }
}
