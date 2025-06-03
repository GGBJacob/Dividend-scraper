package etoro;

import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTracker {

    private float startTime;
    private float averageTimePerCompany = 0;
    private final AtomicInteger companiesProcessed = new AtomicInteger(0);
    private final int companiesCount;
    private int lastPercentage;
    private float totalTime;

    ProgressTracker(int companiesCount) {
        startTime = System.nanoTime();
        this.companiesCount = companiesCount;
    }

    public synchronized void incrementCompaniesProcessed() {
        int processed = companiesProcessed.incrementAndGet();
        int progress = (processed * 100) / companiesCount;

        if (progress >= 5 && progress % 5 == 0 && lastPercentage != progress) {
            lastPercentage = progress;
            displayStatus(progress, processed);
        }

        float timeElapsed = (System.nanoTime() - startTime) / 1_000_000_000;
        totalTime += timeElapsed;
        averageTimePerCompany = totalTime / processed;
        startTime = System.nanoTime();
    }

    public void displayStatus(int progress, int currentCompanyIndex) {
        System.out.println("\nProcessed " + currentCompanyIndex + "/" + companiesCount + " companies (" + progress + "%)");
        long remainingTime = (long) ((companiesCount - currentCompanyIndex) * averageTimePerCompany);
        System.out.println("Remaining time: " + remainingTime / 60 + ":" + String.format("%02d", remainingTime % 60));
    }
}