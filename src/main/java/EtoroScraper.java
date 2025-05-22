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
    public Map<String,Company> companies = new LinkedHashMap<>();
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
        Iterator<Map.Entry<String, Company>> iterator = companies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Company> entry = iterator.next();
            if (entry.getValue().dividendDate.before(currentDate)) {
                iterator.remove();
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

    public void loadCompanies()
    {
        System.out.println("Loading companies...");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            TypeReference<Map<String, Company>> typeRef = new TypeReference<>() {};
            this.companies = objectMapper.readValue(new File("companies.json"), typeRef);
            System.out.println("Companies successfully loaded.");
            updateCompanies();
        }
        catch(Exception e)
        {
            System.out.println("Failed to load companies from file!");
            extractCompanies();
        }
    }


}