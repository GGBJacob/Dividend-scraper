import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Client {

    private Map<String,Company> companies = new LinkedHashMap<>();
    private Map<String, Company> processedCompanies = new LinkedHashMap<>();
    private final int[] columnWidths = {50, 30, 20, 20, 20, 35};

    private int pageIndex = 0;
    private final int pageSize = 15;

    private Set<String> tags = new LinkedHashSet<>();
    private String currentTag = "NONE";
    private int currentComparator = 0;

    private String[] rowTitles = {"Company", "Dividend Yield (after tax)", "Price", "ExDividend date", "Dividend date", "Dividend per share (after tax)" };

    private EtoroScraper server;



    private void printTableHeader() {

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


    private void filterCompaniesByTag()
    {
        if (currentTag.equals("NONE")){
            processedCompanies = new LinkedHashMap<>(companies);
            return;
        }

        processedCompanies = companies.values().stream()
                .filter(company -> company.tags.contains(currentTag))
                .collect(Collectors.toMap(
                        Company::getFullName,
                        Function.identity(),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    private void toggleFiltering()
    {
        if (tags.isEmpty()) {
            currentTag = "NONE";
            return;
        }

        List<String> tagsList = new ArrayList<>(tags);

        int tagIndex = tagsList.indexOf(currentTag);
        if(tagIndex == tagsList.size() - 1)
            currentTag = tagsList.get(0);
        else
            currentTag = tagsList.get(tagIndex+1);
        filterCompaniesByTag();
    }

    private void toggleSorting()
    {
        List<Comparator<Company>> comparators = List.of(
                Comparator.comparing(Company::getFullName),
                Comparator.comparingDouble((Company c) ->
                {
                    double price = c.getPrice();
                    return price != 0 ? c.getDividendPerShare() / c.getPrice() : 0;
                }).reversed(),
                Comparator.comparingDouble(Company::getPrice).reversed(),
                Comparator.comparing(Company::getExDividendDate).reversed(),
                Comparator.comparing(Company::getDividendDate).reversed(),
                Comparator.comparing(Company::getDividendPerShare).reversed()
        );

        currentComparator += 1;
        if (currentComparator >= comparators.size())
            currentComparator = 0;

        processedCompanies = companies.values()
                .stream()
                .sorted(comparators.get(currentComparator))
                .collect(Collectors.toMap(
                        Company::getFullName,
                        Function.identity(),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public void extractTags()
    {
        companies.values().forEach(company -> {tags.addAll(company.tags);});
        tags.add("NONE");
    }


    private void printInstructions()
    {
        System.out.println("Instructions:");
        System.out.println(" [a] - view previous page");
        System.out.println(" [d] - view next page");
        System.out.println(" [w] - enter page index");
        System.out.println(" [s] - toggle sorting (" + rowTitles[currentComparator] + ")");
        System.out.println(" [f] - toggle filtering (" + currentTag +")");
        System.out.println(" [q] - quit");
    }

    private void printPageNumber()
    {
        StringBuilder output = new StringBuilder();
        final int padding = 22;
        output.append("\t".repeat(padding));
        int totalPages = (companies.size() + pageSize - 1) / pageSize;
        output.append("Page " + (pageIndex + 1) + "/" + totalPages);
        output.append(" ".repeat(padding));
        System.out.println(output);
    }

    private void printStockTable(int page)
    {
        printTableHeader();
        List<Company> allCompanies = new ArrayList<>(processedCompanies.values());
        int from = page * pageSize;
        int to = min(from + pageSize, allCompanies.size());
        List<Company> currentPage = allCompanies.subList(from, to);
        for(Company company : currentPage)
        {
            printRow(company);
        }
        System.out.println('\n');
        printPageNumber();
        System.out.println('\n');
        printInstructions();
    }


    private int readInt(int min, int max) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                String line = scanner.nextLine();
                int number = Integer.parseInt(line.trim());
                if (number < min)
                    return min;
                else if(number > max)
                    return max;
                return number;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input!");
            }
        }
    }

    public void startClient(){
        this.server = new EtoroScraper();
        server.loadCompanies();
        this.companies = new LinkedHashMap<>(server.companies);
        toggleSorting();
        extractTags();

        char input = ' ';
        printStockTable(0);

        Scanner scanner = new Scanner(System.in);

        while(true)
        {
            try {
                String line = scanner.nextLine();
                input = line.charAt(0);
            }catch (Exception ignored)
            {
                input = 'q';
            }

            switch(input)
            {
                case 'q':
                    return;

                case 'a':
                {
                    pageIndex = max(0, pageIndex - 1);
                    break;
                }

                case 'd':
                {
                    int pages = (companies.size() + pageSize - 1) / pageSize;
                    pageIndex = min(pages, pageIndex + 1);
                    break;
                }

                case 'w':
                {
                    int pages = (companies.size() + pageSize - 1) / pageSize;
                    System.out.print("Go to page: ");
                    pageIndex = readInt(1, pages)-1;
                    break;
                }

                case 'f':
                {
                    toggleFiltering();
                    break;
                }

                case 's':
                {
                    toggleSorting();
                    break;
                }

                default:
                    break;
            }

            printStockTable(pageIndex);
        }

    }

}
