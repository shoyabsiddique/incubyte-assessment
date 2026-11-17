package com.acme.salary.seed.domain;

import com.acme.salary.shared.domain.Country;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The reference lists the generated data is drawn from: roles, base pay, names and cities. */
final class SeedCatalog {

    record Title(String name, String department, int weight, int baseUsd) {}

    private static final List<Title> TITLES = List.of(
            new Title("Software Engineer", "Engineering", 18, 95_000),
            new Title("Senior Software Engineer", "Engineering", 10, 130_000),
            new Title("QA Engineer", "Engineering", 5, 75_000),
            new Title("DevOps Engineer", "Engineering", 4, 105_000),
            new Title("Engineering Manager", "Engineering", 3, 160_000),
            new Title("Product Manager", "Product", 4, 120_000),
            new Title("Business Analyst", "Product", 4, 80_000),
            new Title("Technical Writer", "Product", 2, 70_000),
            new Title("UX Designer", "Design", 3, 90_000),
            new Title("Visual Designer", "Design", 2, 75_000),
            new Title("Data Analyst", "Data & Analytics", 4, 80_000),
            new Title("Data Engineer", "Data & Analytics", 4, 115_000),
            new Title("Data Scientist", "Data & Analytics", 3, 125_000),
            new Title("Account Executive", "Sales", 5, 85_000),
            new Title("Sales Development Representative", "Sales", 5, 55_000),
            new Title("Sales Manager", "Sales", 2, 125_000),
            new Title("Marketing Specialist", "Marketing", 3, 65_000),
            new Title("Content Strategist", "Marketing", 2, 70_000),
            new Title("Marketing Manager", "Marketing", 2, 105_000),
            new Title("Accountant", "Finance", 3, 65_000),
            new Title("Financial Analyst", "Finance", 3, 80_000),
            new Title("Finance Manager", "Finance", 1, 120_000),
            new Title("HR Specialist", "People Operations", 3, 60_000),
            new Title("Recruiter", "People Operations", 3, 62_000),
            new Title("HR Manager", "People Operations", 2, 100_000));

    private static final Map<Country, BigDecimal> PAY_LEVEL = Map.of(
            Country.USA, new BigDecimal("1.00"),
            Country.UK, new BigDecimal("0.82"),
            Country.GERMANY, new BigDecimal("0.85"),
            Country.CANADA, new BigDecimal("0.80"),
            Country.AUSTRALIA, new BigDecimal("0.88"),
            Country.SINGAPORE, new BigDecimal("0.85"),
            Country.UAE, new BigDecimal("0.75"),
            Country.INDIA, new BigDecimal("0.30"));

    private static final Map<Country, List<String>> FIRST_NAMES = names(
            Country.INDIA, "Aarav,Vivaan,Aditya,Arjun,Rohan,Ananya,Diya,Isha,Kavya,Meera,Priya,Rahul,Sanjay,Neha,Vikram,Anjali,Karthik,Deepa,Rajesh,Sneha",
            Country.USA, "James,Mary,Robert,Patricia,John,Jennifer,Michael,Linda,David,Elizabeth,William,Susan,Daniel,Jessica,Matthew,Sarah",
            Country.UK, "Oliver,Amelia,George,Olivia,Harry,Isla,Jack,Emily,Charlie,Sophie,Thomas,Grace",
            Country.GERMANY, "Lukas,Leon,Finn,Emma,Mia,Hannah,Felix,Jonas,Lena,Paul,Anna,Max",
            Country.SINGAPORE, "Wei,Jun,Mei,Li,Hui,Xin,Jia,Kai,Ravi,Siti,Farah,Ming",
            Country.UAE, "Mohammed,Ahmed,Fatima,Aisha,Omar,Khalid,Layla,Noor,Yusuf,Mariam,Hassan,Sara",
            Country.CANADA, "Liam,Noah,Emma,Olivia,Ethan,Chloe,Lucas,Ava,Benjamin,Zoe,Nathan,Maya",
            Country.AUSTRALIA, "Jack,Mia,William,Charlotte,Noah,Ruby,Henry,Matilda,Cooper,Harper,Archie,Willow");

    private static final Map<Country, List<String>> LAST_NAMES = names(
            Country.INDIA, "Sharma,Patel,Reddy,Nair,Iyer,Gupta,Singh,Mehta,Kulkarni,Joshi,Rao,Das,Menon,Bhat,Verma",
            Country.USA, "Smith,Johnson,Williams,Brown,Jones,Miller,Davis,Garcia,Wilson,Anderson,Taylor,Thomas,Moore,Martin,Jackson",
            Country.UK, "Taylor,Davies,Evans,Thomas,Roberts,Walker,Wright,Robinson,Thompson,White,Hughes,Edwards",
            Country.GERMANY, "Mueller,Schmidt,Schneider,Fischer,Weber,Meyer,Wagner,Becker,Schulz,Hoffmann,Koch,Richter",
            Country.SINGAPORE, "Tan,Lim,Lee,Ng,Ong,Wong,Goh,Chua,Koh,Teo,Chan,Yeo",
            Country.UAE, "Khan,Hassan,Ali,Rahman,Siddiqui,Qureshi,Haddad,Nasser,Saeed,Farooq,Bakr,Youssef",
            Country.CANADA, "Tremblay,Roy,Gagnon,Campbell,Stewart,MacDonald,Bouchard,Fraser,Murray,Cote,Clarke,Ross",
            Country.AUSTRALIA, "Nguyen,Wilson,Kelly,Murphy,Smith,Jones,Brown,King,Walker,Harris,Ryan,Mitchell");

    private static final Map<Country, List<String>> CITIES = names(
            Country.INDIA, "Bengaluru,Pune,Hyderabad,Chennai,Mumbai,Delhi",
            Country.USA, "New York,Austin,San Francisco,Seattle,Chicago,Boston",
            Country.UK, "London,Manchester,Edinburgh,Bristol",
            Country.GERMANY, "Berlin,Munich,Hamburg,Frankfurt",
            Country.SINGAPORE, "Singapore",
            Country.UAE, "Dubai,Abu Dhabi",
            Country.CANADA, "Toronto,Vancouver,Montreal,Calgary",
            Country.AUSTRALIA, "Sydney,Melbourne,Brisbane,Perth");

    private SeedCatalog() {}

    static List<String> departments() {
        return TITLES.stream().map(Title::department).distinct().toList();
    }

    static List<String> jobTitles() {
        return TITLES.stream().map(Title::name).toList();
    }

    static List<Title> titles() {
        return TITLES;
    }

    static BigDecimal payLevel(Country country) {
        return PAY_LEVEL.get(country);
    }

    static List<String> firstNames(Country country) {
        return FIRST_NAMES.get(country);
    }

    static List<String> lastNames(Country country) {
        return LAST_NAMES.get(country);
    }

    static List<String> cities(Country country) {
        return CITIES.get(country);
    }

    private static Map<Country, List<String>> names(Object... countryAndCsv) {
        Map<Country, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < countryAndCsv.length; i += 2) {
            result.put((Country) countryAndCsv[i], new ArrayList<>(List.of(((String) countryAndCsv[i + 1]).split(","))));
        }
        return Map.copyOf(result);
    }
}
