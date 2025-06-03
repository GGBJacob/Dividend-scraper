package etoro;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonFormat;

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


    public String getExDividendDateString() {
        LocalDate date = exDividendDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        return date.format(formatter);
    }

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

    @Override
    public String toString() {
        return "etoro.Company [name=" + name + ", fullName=" + fullName + ", sector=" + sector
                + ", price=" + price  + ", dividendPerShare=" + dividendPerShare
                + ", exDividendDate=" + getExDividendDateString() + ", DividendDate=" + getDividendDateString()  + "]";
    }
}
