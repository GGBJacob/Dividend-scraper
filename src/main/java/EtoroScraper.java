import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.max;


public class EtoroScraper
{
    private final String fullUrl = "https://www.etoro.com";
    private final String url = "https://www.etoro.com/investing/dividend-calendar/";
    private List<Company> companies;
    private final int[] columnWidths = {50, 30, 20, 20, 20, 35};

    private float fetchCompanyPrice(String href, String companyName)
    {
        String link = fullUrl + href;
        Document marketPage;
        try {
            marketPage = Jsoup.connect(link).get();
            String rawPrice = marketPage.select("span[data-automation-id=AssetShortInfoPrice]").text().replace(",", "");
            return Float.parseFloat(rawPrice);
        }catch (IOException e)
        {
            System.out.println("Couldn't connect to " + link);
            return 0;
        }
        catch (Exception e)
        {
            System.out.println("Couldn't fetch "+ companyName +" price!");
        }
        return 0;
    }

    private void extractCompanies()
    {
        if (companies != null)
            return;

        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        }catch (IOException e)
        {
            System.out.println("Couldn't connect to " + url);
            return;
        }

        System.out.println("Extracting companies...");
        List<Element> tableRows = doc.select("tbody.ec-reports-container tr");
        List<Company> result = new ArrayList<>();

        int companiesCount = tableRows.size();
        int currentCompany = 0;
        int lastPercentage = -1;
        long startTime = System.nanoTime();

        for (int i=0; i<companiesCount; i++)
        {
            currentCompany += 1;
            Element row = tableRows.get(i);
            try {
                Elements tds = row.select("td");

                String name = tds.get(0).select("span.ec-company__name").text();
                String fullName = tds.get(0).attr("data-company-name");
                String sector = tds.get(1).attr("data-sector-name");
                Date exDividend = Date.from(LocalDate.parse(tds.get(2).attr("data-exdividend-date")).atStartOfDay(ZoneId.systemDefault()).toInstant());
                Date dividend = Date.from(LocalDate.parse(tds.get(3).attr("data-payment-date")).atStartOfDay(ZoneId.systemDefault()).toInstant());
                String marketHref = tds.get(0).select("a").attr("href");
                float price = fetchCompanyPrice(marketHref, fullName);
                float dividendPerShare = Float.parseFloat(tds.get(5).attr("data-net-dividend"));

                Company company = new Company.Builder(name, fullName)
                        .sector(sector)
                        .exDividendDate(exDividend)
                        .dividendDate(dividend)
                        .price(price)
                        .dividendPerShare(dividendPerShare)
                        .build();

                result.add(company);
            }
            catch (Exception ignored)
            {
            }
            int progress = currentCompany * 100 / companiesCount;

            if (progress >= 5 && progress%5 == 0 && lastPercentage != progress)
            {
                lastPercentage = progress;
                System.out.println("\nProcessed " + currentCompany + "/" + companiesCount + " companies (" + progress + "%)");
                long timeElapsed = (System.nanoTime() - startTime)/1_000_000_000;
                float averageTimePerCompany = timeElapsed / (float) currentCompany;
                long remainingTime = (long)((companiesCount - currentCompany) * averageTimePerCompany);
                System.out.println("Remaining time: " + remainingTime/60 + ":" +String.format("%02d", remainingTime%60));
            }
        }

        this.companies = result;
        saveCompaniesToFile();
    }


    private void saveCompaniesToFile()
    {
        System.out.println("Saving companies...");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("companies.dat"))) {
            out.writeObject(companies);
        }
        catch(Exception e)
        {
            System.out.println("Failed to save companies to file!");
            return;
        }
        System.out.println("Companies successfully saved.");
    }

    private void printTableHeader() {
        String[] rowTitles = {"Company", "Dividend % (after tax)", "Price", "ExDividend date", "Dividend date", "Dividend per share (after tax)" };

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

        System.out.println(header);
        System.out.println(separator);

    }

    private void printRow(Company company)
    {
        float returnValue = company.dividendPerShare/company.price * 100;
        float dividendTax = 0.1f;

        String[] values = {
                company.fullName,
                String.format("%.2f%% (%.2f%%)", returnValue, returnValue-returnValue* dividendTax),
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

        System.out.println(row);
    }

    private void sortCompaniesByReturn()
    {
        companies = companies.stream()
                .sorted((o1, o2) -> Float.compare(o2.dividendPerShare/o2.price, o1.dividendPerShare/o1.price))
                .collect(Collectors.toList());
    }

    public void printBestDividendStocks()
    {
        System.out.println("Loading companies...");
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("companies.dat"))) {
            this.companies = (List<Company>) in.readObject();
            System.out.println("Companies successfully loaded.");
        }
        catch(Exception e)
        {
            System.out.println("Failed to load companies from file!");
            extractCompanies();
        }

        sortCompaniesByReturn();

        printTableHeader();

        for(Company company : this.companies)
        {
            printRow(company);
        }
    }

}