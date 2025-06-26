package etoro;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class Client {

    private Map<String,Company> companies = new LinkedHashMap<>();
    private Map<String, Company> processedCompanies = new LinkedHashMap<>();
    private final int[] columnWidths = {50, 30, 20, 20, 20, 35, 20};

    private int pageIndex = 0;
    private final int pageSize = 15;

    private Set<String> tags = new LinkedHashSet<>();
    private String currentTag = "NONE";
    private int currentComparator = 0;

    private String[] rowTitles = {"Company", "Dividend Yield (after tax)", "Price", "ExDividend date", "Dividend date", "Dividend per share (after tax)", "Market Cap" };

    private EtoroScraper server;

    List<Comparator<Company>> comparators = List.of(
            Comparator.comparing(Company::getFullName),
            Comparator.comparingDouble((Company c) ->
            {
                double price = c.getPrice();
                return price != 0 ? c.getDividendPerShare() / c.getPrice() : 0;
            }).reversed(),
            Comparator.comparingDouble(Company::getPrice).reversed(),
            Comparator.comparing(Company::getExDividendDate),
            Comparator.comparing(Company::getDividendDate),
            Comparator.comparing(Company::getDividendPerShare).reversed(),
            Comparator.comparing(Company::getMarketCap).reversed()
    );

    private List<String> favourites = new ArrayList<>();


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
        final String HOT_PINK = "\u001B[38;2;255;105;180m";
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
                company.getExDividendDateString(),
                company.getDividendDateString(),
                String.format("%.2f (%.2f)", company.dividendPerShare, company.dividendPerShare - company.dividendPerShare* dividendTax),
                company.getMarketCapString()
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

        if(company.tags.contains("FAVOURITE"))
            System.out.println(HOT_PINK + row + RESET);
        else if(company.tags.contains("NEW"))
            System.out.println(GREEN + row + RESET);
        else
            System.out.println(row);
    }


    private void filterCompaniesByTag()
    {
        if (currentTag.equals("NONE")){
            processedCompanies = new LinkedHashMap<>(companies);
            sortCompanies();
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
        sortCompanies();
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
        pageIndex = 0;
    }

    private void sortCompanies()
    {
        processedCompanies = processedCompanies.values()
                .stream()
                .sorted(comparators.get(currentComparator))
                .collect(Collectors.toMap(
                        Company::getFullName,
                        Function.identity(),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    private void toggleSorting()
    {
        currentComparator += 1;
        if (currentComparator >= comparators.size())
            currentComparator = 0;
        sortCompanies();
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
        System.out.println(" [x] - edit favourites");
        System.out.println(" [q] - quit");
    }

    private void printPageNumber()
    {
        StringBuilder output = new StringBuilder();
        int totalPages = (processedCompanies.size() + pageSize - 1) / pageSize;
        String pageText = "Page " +  (pageIndex + 1) + "/" + totalPages;
        final int padding = Arrays.stream(columnWidths).sum()/2 + columnWidths.length - 1 - pageText.length()/2;
        output.append(" ".repeat(padding));
        output.append(pageText);
        output.append(" ".repeat(padding));
        System.out.println(output);
    }

    private void printStockTable(int page)
    {
        clearConsole();
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

    private int levensteinDistance(String a, String b)
    {
        a = a.toLowerCase();
        b = b.toLowerCase();

        BiFunction<Character, Character, Integer> substitutionCost = (c1,c2) -> c1.equals(c2) ? 0 : 1;

        int[][] dp = new int[a.length()+1][b.length()+1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int i = 0; i <= b.length(); i++) {
            dp[0][i] = i;
        }

        for(int i = 1; i <= a.length(); i++)
        {
            for(int j = 1; j <= b.length(); j++)
            {
                dp[i][j] = min(min(dp[i-1][j-1] + substitutionCost.apply(a.charAt(i-1), b.charAt(j-1)),
                        dp[i-1][j] + 1), dp[i][j-1] + 1);
            }
        }

        return dp[a.length()][b.length()];
    }

    private void toggleFavorites() {

        while(true) {
            clearConsole();
            System.out.println("Enter company name and I'll try to find it! (or press q to exit)");
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();
            line = line.strip();

            if (line.equalsIgnoreCase("q"))
                return;

            String bestMatch = "";
            int bestScore = Integer.MAX_VALUE;

            for (String companyName : companies.keySet()) {
                if (bestScore == 0) {
                    break;
                }

                if (companyName.equalsIgnoreCase(line)) {
                    bestMatch = companyName;
                    bestScore = 0;
                    break;
                }

                for (String token : companyName.split(" ")) {
                    int score = levensteinDistance(line, token);
                    if (score < bestScore) {
                        bestMatch = companyName;
                        bestScore = score;
                    }
                }

            }

            System.out.println("Found company: " + bestMatch);
            boolean exists = false;
            if (favourites.contains(bestMatch)) {
                exists = true;
                System.out.println("Company already is in the list. Remove from favourites?");
                System.out.println(" [y] - Yes, remove from favourites");
                System.out.println(" [n] - No, search again");
                System.out.println(" [q] - Quit");
            } else {
                System.out.println("Add to favourites?");
                System.out.println(" [y] - Yes, add to favourites");
                System.out.println(" [n] - No, search again");
                System.out.println(" [q] - Quit");
            }

            while(true) {
                line = scanner.nextLine().strip().toLowerCase();

                if (line.equals("q")) {
                    return;
                }

                if (line.equals("y"))
                {
                    if (exists) {
                        favourites.remove(bestMatch);
                        companies.get(bestMatch).removeTag("FAVOURITE");
                        System.out.println("Removed " + bestMatch + " from favourites");
                    } else {
                        favourites.add(bestMatch);
                        companies.get(bestMatch).addTag("FAVOURITE");
                        System.out.println("Added " + bestMatch + " to favourites");
                    }
                    break;
                }

                else if (line.equals("n")){
                    break;
                }
                System.out.println("Please enter y, n or q.");

            }
        }
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

    public static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFavourites()
    {
        System.out.println("Loading favourites...");
        ObjectMapper mapper = new ObjectMapper();
        try{
            TypeReference<List<String>> typeReference = new TypeReference<>() {};
            this.favourites = mapper.readValue(new File("favourites.txt"), typeReference);
            for (String company : favourites)
            {
                this.companies.get(company).addTag("FAVOURITE");
            }
            System.out.println("Favourites loaded successfully.");
        }catch (Exception e) {
            System.out.println("Failed to load favourites!");
        }
    }

    private void saveFavourites()
    {
        System.out.println("Saving favourites...");
        ObjectMapper mapper = new ObjectMapper();
        try{
            mapper.writeValue(new File("favourites.txt"), favourites);
            System.out.println("Favourites saved successfully.");
        }
        catch(Exception e)
        {
            System.out.println("Failed to save favourites!");
        }
    }

    public void startClient(){
        this.server = new EtoroScraper();
        server.loadCompanies();
        this.companies = new LinkedHashMap<>(server.companies);
        this.processedCompanies = new LinkedHashMap<>(companies);
        loadFavourites();
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
                case 'q': {
                    saveFavourites();
                    return;
                }

                case 'a':
                {
                    pageIndex = max(0, pageIndex - 1);
                    break;
                }

                case 'd':
                {
                    int pages = (processedCompanies.size() + pageSize - 1) / pageSize;
                    pageIndex = min(pages-1, pageIndex+1);
                    break;
                }

                case 'w':
                {
                    int pages = (processedCompanies.size() + pageSize - 1) / pageSize;
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

                case 'x':
                {
                    toggleFavorites();
                    if(favourites.isEmpty())
                    {
                        currentTag = "NONE";
                        tags.remove("FAVOURITE");
                        filterCompaniesByTag();
                    }else{
                        extractTags();
                    }
                    break;
                }

                default:
                    break;
            }
            printStockTable(pageIndex);
        }
    }


}
