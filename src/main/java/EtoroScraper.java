import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.max;


public class EtoroScraper
{
    private final String fullUrl = "https://www.etoro.com";
    private final String url = "https://www.etoro.com/investing/dividend-calendar/";
    private Map<String,Company> companies = new LinkedHashMap<>();
    private final int[] columnWidths = {50, 30, 20, 20, 20, 35};
    private ProgressTracker progressTracker;


    private void fetchCompanyPrice(String href, String companyName)
    {
        String link = fullUrl + href;
        Document marketPage;
        try {
            marketPage = Jsoup.connect(link).timeout(10000).get();
            String rawPrice = marketPage.select("span[data-automation-id=AssetShortInfoPrice]").text().replace(",", "");
            updateCompanyPrice(companyName,Float.parseFloat(rawPrice));
        }catch (IOException e)
        {
            System.out.println("Couldn't connect to " + link);
            updateCompanyPrice(companyName,0);
        }
        catch (Exception e)
        {
            System.out.println("Couldn't fetch "+ companyName +" price!");
            updateCompanyPrice(companyName,0);
            System.out.println(e.getMessage());
        }
    }

    private synchronized void updateCompanyPrice(String companyName, float price)
    {
        companies.get(companyName).price = price;
        progressTracker.incrementCompaniesProcessed();
    }

    private List<Element> getDividendCalendar()
    {
        Document doc;
        try {
            doc = Jsoup.connect(url).maxBodySize(0).get();
        }catch (IOException e)
        {
            System.out.println("Couldn't connect to " + url);
            return new ArrayList<>();
        }

        return doc.select("tbody.ec-reports-container tr");
    }

    private Company extractCompany(Element tableRow)
    {
        Elements tds = tableRow.select("td");
        try {
            String name = tds.get(0).select("span.ec-company__name").text();
            String fullName = tds.get(0).attr("data-company-name");
            String sector = tds.get(1).attr("data-sector-name");
            Date exDividend = Date.from(LocalDate.parse(tds.get(2).attr("data-exdividend-date")).atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dividend = Date.from(LocalDate.parse(tds.get(3).attr("data-payment-date")).atStartOfDay(ZoneId.systemDefault()).toInstant());
            String marketHref = tds.get(0).select("a").attr("href");
            float dividendPerShare = Float.parseFloat(tds.get(5).attr("data-net-dividend"));

            return new Company.Builder(name, fullName)
                    .sector(sector)
                    .exDividendDate(exDividend)
                    .dividendDate(dividend)
                    .marketHref(marketHref)
                    .dividendPerShare(dividendPerShare)
                    .addTag("NEW")
                    .build();
        }
        catch (Exception e)
        {
            System.out.println("Couldn't extract company!");
            return null;
        }
    }



    private void extractCompanies()
    {
        if (!companies.isEmpty())
            return;

        List<Element> tableRows = getDividendCalendar();

        int companiesCount = tableRows.size();
        progressTracker = new ProgressTracker(companiesCount);

        System.out.println("Extracting companies...");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int currentCompany=0; currentCompany<companiesCount; currentCompany++)
        {
            Element row = tableRows.get(currentCompany);
            try {
                Company company = extractCompany(row);
                if (company != null) {
                    companies.put(company.fullName, company);
                    executor.submit(() -> fetchCompanyPrice(company.marketHref, company.fullName));
                }
            }
            catch (Exception ignored)
            {}
        }
        executor.shutdown();

        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            System.out.println("Could not fetch all prices in time!");
        }

