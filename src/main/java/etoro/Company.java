package etoro;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Company{

    public String name;
    public String fullName;
    public String sector;
    @JsonFormat
    (pattern = "dd MMM yyyy")
    public Date exDividendDate;
    @JsonFormat
    (pattern = "dd MMM yyyy")
    public Date dividendDate;
    public float price;
    public String marketHref;
    public float dividendPerShare;
    public BigDecimal marketCap;
    public Set<String> tags;

    public Company(){}

    public static class Builder
    {
        private final String name;
        private final String fullName;
        private String marketHref;
        private String sector;
        private Date exDividendDate;
        private Date dividendDate;
        private float price;
        private float dividendPerShare;
        public BigDecimal marketCap;
        private Set<String> tags = new HashSet<>();

        public Builder(String name, String fullName) {
            this.name = name;
            this.fullName = fullName;

        }

        public Builder sector(String sector) {
            this.sector = sector;
            return this;
        }

        public Builder exDividendDate(Date exDividendDate) {
            this.exDividendDate = exDividendDate;
            return this;
        }

        public Builder dividendDate(Date DividendDate) {
            this.dividendDate = DividendDate;
            return this;
        }

        public Builder price(float price) {
            this.price = price;
            return this;
        }

        public Builder dividendPerShare(float dividendPerShare) {
            this.dividendPerShare = dividendPerShare;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder addTags(List<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public Builder marketHref(String marketHref) {
            this.marketHref = marketHref;
            return this;
        }

        public Builder marketCap(String marketCap) {
            this.marketCap = parseMarketCap(marketCap);
            return this;
        }

        public Builder marketCap(BigDecimal marketCap) {
            this.marketCap = marketCap;
            return this;
        }

        public Company build() {
            return new Company(this);
        }
    }

    public Company(Builder builder) {
        this.name = builder.name;
        this.fullName = builder.fullName;
        this.sector = builder.sector;
        this.price = builder.price;
        this.marketHref = builder.marketHref;
        this.dividendPerShare = builder.dividendPerShare;
        this.exDividendDate = builder.exDividendDate;
        this.dividendDate = builder.dividendDate;
        this.tags = builder.tags;
    }

    public Date getExDividendDate()
    {
        return exDividendDate;
    }

    public Date getDividendDate()
    {
        return dividendDate;
    }

    @JsonIgnore
    public String getExDividendDateString() {
        LocalDate date = exDividendDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        return date.format(formatter);
    }

    @JsonIgnore
    public String getDividendDateString() {
        LocalDate date = dividendDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        return date.format(formatter);
    }

    public String getFullName() {
        return fullName;
    }

    public double getPrice(){return price;}

    public double getDividendPerShare() {return dividendPerShare;}

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    @Override
    public String toString() {
        return "etoro.Company [name=" + name + ", fullName=" + fullName + ", sector=" + sector
                + ", price=" + price  + ", dividendPerShare=" + dividendPerShare
                + ", exDividendDate=" + getExDividendDate() + ", DividendDate=" + getDividendDate()  + "]";
    }

    @JsonIgnore
    public static BigDecimal parseMarketCap(String marketCapString) {
        marketCapString = marketCapString.strip().toUpperCase();
        BigDecimal multiplier = BigDecimal.ONE;

        if (marketCapString.endsWith("T"))
        {
            multiplier = new BigDecimal("1000000000000");
        }
        else if (marketCapString.endsWith("B"))
        {
            multiplier = new BigDecimal("1000000000");
        }
        else if (marketCapString.endsWith("M"))
        {
            multiplier = new BigDecimal("1000000");
        }
        marketCapString = marketCapString.substring(0,marketCapString.length()-1);
        return new BigDecimal(marketCapString).multiply(multiplier);
    }

    @JsonIgnore
    public String getMarketCapString()
    {
        String[] suffixes = {"", "K", "M", "B", "T"};
        BigDecimal thousand = BigDecimal.valueOf(1000);

        BigDecimal marketCapValue = marketCap;
        int marketCapSuffixIndex = 0;


        while (marketCapValue.compareTo(thousand) >= 0 && marketCapSuffixIndex < suffixes.length)
        {
            marketCapValue = marketCapValue.divide(thousand);
            marketCapSuffixIndex += 1;
        }

        marketCapValue = marketCapValue.setScale(2, RoundingMode.HALF_UP);
        String marketCapString = marketCapValue.toString();
        return marketCapString + suffixes[marketCapSuffixIndex];
    }
}
