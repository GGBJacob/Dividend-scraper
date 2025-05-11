import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class Company implements Serializable{

    public String name;
    public String fullName;
    public String sector;
    public Date exDividendDate;
    public Date dividendDate;
    public float price;
    public float dividendPerShare;

    public static class Builder
    {
        private final String name;
        private final String fullName;
        private String sector;
        private Date exDividendDate;
        private Date dividendDate;
        private float price;
        private float dividendPerShare;

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

        public Company build() {
            return new Company(this);
        }
    }

    public Company(Builder builder) {
        this.name = builder.name;
        this.fullName = builder.fullName;
        this.sector = builder.sector;
        this.price = builder.price;
        this.dividendPerShare = builder.dividendPerShare;
        this.exDividendDate = builder.exDividendDate;
        this.dividendDate = builder.dividendDate;
    }

    public String getExDividendDate() {
        LocalDate date = exDividendDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        return date.format(formatter);
    }

    public String getDividendDate() {
        LocalDate date = dividendDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        return date.format(formatter);
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return "Company [name=" + name + ", fullName=" + fullName + ", sector=" + sector
                + ", price=" + price  + ", dividendPerShare=" + dividendPerShare
                + ", exDividendDate=" + getExDividendDate() + ", DividendDate=" + getDividendDate()  + "]";
    }
}