        System.out.println("Extraction completed.");
        saveCompaniesToFile();
    }

    private void removeOutdatedCompanies()
    {
        System.out.println("Removing outdated companies...");
        Date currentDate = new Date(System.currentTimeMillis());
        for (Company company : companies.values())
        {
            if(company.dividendDate.before(currentDate))
            {
                companies.remove(company.fullName);
            }
        }
        System.out.println("Removed outdated companies.");
    }

    private void updateCompanies()
    {
        System.out.println("Updating companies...");
        List<Element> tableRows = getDividendCalendar();
        int companiesCount = tableRows.size();
        removeOutdatedCompanies();

        progressTracker = new ProgressTracker(companiesCount);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int currentCompany=0; currentCompany<companiesCount; currentCompany++) {
            Element row = tableRows.get(currentCompany);
            String companyName = row.select("td[data-company-name]").attr("data-company-name");

            if (companies.containsKey(companyName) && companies.get(companyName).price != 0f) {
                companies.get(companyName).tags.remove("NEW");
                progressTracker.incrementCompaniesProcessed();
                continue;
            }

            System.out.println("Adding " + companyName + " to companies...");
            Company extractedCompany = extractCompany(row);
            if (extractedCompany != null) {
                companies.put(extractedCompany.fullName, extractedCompany);
                executor.submit(() -> fetchCompanyPrice(extractedCompany.marketHref, extractedCompany.fullName));
            }
        }

        executor.shutdown();

        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            System.out.println("Could not fetch all prices in time!");
        }

        System.out.println("Update completed.");
        saveCompaniesToFile();
    }


    private void saveCompaniesToFile()
    {
        System.out.println("Saving companies to file...");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new File("companies.json"), companies);
            System.out.println("Companies successfully saved.");
        }
        catch(Exception e)
        {
            System.out.println("Failed to save companies to file!");
        }
    }

    private void printTableHeader() {
        String[] rowTitles = {"Company", "Dividend Yield (after tax)", "Price", "ExDividend date", "Dividend date", "Dividend per share (after tax)" };

        StringBuilder header = new StringBuilder();
        StringBuilder separator = new StringBuilder();

        for (int i = 0; i < rowTitles.length; i++) {
            int width = columnWidths[i];
            String title = rowTitles[i];
            int padding = (width - title.length()) / 2;
            String formatted = String.format("%" + padding + "s%s%" + (width - padding - title.length()) + "s", "", title, "");
            header.append(formatted);
            if (i != rowTitles.length - 1)
                header.append(" | ");

            separator.append("-".repeat(width));
            if (i != rowTitles.length - 1)
                separator.append("-+-");
        }

        System.out.println("\n\n" + header);
        System.out.println(separator);

    }

    private void printRow(Company company)
    {
        final String RESET = "\u001B[0m";
        final String RED = "\u001B[31m";
        final String GREEN = "\u001B[32m";
        final String YELLOW = "\u001B[33m";
        final String BLUE = "\u001B[34m";
        String dividendReturn;
        float dividendTax = 0.1f;

        if(company.price > 0)
        {
            float returnValue = company.dividendPerShare / company.price * 100;
            dividendReturn = String.format("%.2f%% (%.2f%%)", returnValue, returnValue-returnValue* dividendTax);
        }
        else {
            dividendReturn = "-";
        }

        String[] values = {
                company.fullName,
                dividendReturn,
                String.format("%.2f", company.price),
                company.getExDividendDate(),
                company.getDividendDate(),
                String.format("%.2f (%.2f)", company.dividendPerShare, company.dividendPerShare - company.dividendPerShare* dividendTax),
        };

        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            String value =values[i];
            int padding = max(0, columnWidths[i] - value.length());
            if (padding %2 == 0)
                row.append(" ".repeat(padding/2));
            else
                row.append(" ".repeat(padding/2 + 1));
            row.append(value);
            row.append(" ".repeat(padding/2));

            if (i != values.length - 1)
                row.append(" | ");
        }

        if(company.tags.contains("NEW"))
            System.out.println(GREEN + row + RESET);
        else
            System.out.println(row);
    }

    private void sortCompaniesByReturn()
    {
        companies = companies.values().stream()
                .sorted((o1, o2) -> {
                    float ratio1 = o1.price == 0f ? 0f : o1.dividendPerShare/ o1.price;
                    float ratio2 = o2.price == 0f ? 0f : o2.dividendPerShare / o2.price;
                    int result = Float.compare(ratio2, ratio1);
                    if (result == 0)
                        return Float.compare(o2.price, o1.price);
                    return result;
                })
                .collect(Collectors.toMap(
                        Company::getFullName,
                        Function.identity(),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public boolean loadCompanies()
    {
        System.out.println("Loading companies...");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            TypeReference<Map<String, Company>> typeRef = new TypeReference<>() {};
            this.companies = objectMapper.readValue(new File("companies.json"), typeRef);
            System.out.println("Companies successfully loaded.");
            return true;
        }
        catch(Exception e)
        {
            System.out.println("Failed to load companies from file!");
            return false;
        }
    }

    public void printBestDividendStocks()
    {
        if (!loadCompanies())
            extractCompanies();
        else
            updateCompanies();

        sortCompaniesByReturn();

        printTableHeader();

        for(Company company : companies.values())
        {
            printRow(company);
        }
    }

}